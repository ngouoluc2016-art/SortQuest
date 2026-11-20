package com.sortquest.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.FolderSummary
import com.sortquest.app.domain.repository.OrganizationRepository
import com.sortquest.app.domain.usecase.ChoosePresetUseCase
import com.sortquest.app.domain.usecase.ObservePendingFilesUseCase
import com.sortquest.app.domain.usecase.ObservePresetUseCase
import com.sortquest.app.domain.usecase.ObserveThematicFoldersUseCase
import com.sortquest.app.domain.usecase.OrganizeFilesUseCase
import com.sortquest.app.domain.usecase.SortedFoldersUseCase
import com.sortquest.app.domain.usecase.TrashFilesUseCase
import com.sortquest.app.domain.usecase.UndoAutoPilotRunUseCase
import com.sortquest.app.workers.ScanStatus
import com.sortquest.app.workers.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filtres rapides par type, pour retrouver un fichier d'un coup d'œil. */
enum class KindFilter(val label: String, val kinds: Set<FileKind>) {
    ALL("Tout", emptySet()),
    IMAGES("Images", setOf(FileKind.IMAGE)),
    VIDEOS("Vidéos", setOf(FileKind.VIDEO)),
    DOCUMENTS("Documents", setOf(FileKind.PDF, FileKind.DOCUMENT)),
    AUDIO("Audio", setOf(FileKind.AUDIO)),
    ARCHIVES("Archives & APK", setOf(FileKind.ARCHIVE, FileKind.APK)),
    OTHER("Autres", setOf(FileKind.OTHER)),
}

enum class OrganizeTab { TO_SORT, SORTED }

/** Fichiers qui partagent la même destination proposée : c'est le "plan de rangement" visible. */
data class DestinationGroup(
    val relativeDir: String,
    val label: String,
    val icon: FolderIcon,
    val files: List<FileItem>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

data class OrganizeUiState(
    val isLoaded: Boolean = false,
    val tab: OrganizeTab = OrganizeTab.TO_SORT,
    val totalPending: Int = 0,
    val groups: List<DestinationGroup> = emptyList(),
    val visibleCount: Int = 0,
    val filter: KindFilter = KindFilter.ALL,
    val query: String = "",
    val selection: Set<Long> = emptySet(),
    val preset: OrganizationPreset = OrganizationPreset.BY_THEME,
    val folders: List<ThematicFolder> = emptyList(),
    val protectedCount: Int = 0,
    val storage: StorageStats? = null,
    val scan: ScanStatus? = null,
    val busy: Boolean = false,
    val sorted: List<FolderSummary>? = null, // null = pas encore chargé
) {
    val activeFolderCount: Int get() = folders.count { it.enabled || it.locked }
    val selectionMode: Boolean get() = selection.isNotEmpty()
    fun selectedFiles(): List<FileItem> = groups.flatMap { it.files }.filter { it.id in selection }
}

sealed interface OrganizeEvent {
    data class Organized(val moved: Int, val failed: Int, val batchId: String) : OrganizeEvent
    data class Message(val text: String) : OrganizeEvent
}

private data class LocalState(
    val tab: OrganizeTab = OrganizeTab.TO_SORT,
    val filter: KindFilter = KindFilter.ALL,
    val query: String = "",
    val selection: Set<Long> = emptySet(),
    val storage: StorageStats? = null,
    val busy: Boolean = false,
    val sorted: List<FolderSummary>? = null,
)

private data class Sources(
    val files: List<FileItem>,
    val preset: OrganizationPreset,
    val folders: List<ThematicFolder>,
    val protectedCount: Int,
    val scan: ScanStatus?,
)

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    observePendingFiles: ObservePendingFilesUseCase,
    observePreset: ObservePresetUseCase,
    observeFolders: ObserveThematicFoldersUseCase,
    organizationRepository: OrganizationRepository,
    private val fileRepository: FileRepository,
    private val choosePreset: ChoosePresetUseCase,
    private val organizeFiles: OrganizeFilesUseCase,
    private val trashFiles: TrashFilesUseCase,
    private val undoBatch: UndoAutoPilotRunUseCase,
    private val sortedFolders: SortedFoldersUseCase,
    private val workManager: WorkManager,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val _events = Channel<OrganizeEvent>(Channel.BUFFERED)
    val events: Flow<OrganizeEvent> = _events.receiveAsFlow()

    private val sources: Flow<Sources> = combine(
        observePendingFiles(),
        observePreset(),
        observeFolders(),
        organizationRepository.observeProtectedCount(),
        ScanWorker.observeStatus(workManager),
    ) { files, preset, folders, protectedCount, scan -> Sources(files, preset, folders, protectedCount, scan) }

    val uiState: StateFlow<OrganizeUiState> = combine(sources, local) { src, loc ->
        val q = loc.query.trim().lowercase()
        val visible = src.files.filter { f ->
            (loc.filter == KindFilter.ALL || f.kind in loc.filter.kinds) &&
                (q.isEmpty() || f.name.lowercase().contains(q) || (f.suggestedLabel?.lowercase()?.contains(q) == true))
        }
        val groups = visible
            .groupBy { it.suggestedTargetDir }
            .map { (dir, files) ->
                val first = files.first()
                DestinationGroup(
                    relativeDir = dir,
                    label = first.suggestedLabel ?: dir.substringAfterLast('/'),
                    icon = first.suggestedIcon ?: FolderIcon.OTHER,
                    files = files.sortedByDescending { it.lastModified },
                )
            }
            .sortedWith(compareByDescending<DestinationGroup> { it.files.size }.thenBy { it.label })
        OrganizeUiState(
            isLoaded = true,
            tab = loc.tab,
            totalPending = src.files.size,
            groups = groups,
            visibleCount = visible.size,
            filter = loc.filter,
            query = loc.query,
            // une sélection ne garde que des fichiers encore à trier
            selection = loc.selection.filter { id -> src.files.any { it.id == id } }.toSet(),
            preset = src.preset,
            folders = src.folders,
            protectedCount = src.protectedCount,
            storage = loc.storage,
            scan = src.scan,
            busy = loc.busy,
            sorted = loc.sorted,
        )
    }
        .flowOn(Dispatchers.Default) // tri et regroupement hors du thread d'affichage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeUiState())

    init { refreshStorage() }

    // ------------------------------------------------------------ Navigation interne

    fun setTab(tab: OrganizeTab) {
        local.update { it.copy(tab = tab, selection = emptySet()) }
        if (tab == OrganizeTab.SORTED) loadSorted()
    }

    fun setFilter(filter: KindFilter) = local.update { it.copy(filter = filter) }
    fun setQuery(query: String) = local.update { it.copy(query = query) }
    fun rescan() = ScanWorker.enqueue(workManager)

    // ------------------------------------------------------------ Sélection

    fun toggle(file: FileItem) = local.update {
        it.copy(selection = if (file.id in it.selection) it.selection - file.id else it.selection + file.id)
    }

    fun toggleGroup(group: DestinationGroup) = local.update { s ->
        val ids = group.files.map { it.id }.toSet()
        s.copy(selection = if (s.selection.containsAll(ids)) s.selection - ids else s.selection + ids)
    }

    fun clearSelection() = local.update { it.copy(selection = emptySet()) }

    // ------------------------------------------------------------ Actions

    fun choosePreset(preset: OrganizationPreset) {
        viewModelScope.launch {
            val n = choosePreset.invoke(preset)
            _events.send(OrganizeEvent.Message("Réorganisation « ${preset.title} » appliquée : $n destination(s) recalculée(s)"))
        }
    }

    /** Range tous les fichiers visibles (filtre et recherche appliqués) dans leur destination proposée. */
    fun organizeAllVisible() = organize(uiState.value.groups.flatMap { it.files }, null)

    fun organizeGroup(group: DestinationGroup) = organize(group.files, null)

    fun organizeSelection() = organize(uiState.value.selectedFiles(), null)

    fun moveSelectionTo(folder: ThematicFolder) = organize(uiState.value.selectedFiles(), folder.relativePath)

    fun trashSelection() {
        val files = uiState.value.selectedFiles()
        if (files.isEmpty() || local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = trashFiles(files)
            local.update { it.copy(busy = false, selection = emptySet()) }
            refreshStorage()
            _events.send(OrganizeEvent.Message("${r.trashed} fichier(s) à la corbeille (récupérables 7 jours)"))
        }
    }

    private fun organize(files: List<FileItem>, targetDir: String?) {
        if (files.isEmpty() || local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = organizeFiles(files, targetDir)
            local.update { it.copy(busy = false, selection = emptySet()) }
            refreshStorage()
            if (local.value.sorted != null) loadSorted()
            _events.send(OrganizeEvent.Organized(r.moved, r.failed, r.batchId))
        }
    }

    fun undo(batchId: String) {
        viewModelScope.launch {
            undoBatch(batchId)
                .onSuccess { n ->
                    refreshStorage()
                    if (local.value.sorted != null) loadSorted()
                    _events.send(OrganizeEvent.Message("$n fichier(s) remis à leur place d'origine"))
                }
                .onFailure { _events.send(OrganizeEvent.Message("Annulation impossible : ${it.message}")) }
        }
    }

    fun loadSorted() {
        viewModelScope.launch {
            val list = runCatching { sortedFolders() }.getOrDefault(emptyList())
            local.update { it.copy(sorted = list) }
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            runCatching { fileRepository.storageStats() }.onSuccess { s -> local.update { it.copy(storage = s) } }
        }
    }
}
