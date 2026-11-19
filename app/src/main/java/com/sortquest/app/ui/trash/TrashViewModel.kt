package com.sortquest.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.model.TrashItem
import com.sortquest.app.domain.usecase.DeleteForeverUseCase
import com.sortquest.app.domain.usecase.EmptyTrashUseCase
import com.sortquest.app.domain.usecase.ObserveTrashUseCase
import com.sortquest.app.domain.usecase.RestoreFromTrashUseCase
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

data class TrashUiState(
    val isLoaded: Boolean = false,
    val items: List<TrashItem> = emptyList(),
    /** Éléments en cours de restauration/suppression (boutons désactivés). */
    val busyIds: Set<Long> = emptySet(),
    val isEmptying: Boolean = false,
    /** Horloge de référence pour "N jours restants" (figée à l'ouverture, rafraîchie par refreshClock). */
    val now: Long = System.currentTimeMillis(),
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

private data class LocalTrashState(
    val busyIds: Set<Long> = emptySet(),
    val isEmptying: Boolean = false,
    val now: Long = System.currentTimeMillis(),
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    observeTrash: ObserveTrashUseCase,
    private val restoreUseCase: RestoreFromTrashUseCase,
    private val deleteForeverUseCase: DeleteForeverUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(LocalTrashState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<TrashUiState> = combine(observeTrash(), local) { items, loc ->
        TrashUiState(isLoaded = true, items = items, busyIds = loc.busyIds, isEmptying = loc.isEmptying, now = loc.now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun refreshClock() = local.update { it.copy(now = System.currentTimeMillis()) }

    fun restore(item: TrashItem) = runOn(item) {
        restoreUseCase(item)
            .onSuccess { _messages.send("${item.fileName} restauré à son emplacement d'origine") }
            .onFailure { _messages.send("Restauration impossible : ${it.message}") }
    }

    fun deleteForever(item: TrashItem) = runOn(item) {
        deleteForeverUseCase(item)
            .onSuccess { _messages.send("${item.fileName} supprimé définitivement") }
            .onFailure { _messages.send(it.message ?: "Suppression impossible") }
    }

    fun emptyTrash() {
        if (local.value.isEmptying) return
        local.update { it.copy(isEmptying = true) }
        viewModelScope.launch {
            val result = runCatching { emptyTrashUseCase() }
            local.update { it.copy(isEmptying = false) }
            result
                .onSuccess { _messages.send("Corbeille vidée : ${it.deletedFiles} fichier(s) supprimé(s)") }
                .onFailure { _messages.send("Échec du vidage : ${it.message}") }
        }
    }

    private fun runOn(item: TrashItem, block: suspend () -> Unit) {
        if (item.id in local.value.busyIds) return // anti double-clic
        local.update { it.copy(busyIds = it.busyIds + item.id) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busyIds = it.busyIds - item.id) }
            }
        }
    }
}
