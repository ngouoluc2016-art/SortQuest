package com.sortquest.app.ui.rules

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.ui.theme.Neon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Onglet "Auto-Pilot" : sous-onglets Règles et Historique, snackbar partagé. */
@Composable
fun AutoPilotScreen() {
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // insets gérés par le Scaffold racine
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = Neon.Background, contentColor = Neon.Cyan) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Règles") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Historique") })
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) RulesScreen(snackbar) else HistoryScreen(snackbar)
            }
        }
    }
}

@Composable
fun RulesScreen(
    snackbar: SnackbarHostState,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var ruleToDelete by remember { mutableStateOf<SortRule?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openEditor() },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nouvelle règle") },
                containerColor = Neon.Cyan,
                contentColor = Neon.Background,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "autopilot") {
                AutoPilotCard(
                    state = state,
                    onToggleAuto = viewModel::setAutoPilot,
                    onRunNow = viewModel::runNow,
                    onUndoRun = viewModel::undoLastRun,
                )
            }
            item(key = "title") {
                Text(
                    "Mes règles (${state.rules.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Neon.TextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!state.isLoading && state.rules.isEmpty()) {
                item(key = "empty") { EmptyRules(onExample = viewModel::openExampleRule) }
            }
            items(state.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onClick = { viewModel.openEditor(rule) },
                    onToggle = { viewModel.toggle(rule, it) },
                    onDelete = { ruleToDelete = rule },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    state.editor?.let { editor ->
        RuleEditorSheet(
            editor = editor,
            onChange = viewModel::updateDraft,
            onFolderPicked = viewModel::onFolderPicked,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::closeEditor,
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Supprimer la règle ?") },
            text = { Text("\"${rule.name}\" sera supprimée. Les fichiers déjà rangés ne bougent pas.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(rule); ruleToDelete = null }) {
                    Text("Supprimer", color = Neon.Red)
                }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text("Annuler") } },
            containerColor = Neon.Surface,
        )
    }
}

@Composable
private fun AutoPilotCard(
    state: RulesUiState,
    onToggleAuto: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onUndoRun: () -> Unit,
) {
    val context = LocalContext.current
    val run = state.run
    Column(
        Modifier.fillMaxWidth()
            .background(Neon.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (run != null) Neon.Cyan else Neon.Outline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.AutoMode, null, tint = Neon.Cyan, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-Pilot", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                Text(
                    "Range le dossier Download selon tes règles actives",
                    style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Passage automatique toutes les 12 h", style = MaterialTheme.typography.bodyMedium,
                color = Neon.TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = state.autoPilotEnabled,
                onCheckedChange = onToggleAuto,
                enabled = state.hasActiveRule || state.autoPilotEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.Background),
            )
        }

        Button(
            onClick = onRunNow,
            enabled = run == null && state.hasActiveRule,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.size(8.dp))
            Text(if (run != null) "Rangement en cours..." else "Exécuter maintenant")
        }
        if (!state.hasActiveRule) {
            Text("Active au moins une règle pour lancer l'Auto-Pilot.",
                style = MaterialTheme.typography.labelSmall, color = Neon.Amber)
        }

        // Progression en direct
        AnimatedVisibility(visible = run != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val r = run ?: RunState(0, 0, 0, null)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.total == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Cyan, trackColor = Neon.SurfaceHigh)
                } else {
                    LinearProgressIndicator(
                        progress = { r.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Neon.Cyan,
                        trackColor = Neon.SurfaceHigh,
                    )
                }
                Text(
                    "${r.processed}/${r.total} fichiers examinés · ${r.moved} rangés",
                    style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                )
                r.currentFile?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Résultat de la dernière exécution lancée depuis cet écran
        AnimatedVisibility(visible = state.lastResult != null && run == null) {
            val result = state.lastResult ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().background(Neon.SurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Neon.Lime)
                Spacer(Modifier.size(10.dp))
                Text(
                    if (result.moved == 0) "Aucun fichier ne correspondait à tes règles."
                    else "${result.moved} fichier(s) rangé(s) · ${Formatter.formatShortFileSize(context, result.bytes)}" +
                        if (result.errors > 0) " · ${result.errors} erreur(s)" else "",
                    style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (result.moved > 0) TextButton(onClick = onUndoRun) { Text("Annuler", color = Neon.Amber) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(
    rule: SortRule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .background(Neon.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
            .alpha(if (rule.enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(rule.name, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rule.keyword?.let { ConditionChip("contient « $it »", Neon.Cyan) }
                if (rule.extensions.isNotEmpty()) {
                    ConditionChip(rule.extensions.joinToString(", ") { it.uppercase() }, Neon.Violet)
                }
                if (rule.minSizeBytes > 0) {
                    ConditionChip("≥ ${Formatter.formatShortFileSize(context, rule.minSizeBytes)}", Neon.Amber)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Neon.Lime, modifier = Modifier.size(14.dp))
                Text(rule.targetDir, style = MaterialTheme.typography.labelSmall, color = Neon.Lime,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val stats = buildString {
                append("${rule.filesMoved} fichier(s) rangé(s)")
                rule.lastRunAt?.let { append(" · dernier passage ${RUN_FORMAT.format(Date(it))}") }
            }
            Text(stats, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Lime, checkedThumbColor = Neon.Background),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer ${rule.name}", tint = Neon.TextMuted)
            }
        }
    }
}

@Composable
private fun ConditionChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyRules(onExample: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Aucune règle pour l'instant", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
        Text(
            "Une règle dit à l'Auto-Pilot où ranger certains fichiers. Exemple : tout PDF contenant « EDF » va dans Documents/Factures.",
            style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted,
        )
        OutlinedButton(onClick = onExample) { Text("Partir de cet exemple") }
    }
}

private val RUN_FORMAT = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
