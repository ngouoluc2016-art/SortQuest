package com.sortquest.app.domain.repository

import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.ThematicFolder
import kotlinx.coroutines.flow.Flow

/** Dossier réel déjà rempli par SortQuest (vue "Rangés"). */
data class FolderSummary(
    val relativeDir: String,
    val name: String,
    val fileCount: Int,
    val totalBytes: Long,
    val lastModified: Long,
)

/** Élément réel d'un dossier (fichier ou sous-dossier), lu sur le disque. */
data class FolderEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: FileKind,
    /** Pour un sous-dossier : nombre de fichiers qu'il contient. */
    val childCount: Int = 0,
)

interface OrganizationRepository {
    fun observeFolders(): Flow<List<ThematicFolder>>
    /** Ajoute les dossiers de la gamme absents (nouvelle installation ou nouvelle version). */
    suspend fun ensureCatalog()
    suspend fun setFolderEnabled(id: String, enabled: Boolean)
    suspend fun addCustomFolder(name: String, keywords: Set<String>, extensions: Set<String>): ThematicFolder
    suspend fun deleteCustomFolder(id: String)

    fun observePreset(): Flow<OrganizationPreset>
    suspend fun setPreset(preset: OrganizationPreset)

    /** Recalcule la destination proposée de tous les fichiers à trier. @return nombre de fichiers mis à jour. */
    suspend fun refreshSuggestions(): Int

    /** Nombre de fichiers ignorés car protégés lors du dernier scan. */
    fun observeProtectedCount(): Flow<Int>

    /** Dossiers de premier niveau sous Documents/SortQuest, avec leur contenu réel. */
    suspend fun sortedFolders(): List<FolderSummary>
    /** Contenu réel d'un dossier (fichiers cachés et protégés exclus). */
    suspend fun listFolder(relativeDir: String): List<FolderEntry>
}
