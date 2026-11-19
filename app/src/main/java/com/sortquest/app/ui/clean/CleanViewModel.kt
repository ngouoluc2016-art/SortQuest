package com.sortquest.app.ui.clean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.usecase.ObserveDuplicateGroupsUseCase
import com.sortquest.app.domain.usecase.ObserveLargestFilesUseCase
import com.sortquest.app.domain.usecase.TrashFilesUseCase
import com.sortquest.app.workers.ScanStatus
import com.sortquest.app.workers.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanUiState(
    val isLoaded: Boolean = false,
    val scan: ScanStatus? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    val largest: List<FileItem> = emptyList(),
    /** hash du groupe -> id du fichier à GARDER (par défaut : le plus ancien). */
    val keep: Map<String, Long> = emptyMap(),
    val busy: Boolean = false,
) {
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
    val totalCopies: Int get() = groups.sumOf { it.copies }

    fun keptId(group: DuplicateGroup): Long = keep[group.hash] ?: group.files.first().id
    fun copiesToTrash(group: DuplicateGroup): List<FileItem> = group.files.filter { it.id != keptId(group) }
}

private data class LocalCleanState(val keep: Map<String, Long> = emptyMap(), val busy: Boolean = false)

@HiltViewModel
class CleanViewModel @Inject constructor(
    observeDuplicates: ObserveDuplicateGroupsUseCase,
    observeLargest: ObserveLargestFilesUseCase,
    private val trashFiles: TrashFilesUseCase,
    private val workManager: WorkManager,
) : ViewModel() {

    private val local = MutableStateFlow(LocalCleanState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Les résultats viennent de Room (persistés) : rien n'est recalculé à l'ouverture de l'écran,
     * et rien n'est gardé en mémoire au-delà de ce que l'écran affiche.
     * WhileSubscribed(5 s) : collecte arrêtée quand l'écran n'est plus visible (pas de fuite).
     */
    val uiState: StateFlow<CleanUiState> = combine(
        observeDuplicates(),
        observeLargest(),
        ScanWorker.observeStatus(workManager),
        local,
    ) { groups, largest, scan, loc ->
        CleanUiState(
            isLoaded = true,
            scan = scan,
            groups = groups,
            largest = largest,
            // on oublie les choix portant sur des groupes qui n'existent plus
            keep = loc.keep.filterKeys { hash -> groups.any { it.hash == hash } },
            busy = loc.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanUiState())

    /** Relance un scan complet (indexation + doublons) dans WorkManager. */
    fun analyze() = ScanWorker.enqueue(workManager)

    fun setKeep(group: DuplicateGroup, fileId: Long) {
        local.update { it.copy(keep = it.keep + (group.hash to fileId)) }
    }

    fun trashCopies(group: DuplicateGroup) = trash(uiState.value.copiesToTrash(group))

    fun trashAllCopies() {
        val state = uiState.value
        trash(state.groups.flatMap { state.copiesToTrash(it) })
    }

    fun trashFile(file: FileItem) = trash(listOf(file))

    private fun trash(files: List<FileItem>) {
        if (files.isEmpty() || local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = trashFiles(files)
            local.update { it.copy(busy = false) }
            _messages.send(
                buildString {
                    append("${result.trashed} fichier(s) envoyé(s) à la corbeille")
                    if (result.failed > 0) append(" · ${result.failed} échec(s)")
                    append(". Récupérables 7 jours.")
                }
            )
        }
    }
}
