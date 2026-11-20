package com.sortquest.app.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.HistoryEntry
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.usecase.UndoLastActionUseCase
import com.sortquest.app.ui.components.EmptyState
import com.sortquest.app.ui.components.ShimmerList
import com.sortquest.app.ui.components.readableParent
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class HistoryUiState(val isLoaded: Boolean = false, val entries: List<HistoryEntry> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(
    fileRepository: FileRepository,
    private val undoAction: UndoLastActionUseCase,
) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> = fileRepository.observeHistory(HISTORY_LIMIT)
        .map { HistoryUiState(isLoaded = true, entries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun undo(entry: HistoryEntry) {
        viewModelScope.launch {
            undoAction(entry.record.id)
                .onSuccess { r -> _messages.send(if (r != null) "${r.fileName} remis à sa place" else "Déjà annulé") }
                .onFailure { _messages.send("Annulation impossible : ${it.message}") }
        }
    }

    private companion object { const val HISTORY_LIMIT = 200 }
}

@Composable
fun HistoryScreen(snackbar: SnackbarHostState, onOpenFile: (String) -> Unit = {}, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    when {
        !state.isLoaded -> Box(Modifier.padding(16.dp)) { ShimmerList(count = 6, itemHeight = 64.dp) }
        state.entries.isEmpty() -> EmptyState(Icons.Rounded.History, "Aucune action", "Tes tris et ceux de l'Auto-Pilot apparaîtront ici.")
        else -> {
            val now = System.currentTimeMillis()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.entries, key = { it.record.id }) { entry ->
                    HistoryRow(
                        entry,
                        canUndo = !entry.undone && now - entry.record.timestamp < UNDO_WINDOW_MS,
                        onUndo = { viewModel.undo(entry) },
                        // Consulter le fichier là où il est MAINTENANT (destination, ou origine si annulé)
                        onOpen = { onOpenFile(if (entry.undone) entry.record.fromPath else entry.record.toPath ?: entry.record.fromPath) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, canUndo: Boolean, onUndo: () -> Unit, onOpen: () -> Unit) {
    val r = entry.record
    val (icon, color, verb) = actionVisual(r.action)
    Row(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(12.dp)
            .alpha(if (entry.undone) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.fileName, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = r.toPath?.let { "${r.fromPath.readableParent()} → ${it.readableParent()}" } ?: r.fromPath.readableParent()
            Text("$verb · $where", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(TIME_FORMAT.format(Date(r.timestamp)), style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                Text(
                    if (r.source == ActionSource.AUTO_PILOT) "AUTO-PILOT" else "MANUEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.source == ActionSource.AUTO_PILOT) Neon.Violet else Neon.Cyan,
                )
                if (r.xpGained > 0 && !entry.undone) Text("+${r.xpGained} XP", style = MaterialTheme.typography.labelSmall, color = Neon.Lime)
                if (entry.undone) Text("ANNULÉ", style = MaterialTheme.typography.labelSmall, color = Neon.Amber)
            }
        }
        if (canUndo) TextButton(onClick = onUndo) { Text("Annuler", color = Neon.Amber) }
    }
}

private fun actionVisual(action: ActionType): Triple<ImageVector, Color, String> = when (action) {
    ActionType.MOVE -> Triple(Icons.AutoMirrored.Rounded.DriveFileMove, Neon.Lime, "Classé")
    ActionType.TRASH -> Triple(Icons.Rounded.DeleteOutline, Neon.Red, "Corbeille")
    ActionType.VAULT -> Triple(Icons.Rounded.Lock, Neon.Magenta, "Coffre-fort")
    ActionType.SKIP -> Triple(Icons.Rounded.Schedule, Neon.Amber, "Reporté")
}

private val UNDO_WINDOW_MS = TimeUnit.DAYS.toMillis(7)
private val TIME_FORMAT = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
