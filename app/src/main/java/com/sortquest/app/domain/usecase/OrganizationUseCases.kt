package com.sortquest.app.domain.usecase

import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.FolderEntry
import com.sortquest.app.domain.repository.FolderSummary
import com.sortquest.app.domain.repository.OrganizationRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class ObserveThematicFoldersUseCase @Inject constructor(private val repo: OrganizationRepository) {
    operator fun invoke(): Flow<List<ThematicFolder>> = repo.observeFolders()
}

/** Active/désactive un dossier de la gamme puis recalcule les destinations proposées. */
class ToggleThematicFolderUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(folder: ThematicFolder, enabled: Boolean): Int {
        if (folder.locked) return 0 // "Divers" reste toujours actif
        repo.setFolderEnabled(folder.id, enabled)
        return repo.refreshSuggestions()
    }
}

class AddCustomFolderUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(name: String, keywordsText: String, extensionsText: String): Result<ThematicFolder> = runCatching {
        val cleanName = name.trim()
        if (cleanName.length < 2) throw RuleValidationException("Donne un nom d'au moins 2 caractères.")
        val keywords = keywordsText.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val extensions = extensionsText.split(',', ';', ' ').map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotEmpty() }.toSet()
        if (keywords.isEmpty() && extensions.isEmpty()) {
            throw RuleValidationException("Ajoute au moins un mot-clé ou une extension pour savoir quels fichiers y ranger.")
        }
        repo.addCustomFolder(cleanName, keywords, extensions).also { repo.refreshSuggestions() }
    }
}

class DeleteCustomFolderUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(folder: ThematicFolder) {
        if (folder.builtIn) return
        repo.deleteCustomFolder(folder.id)
        repo.refreshSuggestions()
    }
}

class ObservePresetUseCase @Inject constructor(private val repo: OrganizationRepository) {
    operator fun invoke(): Flow<OrganizationPreset> = repo.observePreset()
}

/** Choisit une réorganisation par défaut : les destinations proposées sont recalculées aussitôt. */
class ChoosePresetUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(preset: OrganizationPreset): Int {
        repo.setPreset(preset)
        return repo.refreshSuggestions()
    }
}

data class OrganizeResult(val batchId: String, val moved: Int, val failed: Int, val bytes: Long)

/**
 * Range une sélection de fichiers en une seule opération annulable.
 * [targetDir] null = chaque fichier va dans SA destination proposée ; sinon tous vont dans ce dossier.
 */
class OrganizeFilesUseCase @Inject constructor(private val sortFile: SortFileUseCase) {
    suspend operator fun invoke(files: List<FileItem>, targetDir: String? = null): OrganizeResult {
        val batchId = UUID.randomUUID().toString()
        var moved = 0
        var failed = 0
        var bytes = 0L
        for (file in files) {
            sortFile(file, SwipeDirection.RIGHT, combo = 0, inExpressSession = false, customTargetDir = targetDir, batchId = batchId)
                .onSuccess { moved++; bytes += file.sizeBytes }
                .onFailure { failed++ }
        }
        return OrganizeResult(batchId, moved, failed, bytes)
    }
}

class ObservePendingFilesUseCase @Inject constructor(private val repo: FileRepository) {
    operator fun invoke(): Flow<List<FileItem>> = repo.observePendingFiles()
}

class SortedFoldersUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(): List<FolderSummary> = repo.sortedFolders()
}

class ListFolderUseCase @Inject constructor(private val repo: OrganizationRepository) {
    suspend operator fun invoke(relativeDir: String): List<FolderEntry> = repo.listFolder(relativeDir)
}

/**
 * Au démarrage : installe les dossiers de la gamme manquants (nouvelle version incluse) et aligne
 * la destination proposée des fichiers déjà indexés sur la configuration actuelle.
 */
class InitializeOrganizationUseCase @Inject constructor(
    private val repo: OrganizationRepository,
    private val trashRepository: com.sortquest.app.domain.repository.TrashRepository,
) {
    suspend operator fun invoke() {
        repo.ensureCatalog()
        repo.refreshSuggestions()
        trashRepository.adoptOrphans() // corbeille retrouvée après une réinstallation
    }
}
