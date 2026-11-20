package com.sortquest.app.domain.repository

import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.HistoryEntry
import com.sortquest.app.domain.model.ScanProgress
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.StorageStats
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    /** Parcourt le stockage et indexe les fichiers (sans les doublons : voir DuplicateScannerUseCase). */
    fun indexStorage(): Flow<ScanProgress>

    fun observeQueue(limit: Int): Flow<List<FileItem>>
    /** Tous les fichiers à trier (vue Organiser), triés par destination proposée puis par nom. */
    fun observePendingFiles(): Flow<List<FileItem>>
    fun observePendingCount(): Flow<Int>

    /** Exécute une action de tri et la journalise. Garantit : pas d'écrasement, cohérence disque/base. */
    suspend fun applyAction(
        item: FileItem,
        action: ActionType,
        customTargetDir: String? = null,
        /** Identifiant commun d'un rangement groupé : permet de tout annuler d'un coup. */
        batchId: String? = null,
    ): Result<SortRecord>

    suspend fun recordXp(historyId: Long, xp: Int)

    // ---------------------------------------------------------------- Annulation / historique
    fun observeCanUndo(): Flow<Boolean>
    fun observeHistory(limit: Int): Flow<List<HistoryEntry>>
    suspend fun undoLast(): Result<SortRecord?>
    suspend fun undo(historyId: Long): Result<SortRecord?>
    suspend fun undoBatch(batchId: String): Result<List<SortRecord>>

    // ---------------------------------------------------------------- Nettoyage
    /**
     * Étape 1 du scanner de doublons : groupes de fichiers partageant EXACTEMENT la même taille.
     * Les empreintes en cache sont remises à null si le fichier a été modifié depuis.
     */
    suspend fun filesGroupedBySize(maxSizeBytes: Long): List<List<FileItem>>
    suspend fun saveHashes(fileId: Long, partialHash: String?, contentHash: String?)
    /** Remplace les marques "doublon" par celles du dernier scan. */
    suspend fun replaceDuplicateMarks(copyIds: List<Long>)
    fun observeDuplicateGroups(): Flow<List<DuplicateGroup>>
    fun observeLargestFiles(minSizeBytes: Long, limit: Int): Flow<List<FileItem>>

    suspend fun storageStats(): StorageStats
}

/** Calcul d'empreintes : implémenté côté data (accès disque), utilisé par le domaine. */
interface FileHasher {
    /** Taille + 64 premiers Ko + 64 derniers Ko. Couvre tout le fichier si taille <= [FULL_COVERAGE_BYTES]. */
    suspend fun partialHash(path: String): String
    suspend fun fullHash(path: String): String

    companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val FULL_COVERAGE_BYTES = 2L * CHUNK_BYTES
    }
}
