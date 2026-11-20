package com.sortquest.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.usecase.ObserveTrashUseCase
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activités proposées sur l'écran d'accueil ; chacune s'ouvre en plein écran. */
enum class HomeActivity(val title: String, val icon: ImageVector, val color: Color) {
    ORGANIZE("Organiser", Icons.Rounded.FolderOpen, Neon.Cyan),
    SORTED("Mes dossiers", Icons.Rounded.Folder, Neon.Lime),
    SWIPE("Tri express", Icons.Rounded.Style, Neon.Magenta),
    CLEAN("Libérer de l'espace", Icons.Rounded.CleaningServices, Neon.Amber),
    TRASH("Corbeille", Icons.Rounded.Delete, Neon.Red),
    AUTOPILOT("Auto-Pilot", Icons.Rounded.AutoMode, Neon.Violet),
    HISTORY("Historique", Icons.Rounded.History, Neon.Cyan),
    FOLDERS("Dossiers thématiques", Icons.Rounded.Tune, Neon.Lime),
}

data class HomeUiState(
    val storage: StorageStats? = null,
    val pending: Int = 0,
    val trashCount: Int = 0,
    val trashBytes: Long = 0,
    val duplicateCopies: Int = 0,
    val duplicateBytes: Long = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    observeTrash: ObserveTrashUseCase,
) : ViewModel() {
    private val storage = MutableStateFlow<StorageStats?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        storage,
        fileRepository.observePendingCount(),
        observeTrash(),
        fileRepository.observeDuplicateGroups(),
    ) { s, pending, trash, dups ->
        HomeUiState(
            storage = s,
            pending = pending,
            trashCount = trash.size,
            trashBytes = trash.sumOf { it.sizeBytes },
            duplicateCopies = dups.sumOf { it.copies },
            duplicateBytes = dups.sumOf { it.reclaimableBytes },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        viewModelScope.launch { runCatching { fileRepository.storageStats() }.onSuccess { storage.value = it } }
    }
}

/**
 * Écran d'accueil épuré : l'état du stockage en un coup d'œil et une tuile par activité.
 * Toucher une tuile ouvre l'activité en plein écran (retour = accueil).
 */
@Composable
fun HomeScreen(
    onOpen: (HomeActivity) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatBytes = rememberBytesFormatter()
    LifecycleResumeEffect(viewModel, state.pending, state.trashCount) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize().background(Neon.Background),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SortQuest", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Neon.TextPrimary)
                    Text("Que veux-tu faire ?", style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Paramètres", tint = Neon.TextMuted) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { StorageCard(state.storage, formatBytes) }
        items(HomeActivity.entries, key = { it.name }) { activity ->
            val (subtitle, badge) = when (activity) {
                HomeActivity.ORGANIZE -> (if (state.pending > 0) "${state.pending} fichier(s) à trier" else "Tout est rangé") to state.pending
                HomeActivity.SORTED -> "${state.storage?.sortedCount ?: 0} fichier(s) rangé(s)" to 0
                HomeActivity.SWIPE -> "Un fichier à la fois, d'un geste" to 0
                HomeActivity.CLEAN ->
                    (if (state.duplicateCopies > 0) "${state.duplicateCopies} doublon(s) · ${formatBytes(state.duplicateBytes)}" else "Doublons et gros fichiers") to 0
                HomeActivity.TRASH ->
                    (if (state.trashCount > 0) "${state.trashCount} élément(s) · ${formatBytes(state.trashBytes)}" else "Vide · conservation 7 jours") to state.trashCount
                HomeActivity.AUTOPILOT -> "Rangement automatique par règles" to 0
                HomeActivity.HISTORY -> "Revoir et annuler les rangements" to 0
                HomeActivity.FOLDERS -> "Choisir la gamme de dossiers" to 0
            }
            ActivityTile(activity, subtitle, badge) { onOpen(activity) }
        }
    }
}

@Composable
private fun StorageCard(storage: StorageStats?, formatBytes: (Long) -> String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.Surface)
            .border(1.dp, Neon.Outline, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Stockage", style = MaterialTheme.typography.labelMedium, color = Neon.TextMuted)
        if (storage == null || storage.totalBytes <= 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = Neon.Cyan, trackColor = Neon.SurfaceHigh, strokeCap = StrokeCap.Round)
            return@Column
        }
        val used = storage.totalBytes - storage.freeBytes
        Text(
            "${formatBytes(used)} utilisés sur ${formatBytes(storage.totalBytes)}",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Neon.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (used.toFloat() / storage.totalBytes).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Neon.Cyan, trackColor = Neon.SurfaceHigh, strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${formatBytes(storage.freeBytes)} libres · santé du rangement ${(storage.health * 100).toInt()} %",
            style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
        )
    }
}

@Composable
private fun ActivityTile(activity: HomeActivity, subtitle: String, badge: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 132.dp).clip(shape).background(Neon.Surface)
            .border(1.dp, activity.color.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClickLabel = "Ouvrir", onClick = onClick)
            .semantics { contentDescription = activity.title }
            .padding(16.dp),
    ) {
        Column {
            Box(Modifier.size(48.dp).clip(CircleShape).background(activity.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(activity.icon, null, tint = activity.color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(activity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Neon.Magenta).padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text(if (badge > 99) "99+" else "$badge", style = MaterialTheme.typography.labelSmall, color = Neon.Background, fontWeight = FontWeight.Bold) }
        }
    }
}
