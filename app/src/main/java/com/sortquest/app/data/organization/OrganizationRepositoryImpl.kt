package com.sortquest.app.data.organization

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.sortquest.app.data.file.FileCategorizer
import com.sortquest.app.data.file.OrganizationConfig
import com.sortquest.app.data.file.StoragePaths
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.data.mapper.toEntity
import com.sortquest.app.data.settings.settingsStore
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.organization.ProtectedFilesPolicy
import com.sortquest.app.domain.organization.ThematicCatalog
import com.sortquest.app.domain.repository.FolderEntry
import com.sortquest.app.domain.repository.FolderSummary
import com.sortquest.app.domain.repository.OrganizationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Lecture de la configuration d'organisation, partagée par le scan, l'Auto-Pilot et la recatégorisation. */
@Singleton
class OrganizationConfigSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    val presetFlow: Flow<OrganizationPreset> = context.settingsStore.data
        .map { p -> p[PRESET]?.let { runCatching { OrganizationPreset.valueOf(it) }.getOrNull() } ?: OrganizationPreset.BY_THEME }
        .distinctUntilChanged()

    suspend fun current(): OrganizationConfig {
        val folders = db.thematicFolderDao().all().map { it.toDomain() }.ifEmpty { ThematicCatalog.defaults }
        val rules = db.autoRuleDao().enabledRules().map { it.toDomain() }
        return OrganizationConfig(folders, presetFlow.first(), rules)
    }

    companion object {
        val PRESET = stringPreferencesKey("organization_preset")
        val PROTECTED_COUNT = intPreferencesKey("last_protected_count")
    }
}

/** Recalcule la destination proposée de tous les fichiers à trier selon la configuration actuelle. */
@Singleton
class SuggestionRefresher @Inject constructor(
    private val db: AppDatabase,
    private val config: OrganizationConfigSource,
    private val categorizer: FileCategorizer,
) {
    suspend fun refreshAll(): Int {
        val cfg = config.current()
        val fileDao = db.fileDao()
        val pending = fileDao.pendingFiles()
        db.withTransaction {
            pending.forEach { e ->
                val r = categorizer.categorize(e.name, e.sizeBytes, e.lastModified, File(e.path).parentFile?.name.orEmpty(), cfg)
                fileDao.updateSuggestion(e.id, r.placement.relativeDir, r.placement.label, r.placement.icon.name, r.category, r.placement.matchedKeyword)
            }
        }
        return pending.size
    }
}

@Singleton
class OrganizationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val config: OrganizationConfigSource,
    private val refresher: SuggestionRefresher,
    @IoDispatcher private val io: CoroutineDispatcher,
) : OrganizationRepository {

    private val folderDao = db.thematicFolderDao()
    private val fileDao = db.fileDao()

    override fun observeFolders(): Flow<List<ThematicFolder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureCatalog() = withContext(io) {
        folderDao.insertMissing(ThematicCatalog.defaults.map { it.toEntity() })
    }

    override suspend fun setFolderEnabled(id: String, enabled: Boolean) = withContext(io) {
        folderDao.setEnabled(id, enabled)
    }

    override suspend fun addCustomFolder(name: String, keywords: Set<String>, extensions: Set<String>): ThematicFolder =
        withContext(io) {
            val existing = folderDao.all()
            val path = ThematicCatalog.customPath(name)
            require(existing.none { it.relativePath.equals(path, ignoreCase = true) }) { "Un dossier porte déjà ce nom." }
            require(StoragePaths.isSafe(StoragePaths.resolve(path))) { "Emplacement non autorisé" }
            val folder = ThematicFolder(
                id = "custom_" + System.currentTimeMillis(),
                name = name,
                description = "Dossier personnel",
                relativePath = path,
                icon = FolderIcon.CUSTOM,
                keywords = keywords,
                extensions = extensions,
                builtIn = false,
                // Avant les dossiers "par type" (200+) : un dossier personnel l'emporte sur "Photos" ou "Documents"
                sortOrder = 150 + existing.count { !it.builtIn },
            )
            folderDao.insert(folder.toEntity())
            folder
        }

    override suspend fun deleteCustomFolder(id: String) = withContext(io) { folderDao.deleteCustom(id) }

    override fun observePreset(): Flow<OrganizationPreset> = config.presetFlow

    override suspend fun setPreset(preset: OrganizationPreset) {
        context.settingsStore.edit { it[OrganizationConfigSource.PRESET] = preset.name }
    }

    override suspend fun refreshSuggestions(): Int = withContext(io) { refresher.refreshAll() }

    override fun observeProtectedCount(): Flow<Int> =
        context.settingsStore.data.map { it[OrganizationConfigSource.PROTECTED_COUNT] ?: 0 }.distinctUntilChanged()

    override suspend fun sortedFolders(): List<FolderSummary> = withContext(io) {
        val root = StoragePaths.outputRoot
        root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { dir ->
                var count = 0
                var bytes = 0L
                var last = dir.lastModified()
                dir.walkTopDown().onEnter { !it.name.startsWith(".") }.forEach { f ->
                    if (f.isFile && !f.name.startsWith(".")) {
                        count++; bytes += f.length(); if (f.lastModified() > last) last = f.lastModified()
                    }
                }
                FolderSummary(StoragePaths.relative(dir), dir.name, count, bytes, last)
            }
            .filter { it.fileCount > 0 }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun listFolder(relativeDir: String): List<FolderEntry> = withContext(io) {
        val dir = StoragePaths.resolve(relativeDir)
        if (!dir.isDirectory || !StoragePaths.isSafe(dir)) return@withContext emptyList()
        dir.listFiles().orEmpty()
            .filter { f ->
                // Rien de caché ni de protégé n'est montré (ni donc proposé au déplacement)
                !f.name.startsWith(".") &&
                    (f.isDirectory || !ProtectedFilesPolicy.isProtected(StoragePaths.relative(f), f.length()))
            }
            .map { f ->
                FolderEntry(
                    path = f.absolutePath,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    lastModified = f.lastModified(),
                    kind = FileKinds.fromExtension(f.extension),
                    childCount = if (f.isDirectory) f.listFiles()?.count { !it.name.startsWith(".") } ?: 0 else 0,
                )
            }
            .sortedWith(compareBy<FolderEntry>({ !it.isDirectory }, { it.name.lowercase() }))
    }
}
