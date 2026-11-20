package com.sortquest.app.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.usecase.AddCustomFolderUseCase
import com.sortquest.app.domain.usecase.DeleteCustomFolderUseCase
import com.sortquest.app.domain.usecase.ObservePresetUseCase
import com.sortquest.app.domain.usecase.ObserveThematicFoldersUseCase
import com.sortquest.app.domain.usecase.ToggleThematicFolderUseCase
import com.sortquest.app.ui.components.FolderBadge
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoldersUiState(
    val isLoaded: Boolean = false,
    val folders: List<ThematicFolder> = emptyList(),
    val preset: OrganizationPreset = OrganizationPreset.BY_THEME,
)

@HiltViewModel
class ThematicFoldersViewModel @Inject constructor(
    observeFolders: ObserveThematicFoldersUseCase,
    observePreset: ObservePresetUseCase,
    private val toggleFolder: ToggleThematicFolderUseCase,
    private val addFolder: AddCustomFolderUseCase,
    private val deleteFolder: DeleteCustomFolderUseCase,
) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<FoldersUiState> = combine(observeFolders(), observePreset()) { folders, preset ->
        FoldersUiState(true, folders, preset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun toggle(folder: ThematicFolder, enabled: Boolean) {
        viewModelScope.launch { toggleFolder(folder, enabled) }
    }

    /** @return message d'erreur, ou null si le dossier a été créé. */
    suspend fun create(name: String, keywords: String, extensions: String): String? =
        addFolder(name, keywords, extensions).fold(
            onSuccess = { _messages.send("Dossier « ${it.name} » créé"); null },
            onFailure = { it.message ?: "Création impossible" },
        )

    fun delete(folder: ThematicFolder) {
        viewModelScope.launch {
            deleteFolder(folder)
            _messages.send("Dossier « ${folder.name} » retiré de la gamme (les fichiers déjà rangés ne bougent pas)")
        }
    }
}

private enum class FolderGroup(val title: String) { ADMIN("Administratif"), MEDIA("Médias"), FILES("Fichiers"), CUSTOM("Mes dossiers") }

private fun ThematicFolder.group(): FolderGroup = when {
    !builtIn -> FolderGroup.CUSTOM
    relativePath.contains("/Administratif/") || id == "voyages" -> FolderGroup.ADMIN
    icon in setOf(FolderIcon.PHOTO, FolderIcon.VIDEO, FolderIcon.MUSIC, FolderIcon.SCREENSHOT) -> FolderGroup.MEDIA
    else -> FolderGroup.FILES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThematicFoldersScreen(onBack: () -> Unit, viewModel: ThematicFoldersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ThematicFolder?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Dossiers thématiques", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Neon.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                text = { Text("Créer un dossier") },
                containerColor = Neon.Cyan, contentColor = Neon.Background,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "info") {
                Row(Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = Neon.Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.preset == OrganizationPreset.BY_THEME || state.preset == OrganizationPreset.BY_THEME_AND_YEAR)
                            "Choisis les dossiers à utiliser. Un fichier destiné à un dossier désactivé part vers le dossier suivant qui lui correspond, ou dans Divers."
                        else
                            "Ta réorganisation actuelle (« ${state.preset.title} ») n'utilise pas cette gamme. Choisis « Par thème » dans Organiser pour l'activer.",
                        style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted,
                    )
                }
            }
            FolderGroup.entries.forEach { group ->
                val list = state.folders.filter { it.group() == group }
                if (list.isNotEmpty()) {
                    item(key = "g:${group.name}") {
                        Text(group.title, style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted, modifier = Modifier.padding(top = 10.dp))
                    }
                    items(list, key = { it.id }) { folder ->
                        FolderRow(folder, onToggle = { viewModel.toggle(folder, it) }, onDelete = { toDelete = folder })
                    }
                }
            }
        }
    }

    if (creating) {
        CreateFolderDialog(onCreate = viewModel::create, onDismiss = { creating = false })
    }
    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Retirer « ${f.name} » ?") },
            text = { Text("Le dossier ne recevra plus de fichiers. Les fichiers qui s'y trouvent déjà ne bougent pas.") },
            confirmButton = { TextButton(onClick = { toDelete = null; viewModel.delete(f) }) { Text("Retirer", color = Neon.Red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
            containerColor = Neon.Surface,
        )
    }
}

@Composable
private fun FolderRow(folder: ThematicFolder, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val active = folder.enabled || folder.locked
    Row(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderBadge(folder.icon, Modifier.alpha(if (active) 1f else 0.4f))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f).alpha(if (active) 1f else 0.5f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
            Text(folder.description, style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (folder.keywords.isNotEmpty()) {
                Text(
                    "Mots-clés : " + folder.keywords.take(6).joinToString(", ") + if (folder.keywords.size > 6) "…" else "",
                    style = MaterialTheme.typography.labelSmall, color = Neon.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(folder.relativePath.removePrefix("Documents/"), style = MaterialTheme.typography.labelSmall, color = Neon.Lime, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!folder.builtIn) {
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Retirer ${folder.name}", tint = Neon.TextMuted) }
        }
        if (folder.locked) {
            Icon(Icons.Rounded.Lock, "Toujours actif", tint = Neon.TextMuted, modifier = Modifier.padding(12.dp))
        } else {
            Switch(
                checked = folder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.Background),
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(onCreate: suspend (String, String, String) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submit by remember { mutableStateOf(0) }

    LaunchedEffect(submit) {
        if (submit > 0) {
            error = onCreate(name, keywords, extensions)
            if (error == null) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CreateNewFolder, null, tint = Neon.Cyan) },
        title = { Text("Nouveau dossier thématique") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, placeholder = { Text("ex. Recettes") }, singleLine = true)
                OutlinedTextField(keywords, { keywords = it }, label = { Text("Mots-clés (séparés par des virgules)") }, placeholder = { Text("ex. recette, cuisine, menu") })
                OutlinedTextField(extensions, { extensions = it }, label = { Text("Extensions (facultatif)") }, placeholder = { Text("ex. pdf, docx") }, singleLine = true)
                Text("Créé dans Documents/SortQuest/${name.trim().ifEmpty { "…" }}", style = MaterialTheme.typography.labelSmall, color = Neon.Lime)
                error?.let { Text(it, color = Neon.Red, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { submit++ }) { Text("Créer", color = Neon.Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Neon.Surface,
    )
}
