package com.sortquest.app.domain.usecase

import com.sortquest.app.domain.gamification.GameEvent
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.DuplicateScanProgress
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.KeepPreference
import com.sortquest.app.domain.model.PurgeResult
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.domain.model.TrashItem
import com.sortquest.app.domain.repository.FileHasher
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.ProgressRepository
import com.sortquest.app.domain.repository.TrashRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * Détection de doublons en 3 passes, de la moins coûteuse à la plus coûteuse :
 *
 *  1. Même taille exacte (requête SQL, aucune lecture disque) : élimine l'immense majorité des fichiers.
 *  2. Empreinte partielle (taille + 64 premiers Ko + 64 derniers Ko) : 128 Ko lus au maximum par fichier.
 *  3. SHA-256 complet, UNIQUEMENT pour les fichiers encore en concurrence après l'étape 2.
 *     Pour un fichier <= 128 Ko, l'étape 2 a déjà lu tout le contenu : l'étape 3 est sautée.
 *
 * Ressources : un fichier à la fois (1 cœur max), lecture en flux par blocs de 64 Ko
 * (mémoire constante), empreintes mises en cache en base, annulable entre deux fichiers.
 */
class DuplicateScannerUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val hasher: FileHasher,
) {
    operator fun invoke(): Flow<DuplicateScanProgress> = flow {
        val sizeGroups = fileRepository.filesGroupedBySize(MAX_HASHED_BYTES) // étape 1
        val copyIds = mutableListOf<Long>()
        var reclaimable = 0L
        emit(DuplicateScanProgress(0, sizeGroups.size, 0, 0))

        sizeGroups.forEachIndexed { index, sameSize ->
            val size = sameSize.first().sizeBytes

            // Étape 2 : empreinte partielle
            val byPartial = sameSize.mapNotNull { file ->
                val partial = file.partialHash ?: safeHash { hasher.partialHash(file.path) }
                    ?.also { fileRepository.saveHashes(file.id, it, file.contentHash) }
                partial?.let { file.copy(partialHash = it) }
            }.groupBy { it.partialHash!! }.values.filter { it.size > 1 }

            // Étape 3 : SHA-256 complet, seulement sur les survivants
            for (candidates in byPartial) {
                val byFull = if (size <= FileHasher.FULL_COVERAGE_BYTES) {
                    candidates.map { f ->
                        val h = "p:${f.partialHash}" // l'empreinte partielle couvre tout le fichier
                        if (f.contentHash != h) fileRepository.saveHashes(f.id, f.partialHash, h)
                        f.copy(contentHash = h)
                    }
                } else {
                    candidates.mapNotNull { f ->
                        currentCoroutineContext().ensureActive()
                        val full = f.contentHash ?: safeHash { hasher.fullHash(f.path) }
                            ?.also { fileRepository.saveHashes(f.id, f.partialHash, it) }
                        full?.let { f.copy(contentHash = it) }
                    }
                }.groupBy { it.contentHash!! }.values.filter { it.size > 1 }

                for (identical in byFull) {
                    val copies = identical.sortedWith(KeepPreference).drop(1) // exemplaire rangé ou plus ancien = gardé
                    copyIds += copies.map { it.id }
                    reclaimable += size * copies.size
                }
            }

            currentCoroutineContext().ensureActive()
            yield() // laisse respirer les autres coroutines (UI, swipes)
            emit(DuplicateScanProgress(index + 1, sizeGroups.size, copyIds.size, reclaimable))
        }

        fileRepository.replaceDuplicateMarks(copyIds)
        emit(DuplicateScanProgress(sizeGroups.size, sizeGroups.size, copyIds.size, reclaimable, done = true))
    }

    /** Fichier illisible ou supprimé pendant le scan : ignoré, le scan continue. */
    private suspend fun safeHash(block: suspend () -> String): String? =
        try { block() } catch (e: java.io.IOException) { null } catch (e: SecurityException) { null }

    companion object {
        /** Au-delà, le hash complet coûterait trop cher sur mobile (fichiers > 2 Go ignorés). */
        const val MAX_HASHED_BYTES = 2L * 1024 * 1024 * 1024
    }
}

class ObserveDuplicateGroupsUseCase @Inject constructor(private val repo: FileRepository) {
    operator fun invoke(): Flow<List<DuplicateGroup>> = repo.observeDuplicateGroups()
}

/** Top 20 des fichiers de plus de 50 Mo. */
class ObserveLargestFilesUseCase @Inject constructor(private val repo: FileRepository) {
    operator fun invoke(minSizeBytes: Long = MIN_SIZE, limit: Int = LIMIT): Flow<List<FileItem>> =
        repo.observeLargestFiles(minSizeBytes, limit)

    companion object {
        const val MIN_SIZE = 50L * 1024 * 1024
        const val LIMIT = 20
    }
}

/** Envoie des fichiers à la corbeille en passant par le flux normal (historique, XP, annulation). */
data class TrashBatchResult(val trashed: Int, val failed: Int, val bytes: Long)

class TrashFilesUseCase @Inject constructor(private val sortFile: SortFileUseCase) {
    suspend operator fun invoke(files: List<FileItem>): TrashBatchResult {
        var ok = 0
        var ko = 0
        var bytes = 0L
        for (file in files) {
            sortFile(file, SwipeDirection.LEFT, combo = 0, inExpressSession = false)
                .onSuccess { ok++; bytes += file.sizeBytes }
                .onFailure { ko++ }
        }
        return TrashBatchResult(ok, ko, bytes)
    }
}

class ObserveTrashUseCase @Inject constructor(private val repo: TrashRepository) {
    operator fun invoke(): Flow<List<TrashItem>> = repo.observeTrash()
}

/** Restaure un fichier et retire l'XP gagnée par sa suppression. */
class RestoreFromTrashUseCase @Inject constructor(
    private val trashRepository: TrashRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(item: TrashItem): Result<Unit> =
        trashRepository.restore(item.id).mapCatching { record ->
            if (record != null) {
                progressRepository.apply(
                    GameEvent.ActionReverted(record.action, record.source, record.sizeBytes, record.wasDuplicate, record.xpGained)
                )
            }
        }
}

class DeleteForeverUseCase @Inject constructor(private val repo: TrashRepository) {
    suspend operator fun invoke(item: TrashItem): Result<Unit> = repo.deleteForever(item.id)
}

class EmptyTrashUseCase @Inject constructor(private val repo: TrashRepository) {
    suspend operator fun invoke(): PurgeResult = repo.emptyTrash()
}

class PurgeExpiredTrashUseCase @Inject constructor(private val repo: TrashRepository) {
    suspend operator fun invoke(): PurgeResult = repo.purgeExpired()
}
