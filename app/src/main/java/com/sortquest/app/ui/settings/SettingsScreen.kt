package com.sortquest.app.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.repository.FeedbackSettings
import com.sortquest.app.domain.repository.SettingsRepository
import com.sortquest.app.ui.sort.components.rememberSortHaptics
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<FeedbackSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedbackSettings())

    fun setSound(enabled: Boolean) { viewModelScope.launch { repository.setSoundEnabled(enabled) } }
    fun setHaptics(enabled: Boolean) { viewModelScope.launch { repository.setHapticsEnabled(enabled) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = rememberSortHaptics()
    var hasAccess by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess(context)) }
    LifecycleResumeEffect(Unit) {
        hasAccess = StoragePermissionHelper.hasAllFilesAccess(context)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(Neon.Background)) {
        TopAppBar(
            title = { Text("Paramètres", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Neon.Background),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Retours sensoriels")
            ToggleRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Sons",
                subtitle = "Petits effets lors des tris. Coupés automatiquement en mode silencieux ou vibreur.",
                checked = settings.soundEnabled,
                onChange = viewModel::setSound,
            )
            ToggleRow(
                icon = Icons.Rounded.Vibration,
                title = "Vibrations",
                subtitle = "Retour haptique à chaque geste. Suit aussi le réglage système « Vibrations au toucher ».",
                checked = settings.hapticsEnabled,
                onChange = viewModel::setHaptics,
            )
            OutlinedButton(onClick = { feedback.sorted() }, modifier = Modifier.fillMaxWidth()) {
                Text("Tester le retour", color = Neon.Cyan)
            }

            Section("Accès aux fichiers")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = if (hasAccess) Neon.Lime else Neon.Amber)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (hasAccess) "Accès accordé" else "Accès non accordé",
                            style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                        Text("Gérable à tout moment dans les Paramètres Android.",
                            style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OutlinedButton(
                        onClick = { StoragePermissionHelper.openAllFilesAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ouvrir les paramètres d'accès") }
                }
            }

            Section("Confidentialité")
            Card {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Shield, null, tint = Neon.Lime)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "SortQuest fonctionne entièrement hors ligne. L'appli ne déclare pas la permission Internet : " +
                            "aucun fichier, nom de fichier ni statistique ne peut quitter ton téléphone.",
                        style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted,
                    )
                }
            }

            Text(
                "SortQuest ${context.versionName()}",
                style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Neon.Surface, RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (checked) Neon.Cyan else Neon.TextMuted)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.Background),
        )
    }
}

@Suppress("DEPRECATION")
private fun Context.versionName(): String = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        packageManager.getPackageInfo(packageName, 0).versionName
    }
}.getOrNull().orEmpty()
