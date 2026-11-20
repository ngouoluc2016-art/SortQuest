package com.sortquest.app.ui.organize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.organization.ProtectionReason
import com.sortquest.app.domain.repository.FolderSummary
import com.sortquest.app.ui.components.EmptyState
import com.sortquest.app.ui.components.FileThumbnail
import com.sortquest.app.ui.components.FolderBadge
import com.sortquest.app.ui.components.ScanStatusBanner
import com.sortquest.app.ui.components.ShimmerList
import com.sortquest.app.ui.components.readableParent
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.components.vector
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran principal : ORGANISER.
 *  - "À trier" : le plan de rangement, fichiers groupés par destination proposée, avec miniatures.
 *  - "Rangés"  : les dossiers déjà remplis, consultables fichier par fichier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenOptimize: () -> Unit,
    viewModel: OrganizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val formatBytes = rememberBytesFormatter()
    var showPresets by remember { mutableStateOf(false) }
    var showMoveTo by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var showProtection by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is OrganizeEvent.Organized -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val text = "${e.moved} fichier(s) rangé(s)" + if (e.failed > 0) " · ${e.failed} échec(s)" else ""
                    if (snackbar.showSnackbar(text, "Annuler", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        viewModel.undo(e.batchId)
                    }
                }
                is OrganizeEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
            }
        }
    }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.tab == OrganizeTab.TO_SORT && !state.selectionMode && state.visibleCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { confirmAll = true },
                    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    text = { Text(if (state.visibleCount == state.totalPending) "Tout ranger (${state.visibleCount})" else "Ranger ces ${state.visibleCount}") },
                    containerColor = Neon.Lime,
                    contentColor = Neon.Background,
                    expanded = !state.busy,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // État du stockage : affiché sur l'accueil (écran d'activité épuré)

            // Réorganisation choisie + gamme de dossiers
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip(Icons.Rounded.AutoAwesome, "Réorganisation", state.preset.title, Modifier.weight(1f)) { showPresets = true }
                SettingChip(Icons.Rounded.Tune, "Dossiers", "${state.activeFolderCount} actifs", Modifier.weight(1f), onOpenFolders)
            }
            if (state.protectedCount > 0) {
                Row(
                    Modifier.fillMaxWidth().clickable { showProtection = true }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = Neon.Lime, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "${state.protectedCount} fichier(s) système, caché(s) ou technique(s) protégé(s) : jamais déplacé(s)",
                        style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                    )
                }
            }
            state.scan?.let { ScanStatusBanner(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = state.tab == OrganizeTab.TO_SORT,
                    onClick = { viewModel.setTab(OrganizeTab.TO_SORT) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Neon.Cyan.copy(alpha = 0.2f), activeContentColor = Neon.Cyan),
                ) { Text("À trier (${state.totalPending})") }
                SegmentedButton(
                    selected = state.tab == OrganizeTab.SORTED,
                    onClick = { viewModel.setTab(OrganizeTab.SORTED) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Neon.Cyan.copy(alpha = 0.2f), activeContentColor = Neon.Cyan),
                ) { Text("Rangés") }
            }

            Box(Modifier.weight(1f)) {
                when (state.tab) {
                    OrganizeTab.TO_SORT -> ToSortTab(state, formatBytes, viewModel, onOpenFile)
                    OrganizeTab.SORTED -> SortedTab(state.sorted, formatBytes, onOpenFolder)
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.selectionMode,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SelectionBar(
                        count = state.selection.size,
                        busy = state.busy,
                        onOrganize = viewModel::organizeSelection,
                        onMoveTo = { showMoveTo = true },
                        onTrash = viewModel::trashSelection,
                        onClose = viewModel::clearSelection,
                    )
                }
            }
        }
    }

    if (showPresets) {
        PresetSheet(current = state.preset, onPick = { showPresets = false; viewModel.choosePreset(it) }, onDismiss = { showPresets = false })
    }
    if (showMoveTo) {
        MoveToSheet(
            folders = state.folders.filter { it.enabled || it.locked },
            onPick = { showMoveTo = false; viewModel.moveSelectionTo(it) },
            onDismiss = { showMoveTo = false },
        )
    }
    if (confirmAll) {
        ConfirmPlanDialog(state, formatBytes, onConfirm = { confirmAll = false; viewModel.organizeAllVisible() }, onDismiss = { confirmAll = false })
    }
    if (showProtection) {
        AlertDialog(
            onDismissRequest = { showProtection = false },
            icon = { Icon(Icons.Rounded.Shield, null, tint = Neon.Lime) },
            title = { Text("Fichiers protégés") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SortQuest ne déplace jamais ces fichiers, pour ne pas perturber le téléphone ou tes applications :")
                    ProtectionReason.entries.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { showProtection = false }) { Text("Compris") } },
            containerColor = Neon.Surface,
        )
    }
}

// ------------------------------------------------------------------ En-tête

@Composable
private fun StorageOverview(state: OrganizeUiState, formatBytes: (Long) -> String, onOpenOptimize: () -> Unit) {
    val s = state.storage
    Column(
        Modifier.fillMaxWidth().padding(16.dp).background(Neon.Surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stockage", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                Text(
                    if (s != null) "${formatBytes(s.totalBytes - s.freeBytes)} utilisés sur ${formatBytes(s.totalBytes)}" else "…",
                    style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary,
                )
            }
            TextButton(onClick = onOpenOptimize) { Text("Libérer de l'espace", color = Neon.Lime) }
        }
        if (s != null && s.totalBytes > 0) {
            val used = (s.totalBytes - s.freeBytes).toFloat() / s.totalBytes
            LinearProgressIndicator(
                progress = { used },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (used > 0.9f) Neon.Red else if (used > 0.75f) Neon.Amber else Neon.Cyan,
                trackColor = Neon.SurfaceHigh,
            )
            Text(
                "${formatBytes(s.freeBytes)} libres · ${state.totalPending} fichier(s) à trier · ${s.sortedCount} rangé(s)",
                style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
            )
        }
    }
}

@Composable
private fun SettingChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .background(Neon.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, Neon.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Neon.Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Neon.TextMuted)
    }
}

// ------------------------------------------------------------------ À trier

@Composable
private fun ToSortTab(
    state: OrganizeUiState,
    formatBytes: (Long) -> String,
    viewModel: OrganizeViewModel,
    onOpenFile: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Rechercher un fichier ou un dossier", color = Neon.TextMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Neon.TextMuted) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, "Effacer") }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Neon.Cyan, unfocusedBorderColor = Neon.Outline),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(KindFilter.entries) { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon.Cyan.copy(alpha = 0.2f), selectedLabelColor = Neon.Cyan),
                )
            }
        }
        when {
            !state.isLoaded -> Box(Modifier.padding(16.dp)) { ShimmerList(6, 64.dp) }
            state.totalPending == 0 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                EmptyState(Icons.Rounded.CheckCircle, "Tout est rangé", "Aucun fichier à trier. Lance une analyse après tes prochains téléchargements.")
                Button(onClick = viewModel::rescan, colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background)) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(6.dp)); Text("Analyser le stockage")
                }
            }
            state.groups.isEmpty() -> EmptyState(Icons.Rounded.Search, "Aucun résultat", "Aucun fichier ne correspond à ce filtre.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "h:${group.relativeDir}") {
                        GroupHeader(
                            group = group,
                            allSelected = group.files.all { it.id in state.selection },
                            busy = state.busy,
                            formatBytes = formatBytes,
                            onToggle = { viewModel.toggleGroup(group) },
                            onOrganize = { viewModel.organizeGroup(group) },
                        )
                    }
                    items(group.files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            selected = file.id in state.selection,
                            selectionMode = state.selectionMode,
                            formatBytes = formatBytes,
                            onOpen = { onOpenFile(file.path) },
                            onToggle = { viewModel.toggle(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: DestinationGroup,
    allSelected: Boolean,
    busy: Boolean,
    formatBytes: (Long) -> String,
    onToggle: () -> Unit,
    onOrganize: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = allSelected, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Neon.Cyan))
        FolderBadge(group.icon, size = 36.dp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text("→ ${group.label}", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${group.files.size} fichier(s) · ${formatBytes(group.totalBytes)} · ${group.relativeDir.removePrefix("Documents/")}",
                style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOrganize, enabled = !busy) { Text("Ranger", color = Neon.Lime) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    formatBytes: (Long) -> String,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Neon.Cyan.copy(alpha = 0.10f) else Neon.Surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onOpen() }, // toucher = consulter le fichier
                onLongClick = onToggle,                                    // appui long = sélectionner
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumbnail(file.path, file.kind, file.lastModified, file.extension, size = 52.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatBytes(file.sizeBytes)} · ${DATE.format(Date(file.lastModified))} · ${file.path.readableParent()}",
                style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (file.isDuplicate) Text("Doublon", style = MaterialTheme.typography.labelSmall, color = Neon.Red)
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Neon.Cyan))
    }
}

@Composable
private fun SelectionBar(count: Int, busy: Boolean, onOrganize: () -> Unit, onMoveTo: () -> Unit, onTrash: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp)
            .background(Neon.SurfaceHigh, RoundedCornerShape(18.dp))
            .border(1.dp, Neon.Cyan, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Annuler la sélection", tint = Neon.TextMuted) }
        Text("$count", style = MaterialTheme.typography.titleMedium, color = Neon.Cyan, modifier = Modifier.weight(1f))
        TextButton(onClick = onOrganize, enabled = !busy) { Text("Ranger", color = Neon.Lime) }
        IconButton(onClick = onMoveTo, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, "Déplacer vers un dossier", tint = Neon.Cyan) }
        IconButton(onClick = onTrash, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "Mettre à la corbeille", tint = Neon.Red) }
    }
}

// ------------------------------------------------------------------ Rangés

@Composable
private fun SortedTab(sorted: List<FolderSummary>?, formatBytes: (Long) -> String, onOpenFolder: (String) -> Unit) {
    when {
        sorted == null -> Box(Modifier.padding(16.dp)) { ShimmerList(5, 64.dp) }
        sorted.isEmpty() -> EmptyState(Icons.Rounded.FolderOpen, "Aucun dossier rangé", "Les fichiers que tu ranges apparaîtront ici, classés par dossier.")
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Dans Documents/SortQuest", style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted)
            }
            items(sorted, key = { it.relativeDir }) { folder ->
                Row(
                    Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp))
                        .clickable { onOpenFolder(folder.relativeDir) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FolderBadge(iconForFolderName(folder.name))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                        Text("${folder.fileCount} fichier(s) · ${formatBytes(folder.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Neon.TextMuted)
                }
            }
        }
    }
}

/** Icône d'un dossier réel de premier niveau, d'après le dossier de la gamme correspondant. */
private fun iconForFolderName(name: String) =
    com.sortquest.app.domain.organization.ThematicCatalog.defaults
        .firstOrNull { it.relativePath.substringAfter("SortQuest/").substringBefore('/').equals(name, true) }
        ?.icon ?: when (name.lowercase()) {
            "administratif" -> com.sortquest.app.domain.model.FolderIcon.DOCUMENT
            "chronologie" -> com.sortquest.app.domain.model.FolderIcon.CALENDAR
            "coffre-fort" -> com.sortquest.app.domain.model.FolderIcon.IDENTITY
            "par type" -> com.sortquest.app.domain.model.FolderIcon.ARCHIVE
            else -> com.sortquest.app.domain.model.FolderIcon.CUSTOM
        }

// ------------------------------------------------------------------ Feuilles et dialogues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(current: OrganizationPreset, onPick: (OrganizationPreset) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Neon.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choisir une réorganisation", style = MaterialTheme.typography.headlineSmall, color = Neon.TextPrimary)
            Text("Les fichiers déjà rangés ne bougent pas. Seule la destination des fichiers à trier change.", style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
            OrganizationPreset.entries.forEach { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (p == current) Neon.Cyan.copy(alpha = 0.10f) else Neon.SurfaceHigh, RoundedCornerShape(16.dp))
                        .border(1.dp, if (p == current) Neon.Cyan else Neon.Outline, RoundedCornerShape(16.dp))
                        .clickable { onPick(p) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(selected = p == current, onClick = { onPick(p) }, colors = RadioButtonDefaults.colors(selectedColor = Neon.Cyan))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.title + if (p == OrganizationPreset.BY_THEME) "  (recommandé)" else "", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                        Text(p.description, style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
                        Text(p.example, style = MaterialTheme.typography.labelSmall, color = Neon.Lime)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToSheet(folders: List<ThematicFolder>, onPick: (ThematicFolder) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Neon.Surface) {
        Text("Déplacer vers…", style = MaterialTheme.typography.headlineSmall, color = Neon.TextPrimary, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(folders, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().background(Neon.SurfaceHigh, RoundedCornerShape(14.dp)).clickable { onPick(f) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FolderBadge(f.icon, size = 36.dp)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary)
                        Text(f.relativePath.removePrefix("Documents/"), style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                    }
                }
            }
        }
    }
}

/** Récapitulatif visuel du plan avant de tout ranger. */
@Composable
private fun ConfirmPlanDialog(state: OrganizeUiState, formatBytes: (Long) -> String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Neon.Lime) },
        title = { Text("Ranger ${state.visibleCount} fichier(s) ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.groups.take(7).forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(g.icon.vector, null, tint = Neon.Cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(g.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${g.files.size}", color = Neon.Lime)
                    }
                }
                if (state.groups.size > 7) Text("… et ${state.groups.size - 7} autre(s) dossier(s)", color = Neon.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text("Annulable en un geste juste après.", style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ranger", color = Neon.Lime) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Neon.Surface,
    )
}

private val DATE = SimpleDateFormat("dd/MM/yy", Locale.FRANCE)
