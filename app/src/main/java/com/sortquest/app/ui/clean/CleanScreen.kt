package com.sortquest.app.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.ui.components.EmptyState
import com.sortquest.app.ui.components.FileKindIcon
import com.sortquest.app.ui.components.ScanStatusBanner
import com.sortquest.app.ui.components.ShimmerList
import com.sortquest.app.ui.components.readableParent
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.theme.Neon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CleanScreen(viewModel: CleanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val formatBytes = rememberBytesFormatter()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // insets gérés par le Scaffold racine
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // En-tête : espace récupérable + relance
            Row(
                Modifier.fillMaxWidth().padding(16.dp)
                    .background(Neon.Surface, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CleaningServices, null, tint = Neon.Lime, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Espace récupérable", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                    Text(formatBytes(state.reclaimableBytes), style = MaterialTheme.typography.headlineSmall, color = Neon.Lime)
                    Text("${state.totalCopies} copie(s) en double", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                }
                IconButton(onClick = viewModel::analyze, enabled = state.scan == null) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Relancer l'analyse", tint = Neon.Cyan)
                }
            }

            state.scan?.let { ScanStatusBanner(it, Modifier.padding(horizontal = 16.dp)) }

            TabRow(selectedTabIndex = tab, containerColor = Neon.Background, contentColor = Neon.Cyan) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Doublons (${state.groups.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Fichiers lourds") })
            }

            Box(Modifier.weight(1f)) {
                when {
                    // Chargement initial ou scan en cours sans résultat : squelettes animés
                    !state.isLoaded || (state.scan != null && state.groups.isEmpty() && state.largest.isEmpty()) ->
                        Box(Modifier.padding(16.dp)) { ShimmerList(count = 5, itemHeight = 84.dp) }
                    tab == 0 -> DuplicatesTab(
                        state = state,
                        formatBytes = formatBytes,
                        onKeep = viewModel::setKeep,
                        onTrashGroup = viewModel::trashCopies,
                        onTrashAll = { confirmAll = true },
                    )
                    else -> LargestTab(state.largest, state.busy, formatBytes, viewModel::trashFile)
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, tint = Neon.Amber) },
            title = { Text("Supprimer toutes les copies ?") },
            text = {
                Text(
                    "${state.totalCopies} copie(s) iront à la corbeille (${formatBytes(state.reclaimableBytes)}). " +
                        "Un exemplaire de chaque fichier est conservé. Récupérables pendant 7 jours."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; viewModel.trashAllCopies() }) { Text("Supprimer", color = Neon.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } },
            containerColor = Neon.Surface,
        )
    }
}

@Composable
private fun DuplicatesTab(
    state: CleanUiState,
    formatBytes: (Long) -> String,
    onKeep: (DuplicateGroup, Long) -> Unit,
    onTrashGroup: (DuplicateGroup) -> Unit,
    onTrashAll: () -> Unit,
) {
    if (state.groups.isEmpty()) {
        EmptyState(Icons.Rounded.Verified, "Aucun doublon", "Ton stockage ne contient aucune copie identique connue.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "all") {
            Button(
                onClick = onTrashAll,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Lime, contentColor = Neon.Background),
            ) { Text("Garder 1 exemplaire de chaque · libérer ${formatBytes(state.reclaimableBytes)}") }
        }
        items(state.groups, key = { it.hash }) { group ->
            DuplicateGroupCard(group, state.keptId(group), state.busy, formatBytes, onKeep, onTrashGroup, Modifier.animateItem())
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    keptId: Long,
    busy: Boolean,
    formatBytes: (Long) -> String,
    onKeep: (DuplicateGroup, Long) -> Unit,
    onTrash: (DuplicateGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileKindIcon(group.files.first().extension)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.files.first().name, style = MaterialTheme.typography.titleMedium,
                    color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${group.files.size} exemplaires · ${formatBytes(group.sizeBytes)} chacun",
                    style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
            }
        }
        Text("Choisis l'exemplaire à garder :", style = MaterialTheme.typography.labelSmall, color = Neon.Cyan)
        group.files.forEach { file ->
            Row(
                Modifier.fillMaxWidth().clickable { onKeep(group, file.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = file.id == keptId,
                    onClick = { onKeep(group, file.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = Neon.Lime),
                )
                Column(Modifier.weight(1f)) {
                    Text(file.path.readableParent(), style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${file.name} · ${DATE_FORMAT.format(Date(file.lastModified))}",
                        style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OutlinedButton(onClick = { onTrash(group) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Supprimer ${group.copies} copie(s) · ${formatBytes(group.reclaimableBytes)}", color = Neon.Red)
        }
    }
}

@Composable
private fun LargestTab(
    files: List<FileItem>,
    busy: Boolean,
    formatBytes: (Long) -> String,
    onTrash: (FileItem) -> Unit,
) {
    if (files.isEmpty()) {
        EmptyState(Icons.Rounded.Verified, "Aucun fichier de plus de 50 Mo", "Rien de lourd à signaler.")
        return
    }
    val max = files.first().sizeBytes.coerceAtLeast(1)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "hint") {
            Text("Top ${files.size} des fichiers de plus de 50 Mo", style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted)
        }
        items(files, key = { it.id }) { file ->
            Column(
                Modifier.animateItem().fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileKindIcon(file.extension)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file.path.readableParent(), style = MaterialTheme.typography.labelSmall,
                            color = Neon.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatBytes(file.sizeBytes), style = MaterialTheme.typography.labelLarge, color = Neon.Amber)
                    TextButton(onClick = { onTrash(file) }, enabled = !busy) { Text("Corbeille", color = Neon.Red) }
                }
                // Barre proportionnelle au plus gros fichier : repère visuel immédiat
                Box(Modifier.fillMaxWidth().height(4.dp).background(Neon.SurfaceHigh, RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier.fillMaxWidth(file.sizeBytes.toFloat() / max).height(4.dp)
                            .background(Neon.Amber, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
