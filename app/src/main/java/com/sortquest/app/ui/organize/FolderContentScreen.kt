package com.sortquest.app.ui.organize

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.repository.FolderEntry
import com.sortquest.app.domain.usecase.ListFolderUseCase
import com.sortquest.app.ui.components.EmptyState
import com.sortquest.app.ui.components.FileThumbnail
import com.sortquest.app.ui.components.ShimmerList
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FolderContentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listFolder: ListFolderUseCase,
) : ViewModel() {
    val relativeDir: String = savedStateHandle.get<String>("dir").orEmpty()
    private val _entries = MutableStateFlow<List<FolderEntry>?>(null)
    val entries: StateFlow<List<FolderEntry>?> = _entries

    /** Relu à chaque retour sur l'écran : reflète l'état réel du disque. */
    fun refresh() {
        viewModelScope.launch { _entries.value = runCatching { listFolder(relativeDir) }.getOrDefault(emptyList()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentScreen(
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: FolderContentViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val formatBytes = rememberBytesFormatter()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(Neon.Background)) {
        TopAppBar(
            title = {
                Column {
                    Text(viewModel.relativeDir.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium)
                    Text(viewModel.relativeDir.removePrefix("Documents/"), style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Neon.Background),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        )
        val list = entries
        when {
            list == null -> Box(Modifier.padding(16.dp)) { ShimmerList(6, 60.dp) }
            list.isEmpty() -> EmptyState(Icons.Rounded.FolderOpen, "Dossier vide", "Aucun fichier dans ce dossier.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("${list.count { !it.isDirectory }} fichier(s), ${list.count { it.isDirectory }} dossier(s)", style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted) }
                items(list, key = { it.path }) { e ->
                    Row(
                        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(14.dp))
                            .clickable {
                                if (e.isDirectory) onOpenFolder(viewModel.relativeDir + "/" + e.name) else onOpenFile(e.path)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (e.isDirectory) {
                            Box(Modifier.size(52.dp).background(Neon.Cyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Folder, null, tint = Neon.Cyan, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            FileThumbnail(e.path, e.kind, e.lastModified, e.name.substringAfterLast('.', ""), size = 52.dp)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (e.isDirectory) "${e.childCount} élément(s)" else "${formatBytes(e.sizeBytes)} · ${DATE.format(Date(e.lastModified))}",
                                style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                            )
                        }
                        if (e.isDirectory) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Neon.TextMuted)
                    }
                }
            }
        }
    }
}

private val DATE = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
