package com.sortquest.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.usecase.DeleteRuleUseCase
import com.sortquest.app.domain.usecase.ObserveRulesUseCase
import com.sortquest.app.domain.usecase.PreviewRuleUseCase
import com.sortquest.app.domain.usecase.SaveRuleUseCase
import com.sortquest.app.domain.usecase.ToggleRuleUseCase
import com.sortquest.app.domain.usecase.UndoAutoPilotRunUseCase
import com.sortquest.app.workers.AutoSortWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Saisie brute de l'éditeur (texte), convertie en SortRule à l'enregistrement. */
data class RuleDraft(
    val id: Long = 0,
    val name: String = "",
    val keyword: String = "",
    val extensions: String = "",
    val minSizeMb: String = "",
    val targetDir: String = "Documents/",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Long = 0,
) {
    val extensionSet: Set<String>
        get() = extensions.split(',', ' ', ';').map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotEmpty() }.toSet()

    fun toRule() = SortRule(
        id = id,
        name = name,
        keyword = keyword.ifBlank { null },
        extensions = extensionSet,
        minSizeBytes = ((minSizeMb.replace(',', '.').toDoubleOrNull() ?: 0.0) * MB).toLong(),
        targetDir = targetDir,
        enabled = enabled,
        priority = priority,
        createdAt = createdAt,
    )

    companion object {
        private const val MB = 1024.0 * 1024.0

        fun from(rule: SortRule) = RuleDraft(
            id = rule.id,
            name = rule.name,
            keyword = rule.keyword.orEmpty(),
            extensions = rule.extensions.joinToString(", "),
            minSizeMb = if (rule.minSizeBytes > 0) "%.0f".format(rule.minSizeBytes / MB) else "",
            targetDir = rule.targetDir,
            enabled = rule.enabled,
            priority = rule.priority,
            createdAt = rule.createdAt,
        )
    }
}

data class EditorState(
    val draft: RuleDraft,
    val previewCount: Int? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == 0L
}

data class RunState(val processed: Int, val total: Int, val moved: Int, val currentFile: String?) {
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total
}

data class RunResult(val moved: Int, val bytes: Long, val errors: Int, val batchId: String)

data class RulesUiState(
    val rules: List<SortRule> = emptyList(),
    val isLoading: Boolean = true,
    val autoPilotEnabled: Boolean = false,
    val run: RunState? = null,
    val lastResult: RunResult? = null,
    val editor: EditorState? = null,
) {
    val hasActiveRule: Boolean get() = rules.any { it.enabled }
}

private data class LocalRulesState(
    val lastResult: RunResult? = null,
    val editor: EditorState? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    observeRules: ObserveRulesUseCase,
    private val saveRule: SaveRuleUseCase,
    private val toggleRule: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val previewRule: PreviewRuleUseCase,
    private val undoRun: UndoAutoPilotRunUseCase,
    private val workManager: WorkManager,
) : ViewModel() {

    private val local = MutableStateFlow(LocalRulesState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var previewJob: Job? = null
    private var resultJob: Job? = null

    /** Progression lue depuis WorkManager : reste juste même si l'écran a été quitté puis rouvert. */
    private val runState: Flow<RunState?> = AutoSortWorker.observeNowRunning(workManager).map { info ->
        info?.takeIf { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }?.let {
            RunState(
                processed = it.progress.getInt(AutoSortWorker.KEY_PROCESSED, 0),
                total = it.progress.getInt(AutoSortWorker.KEY_TOTAL, 0),
                moved = it.progress.getInt(AutoSortWorker.KEY_MOVED, 0),
                currentFile = it.progress.getString(AutoSortWorker.KEY_CURRENT),
            )
        }
    }

    val uiState: StateFlow<RulesUiState> = combine(
        observeRules(),
        AutoSortWorker.observePeriodicEnabled(workManager),
        runState,
        local,
    ) { rules, periodic, run, loc ->
        RulesUiState(
            rules = rules,
            isLoading = false,
            autoPilotEnabled = periodic,
            run = run,
            lastResult = loc.lastResult,
            editor = loc.editor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    // ------------------------------------------------------------ Auto-Pilot

    fun setAutoPilot(enabled: Boolean) {
        AutoSortWorker.setPeriodic(workManager, enabled)
        viewModelScope.launch {
            _messages.send(if (enabled) "Auto-Pilot activé : passage toutes les 12 h" else "Auto-Pilot automatique désactivé")
        }
    }

    fun runNow() {
        if (uiState.value.run != null) return
        local.update { it.copy(lastResult = null) }
        val id: UUID = AutoSortWorker.runNow(workManager)
        resultJob?.cancel()
        resultJob = viewModelScope.launch {
            // On n'attend QUE la fin de cette exécution-ci (pas un résultat ancien)
            val info = AutoSortWorker.observeRun(workManager, id).filterNotNull().first { it.state.isFinished }
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val out = info.outputData
                    val result = RunResult(
                        moved = out.getInt(AutoSortWorker.KEY_MOVED, 0),
                        bytes = out.getLong(AutoSortWorker.KEY_BYTES, 0),
                        errors = out.getInt(AutoSortWorker.KEY_ERRORS, 0),
                        batchId = out.getString(AutoSortWorker.KEY_BATCH).orEmpty(),
                    )
                    local.update { it.copy(lastResult = result) }
                }
                WorkInfo.State.FAILED -> _messages.send(
                    if (info.outputData.getString(AutoSortWorker.KEY_ERROR) == AutoSortWorker.ERROR_PERMISSION)
                        "Accès au stockage requis pour l'Auto-Pilot"
                    else "L'Auto-Pilot a rencontré une erreur"
                )
                else -> Unit // annulé
            }
        }
    }

    fun undoLastRun() {
        val result = local.value.lastResult ?: return
        if (result.batchId.isEmpty()) return
        viewModelScope.launch {
            undoRun(result.batchId)
                .onSuccess { count ->
                    local.update { it.copy(lastResult = null) }
                    _messages.send("$count fichier(s) remis dans Download")
                }
                .onFailure { _messages.send("Annulation impossible : ${it.message}") }
        }
    }

    // ------------------------------------------------------------ Règles

    fun toggle(rule: SortRule, enabled: Boolean) {
        viewModelScope.launch { toggleRule(rule.id, enabled) }
    }

    fun delete(rule: SortRule) {
        viewModelScope.launch {
            deleteRuleUseCase(rule.id)
            _messages.send("Règle \"${rule.name}\" supprimée")
        }
    }

    // ------------------------------------------------------------ Éditeur

    fun openEditor(rule: SortRule? = null) {
        val draft = rule?.let(RuleDraft::from) ?: RuleDraft()
        local.update { it.copy(editor = EditorState(draft)) }
        refreshPreview(draft)
    }

    /** Exemple du cahier des charges : PDF contenant "EDF" -> Documents/Factures. */
    fun openExampleRule() {
        val draft = RuleDraft(name = "Factures EDF", keyword = "EDF", extensions = "pdf", targetDir = "Documents/Factures")
        local.update { it.copy(editor = EditorState(draft)) }
        refreshPreview(draft)
    }

    fun updateDraft(transform: (RuleDraft) -> RuleDraft) {
        val editor = local.value.editor ?: return
        val draft = transform(editor.draft)
        local.update { it.copy(editor = editor.copy(draft = draft, error = null)) }
        refreshPreview(draft)
    }

    fun onFolderPicked(relativePath: String?) {
        if (relativePath == null) {
            viewModelScope.launch { _messages.send("Choisis un dossier du stockage interne") }
            return
        }
        updateDraft { it.copy(targetDir = relativePath) }
    }

    fun closeEditor() {
        previewJob?.cancel()
        local.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = local.value.editor ?: return
        local.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            saveRule(editor.draft.toRule())
                .onSuccess {
                    closeEditor()
                    _messages.send(if (editor.isNew) "Règle créée" else "Règle mise à jour")
                }
                .onFailure { e ->
                    local.update { s -> s.copy(editor = s.editor?.copy(isSaving = false, error = e.message)) }
                }
        }
    }

    /** Aperçu "N fichiers concernés", recalculé 300 ms après la dernière frappe. */
    private fun refreshPreview(draft: RuleDraft) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(300)
            val count = runCatching { previewRule(draft.toRule()) }.getOrNull()
            local.update { s -> s.copy(editor = s.editor?.copy(previewCount = count)) }
        }
    }
}
