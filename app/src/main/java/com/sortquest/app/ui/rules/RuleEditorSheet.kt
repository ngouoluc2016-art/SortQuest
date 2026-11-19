package com.sortquest.app.ui.rules

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sortquest.app.ui.theme.Neon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    editor: EditorState,
    onChange: ((RuleDraft) -> RuleDraft) -> Unit,
    onFolderPicked: (String?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = editor.draft

    // SAF : sélection d'un dossier, converti en chemin relatif du stockage interne
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(treeUriToRelativePath(uri))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Neon.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (editor.isNew) "Nouvelle règle" else "Modifier la règle",
                style = MaterialTheme.typography.headlineSmall, color = Neon.TextPrimary,
            )

            Section("SI le fichier...")
            Field(draft.keyword, { v -> onChange { it.copy(keyword = v) } },
                "contient le mot", "ex. EDF, facture, contrat")
            Field(draft.extensions, { v -> onChange { it.copy(extensions = v) } },
                "a l'extension", "ex. pdf, docx (séparées par des virgules)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_EXTENSIONS.forEach { ext ->
                    val selected = ext in draft.extensionSet
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange { d ->
                                val set = d.extensionSet.toMutableSet().apply { if (selected) remove(ext) else add(ext) }
                                d.copy(extensions = set.joinToString(", "))
                            }
                        },
                        label = { Text(ext.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon.Violet.copy(alpha = 0.3f)),
                    )
                }
            }
            Field(draft.minSizeMb, { v -> onChange { it.copy(minSizeMb = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                "pèse au moins (Mo)", "laisser vide = pas de condition", KeyboardType.Decimal)

            Section("ALORS le ranger dans...")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(draft.targetDir, { v -> onChange { it.copy(targetDir = v) } },
                    "Dossier de destination", "ex. Documents/Factures", modifier = Modifier.weight(1f))
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Choisir un dossier", tint = Neon.Cyan)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTED_FOLDERS.forEach { folder ->
                    AssistChip(onClick = { onChange { it.copy(targetDir = folder) } }, label = { Text(folder) })
                }
            }

            Field(draft.name, { v -> onChange { it.copy(name = v) } },
                "Nom de la règle (facultatif)", "généré automatiquement si vide")

            // Aperçu en direct : rassure avant d'enregistrer
            val preview = editor.previewCount
            Text(
                when {
                    preview == null -> "Calcul de l'aperçu..."
                    preview == 0 -> "Aucun fichier actuel de Download n'est concerné."
                    else -> "$preview fichier(s) de Download seraient rangés maintenant."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if ((preview ?: 0) > 0) Neon.Lime else Neon.TextMuted,
            )

            editor.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Neon.Red) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Annuler") }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onSave,
                    enabled = !editor.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background),
                ) {
                    if (editor.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Enregistrer")
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Neon.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Neon.Cyan, focusedLabelColor = Neon.Cyan),
        modifier = modifier,
    )
}

/**
 * "content://.../tree/primary%3ADocuments%2FFactures" -> "Documents/Factures".
 * Null si le dossier n'est pas sur le stockage interne principal (carte SD, cloud).
 */
internal fun treeUriToRelativePath(uri: Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    if (docId.startsWith("primary:")) docId.removePrefix("primary:").trim('/').ifEmpty { null } else null
}.getOrNull()

private val QUICK_EXTENSIONS = listOf("pdf", "jpg", "png", "docx", "xlsx", "zip", "apk", "mp4")
private val SUGGESTED_FOLDERS = listOf(
    "Documents/Factures",
    "Documents/Administratif",
    "Documents/Contrats",
    "Pictures/Captures",
    "Documents/Archives",
)
