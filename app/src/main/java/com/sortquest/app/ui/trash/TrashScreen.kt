package com.sortquest.app.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortquest.app.domain.model.TrashItem
import com.sortquest.app.ui.components.EmptyState
import com.sortquest.app.ui.components.FileKindIcon
import com.sortquest.app.ui.components.ShimmerList
import com.sortquest.app.ui.components.readableParent
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.theme.Neon

@Composable
fun TrashScreen(viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val formatBytes = rememberBytesFormatter()
    var confirmEmpty by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TrashItem?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // "N jours restants" juste à chaque retour sur l'écran
    LifecycleResumeEffect(Unit) {
        viewModel.refreshClock()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // En-tête : volume occupé + vidage manuel
            Column(
                Modifier.fillMaxWidth().padding(16.dp).background(Neon.Surface, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Neon.Red, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Corbeille", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                        Text(
                            "${state.items.size} fichier(s) · ${formatBytes(state.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                        )
                    }
                }
                Text(
                    "Les fichiers sont supprimés définitivement au bout de 7 jours. D'ici là, tu peux les restaurer.",
                    style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted,
                )
                Button(
                    onClick = { confirmEmpty = true },
                    enabled = state.items.isNotEmpty() && !state.isEmptying,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Red, contentColor = Neon.Background),
                ) {
                    if (state.isEmptying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Background)
                    else {
                        Icon(Icons.Rounded.DeleteSweep, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Vider la corbeille")
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    !state.isLoaded -> Box(Modifier.padding(16.dp)) { ShimmerList(count = 4, itemHeight = 96.dp) }
                    state.items.isEmpty() -> EmptyState(
                        Icons.Rounded.DeleteOutline, "Corbeille vide",
                        "Les fichiers glissés vers la gauche apparaîtront ici pendant 7 jours.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            TrashRow(
                                item = item,
                                now = state.now,
                                busy = item.id in state.busyIds || state.isEmptying,
                                formatBytes = formatBytes,
                                onRestore = { viewModel.restore(item) },
                                onDelete = { toDelete = item },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Neon.Red) },
            title = { Text("Vider la corbeille ?") },
            text = { Text("${state.items.size} fichier(s) (${formatBytes(state.totalBytes)}) seront supprimés définitivement. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; viewModel.emptyTrash() }) { Text("Vider", color = Neon.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annuler") } },
            containerColor = Neon.Surface,
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer définitivement ?") },
            text = { Text("\"${item.fileName}\" ne pourra plus être récupéré.") },
            confirmButton = {
                TextButton(onClick = { toDelete = null; viewModel.deleteForever(item) }) { Text("Supprimer", color = Neon.Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
            containerColor = Neon.Surface,
        )
    }
}

@Composable
private fun TrashRow(
    item: TrashItem,
    now: Long,
    busy: Boolean,
    formatBytes: (Long) -> String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = item.daysLeft(now)
    val urgency = when {
        days <= 1 -> Neon.Red
        days <= 3 -> Neon.Amber
        else -> Neon.Lime
    }
    Column(
        modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileKindIcon(item.fileName.substringAfterLast('.', ""))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.originalPath.readableParent()} · ${formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LinearProgressIndicator(
            progress = { item.remainingFraction(now) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = urgency,
            trackColor = Neon.SurfaceHigh,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (days <= 0) "Suppression au prochain nettoyage" else "Suppression définitive dans $days j",
                style = MaterialTheme.typography.labelSmall, color = urgency, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete, enabled = !busy) { Text("Supprimer", color = Neon.TextMuted) }
            OutlinedButton(onClick = onRestore, enabled = !busy) {
                Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.size(16.dp), tint = Neon.Cyan)
                Spacer(Modifier.size(6.dp))
                Text("Restaurer", color = Neon.Cyan)
            }
        }
    }
}
