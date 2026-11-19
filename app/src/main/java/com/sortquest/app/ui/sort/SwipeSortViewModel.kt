package com.sortquest.app.ui.sort

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sortquest.app.domain.gamification.GamificationManager
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.domain.model.UserProgress
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.ProgressRepository
import com.sortquest.app.domain.usecase.CompleteExpressSessionUseCase
import com.sortquest.app.domain.usecase.SortFileUseCase
import com.sortquest.app.domain.usecase.UndoLastActionUseCase
import com.sortquest.app.workers.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpressSession(
    val remainingSeconds: Int = DURATION_SECONDS,
    val sorted: Int = 0,
    val target: Int = 20,
) {
    companion object { const val DURATION_SECONDS = 120 }
}

data class SwipeSortUiState(
    val deck: List<FileItem> = emptyList(),
    val pendingCount: Int = 0,
    val progress: UserProgress = UserProgress.EMPTY,
    val storage: StorageStats? = null,
    val combo: Int = 0,
    val canUndo: Boolean = false,
    val isScanning: Boolean = false,
    val scannedFiles: Int = 0,
    val express: ExpressSession? = null,
)

/** Événements ponctuels (niveaux et badges sont gérés globalement par CelebrationHost). */
sealed interface SortUiEvent {
    data class ActionDone(
        val historyId: Long,
        val direction: SwipeDirection,
        val xp: Int,
        val fileName: String,
        val combo: Int,
    ) : SortUiEvent
    data class ExpressFinished(val sorted: Int, val success: Boolean, val bonusXp: Int) : SortUiEvent
    data class Undone(val fileName: String) : SortUiEvent
    data class Error(val message: String) : SortUiEvent
}

private data class LocalState(
    val combo: Int = 0,
    val hiddenIds: Set<Long> = emptySet(), // cartes déjà animées hors écran, en cours de traitement
    val storage: StorageStats? = null,
    val express: ExpressSession? = null,
)

private data class RepoSnapshot(
    val queue: List<FileItem>,
    val pending: Int,
    val progress: UserProgress,
    val canUndo: Boolean,
    val scan: Pair<Boolean, Int>,
)

@HiltViewModel
class SwipeSortViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    progressRepository: ProgressRepository,
    private val sortFile: SortFileUseCase,
    private val undoLastAction: UndoLastActionUseCase,
    private val completeExpress: CompleteExpressSessionUseCase,
    private val workManager: WorkManager,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val _events = Channel<SortUiEvent>(Channel.BUFFERED)
    val events: Flow<SortUiEvent> = _events.receiveAsFlow()

    private var lastActionAt = 0L
    private var expressJob: Job? = null

    private val scanState: Flow<Pair<Boolean, Int>> =
        workManager.getWorkInfosForUniqueWorkFlow(ScanWorker.UNIQUE_NAME).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            (running != null) to (running?.progress?.getInt(ScanWorker.KEY_SCANNED, 0) ?: 0)
        }

    private val repoSnapshot: Flow<RepoSnapshot> = combine(
        fileRepository.observeQueue(DECK_SIZE),
        fileRepository.observePendingCount(),
        progressRepository.observe(),
        fileRepository.observeCanUndo(), // lu en base : l'annulation survit à un redémarrage
        scanState,
    ) { queue, pending, progress, canUndo, scan -> RepoSnapshot(queue, pending, progress, canUndo, scan) }

    val uiState: StateFlow<SwipeSortUiState> = combine(repoSnapshot, local) { repo, loc ->
        SwipeSortUiState(
            deck = repo.queue.filterNot { it.id in loc.hiddenIds },
            pendingCount = repo.pending,
            progress = repo.progress,
            storage = loc.storage,
            combo = loc.combo,
            canUndo = repo.canUndo,
            isScanning = repo.scan.first,
            scannedFiles = repo.scan.second,
            express = loc.express,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeSortUiState())

    init {
        viewModelScope.launch {
            refreshStorage()
            if (fileRepository.observePendingCount().first() == 0) rescan()
        }
        // La jauge "Santé du stockage" suit le nombre de fichiers à trier : sans cela, elle restait
        // figée sur la valeur lue AVANT la fin du premier scan (constaté sur émulateur : 94 % au lieu de ~40 %).
        viewModelScope.launch {
            fileRepository.observePendingCount().distinctUntilChanged().collect { refreshStorage() }
        }
    }

    fun rescan() = ScanWorker.enqueue(workManager)

    /** Appelé quand la carte a fini son animation de sortie (geste OU bouton). */
    fun onSwipe(item: FileItem, direction: SwipeDirection) {
        // Combo calculé de façon synchrone : juste même avec des swipes très rapides
        val now = SystemClock.elapsedRealtime()
        val current = local.value.combo
        val combo = when {
            direction == SwipeDirection.DOWN -> current // "plus tard" ne casse pas le combo
            now - lastActionAt <= GamificationManager.COMBO_WINDOW_MS -> current + 1
            else -> 1
        }
        if (direction != SwipeDirection.DOWN) lastActionAt = now
        val inExpress = local.value.express != null
        local.update { it.copy(hiddenIds = it.hiddenIds + item.id, combo = combo) }

        viewModelScope.launch {
            sortFile(item, direction, combo, inExpress)
                .onSuccess { outcome ->
                    val storage = fileRepository.storageStats()
                    local.update { s ->
                        s.copy(
                            storage = storage,
                            express = s.express?.let { e ->
                                if (direction == SwipeDirection.DOWN) e else e.copy(sorted = e.sorted + 1)
                            },
                        )
                    }
                    _events.send(SortUiEvent.ActionDone(outcome.record.id, direction, outcome.reward.xpGained, item.name, combo))
                    checkExpressGoal()
                }
                .onFailure { e ->
                    local.update { it.copy(hiddenIds = it.hiddenIds - item.id, combo = 0) }
                    _events.send(SortUiEvent.Error(e.message ?: "Action impossible"))
                }
        }
    }

    /**
     * [historyId] null (bouton Annuler) : dernière action en base, manuelle OU Auto-Pilot,
     * même après redémarrage. Sinon : l'action précise liée au snackbar.
     */
    fun undo(historyId: Long? = null) {
        viewModelScope.launch {
            undoLastAction(historyId)
                .onSuccess { record ->
                    if (record == null) return@onSuccess
                    val storage = fileRepository.storageStats()
                    local.update {
                        it.copy(
                            hiddenIds = record.fileId?.let { id -> it.hiddenIds - id } ?: it.hiddenIds,
                            combo = 0,
                            storage = storage,
                        )
                    }
                    _events.send(SortUiEvent.Undone(record.fileName))
                }
                .onFailure { e -> _events.send(SortUiEvent.Error("Annulation impossible : ${e.message}")) }
        }
    }

    // ------------------------------------------------------------ Session Express

    fun startExpressSession() {
        if (local.value.express != null) return
        local.update { it.copy(express = ExpressSession(), combo = 0) }
        expressJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val e = local.value.express ?: break
                val remaining = e.remainingSeconds - 1
                if (remaining <= 0) {
                    finishExpress()
                    break
                }
                local.update { it.copy(express = e.copy(remainingSeconds = remaining)) }
            }
        }
    }

    private suspend fun checkExpressGoal() {
        val e = local.value.express ?: return
        if (e.sorted >= e.target) {
            expressJob?.cancel()
            finishExpress()
        }
    }

    private suspend fun finishExpress() {
        val e = local.value.express ?: return
        local.update { it.copy(express = null) }
        val reward = completeExpress(e.sorted, e.target, e.remainingSeconds)
        _events.send(SortUiEvent.ExpressFinished(e.sorted, e.sorted >= e.target, reward.xpGained))
    }

    private suspend fun refreshStorage() {
        runCatching { fileRepository.storageStats() }.onSuccess { s -> local.update { it.copy(storage = s) } }
    }

    private companion object {
        const val DECK_SIZE = 12
    }
}
