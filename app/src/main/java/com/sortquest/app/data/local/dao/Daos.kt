package com.sortquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.sortquest.app.data.local.entity.AutoRuleEntity
import com.sortquest.app.data.local.entity.FileEntity
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.local.entity.ThematicFolderEntity
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.local.entity.UserProgressEntity
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.FileStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    /** IGNORE : un fichier déjà indexé (même chemin) garde son statut. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(files: List<FileEntity>): List<Long>

    /** File de tri : doublons et gros fichiers d'abord (gain d'espace rapide = motivation). */
    @Query(
        """
        SELECT * FROM files
        WHERE status = 'PENDING' OR (status = 'SKIPPED' AND skippedUntil < :now)
        ORDER BY isDuplicate DESC, sizeBytes DESC
        LIMIT :limit
        """
    )
    fun observeQueue(now: Long, limit: Int): Flow<List<FileEntity>>

    /** Vue Organiser : TOUS les fichiers à trier, groupables par destination. */
    @Query("SELECT * FROM files WHERE status IN ('PENDING', 'SKIPPED') ORDER BY suggestedTargetDir, name COLLATE NOCASE")
    fun observePendingFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE status IN ('PENDING', 'SKIPPED')")
    suspend fun pendingFiles(): List<FileEntity>

    @Query(
        """
        UPDATE files SET suggestedTargetDir = :dir, suggestedLabel = :label, suggestedIcon = :icon,
            category = :category, matchedKeyword = :keyword
        WHERE id = :id
        """
    )
    suspend fun updateSuggestion(id: Long, dir: String, label: String, icon: String, category: FileCategory, keyword: String?)

    @Query("SELECT COUNT(*) FROM files WHERE status IN ('PENDING', 'SKIPPED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE status IN ('PENDING', 'SKIPPED')")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM files WHERE status IN ('SORTED', 'TRASHED', 'VAULTED')")
    suspend fun sortedCount(): Int

    @Query("SELECT * FROM files WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): FileEntity?

    /** OR REPLACE : écrase une éventuelle ligne obsolète qui occuperait déjà le nouveau chemin. */
    @Query("UPDATE OR REPLACE files SET path = :path, status = :status, skippedUntil = :skippedUntil WHERE id = :id")
    suspend fun updateState(id: Long, path: String, status: FileStatus, skippedUntil: Long)

    @Query("SELECT id, path FROM files WHERE status IN ('PENDING', 'SKIPPED')")
    suspend fun pendingPaths(): List<IdPath>

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ------------------------------------------------ Doublons et fichiers lourds
    // "Actifs" = encore sur le disque à un emplacement utilisateur (pas dans la corbeille).

    @Query(
        """
        SELECT sizeBytes FROM files
        WHERE status IN ('PENDING', 'SKIPPED', 'SORTED', 'VAULTED') AND sizeBytes > 0 AND sizeBytes <= :maxSize
        GROUP BY sizeBytes HAVING COUNT(*) > 1
        """
    )
    suspend fun sharedSizes(maxSize: Long): List<Long>

    @Query("SELECT * FROM files WHERE status IN ('PENDING', 'SKIPPED', 'SORTED', 'VAULTED') AND sizeBytes = :size")
    suspend fun activeBySize(size: Long): List<FileEntity>

    @Query("UPDATE files SET partialHash = :partial, contentHash = :full WHERE id = :id")
    suspend fun setHashes(id: Long, partial: String?, full: String?)

    @Query("UPDATE files SET isDuplicate = 0 WHERE isDuplicate = 1")
    suspend fun clearDuplicateMarks()

    @Query("UPDATE files SET isDuplicate = 1 WHERE id IN (:ids)")
    suspend fun markDuplicates(ids: List<Long>)

    @Query(
        """
        SELECT * FROM files
        WHERE status IN ('PENDING', 'SKIPPED', 'SORTED', 'VAULTED') AND contentHash IN (
            SELECT contentHash FROM files
            WHERE contentHash IS NOT NULL AND status IN ('PENDING', 'SKIPPED', 'SORTED', 'VAULTED')
            GROUP BY contentHash HAVING COUNT(*) > 1
        )
        ORDER BY sizeBytes DESC, contentHash, lastModified ASC
        """
    )
    fun observeDuplicateFiles(): Flow<List<FileEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE status IN ('PENDING', 'SKIPPED', 'SORTED', 'VAULTED') AND sizeBytes >= :minSize
        ORDER BY sizeBytes DESC LIMIT :limit
        """
    )
    fun observeLargest(minSize: Long, limit: Int): Flow<List<FileEntity>>
}

data class IdPath(val id: Long, val path: String)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: SortHistoryEntity): Long

    @Query("UPDATE sort_history SET xpGained = :xp WHERE id = :id")
    suspend fun setXp(id: Long, xp: Int)

    /** Dernière action annulable (LIFO), dans la fenêtre de 7 jours de la corbeille. */
    @Query("SELECT * FROM sort_history WHERE undone = 0 AND timestamp > :minTimestamp ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun lastUndoable(minTimestamp: Long): SortHistoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM sort_history WHERE undone = 0 AND timestamp > :minTimestamp)")
    fun observeHasUndoable(minTimestamp: Long): Flow<Boolean>

    @Query("SELECT * FROM sort_history WHERE id = :id AND undone = 0")
    suspend fun undoableById(id: Long): SortHistoryEntity?

    /** Ligne qui a envoyé ce fichier à la corbeille (restauration depuis l'écran Corbeille). */
    @Query("SELECT * FROM sort_history WHERE toPath = :path AND undone = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun undoableByDestination(path: String): SortHistoryEntity?

    /** Ordre inverse : on défait du plus récent au plus ancien. */
    @Query("SELECT * FROM sort_history WHERE batchId = :batchId AND undone = 0 ORDER BY timestamp DESC, id DESC")
    suspend fun byBatch(batchId: String): List<SortHistoryEntity>

    @Query("UPDATE sort_history SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)

    /** Après purge définitive d'un fichier, plus d'annulation possible. */
    @Query("UPDATE sort_history SET undone = 1 WHERE toPath = :path")
    suspend fun invalidateByDestination(path: String)

    @Query("SELECT * FROM sort_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SortHistoryEntity>>
}

@Dao
interface TrashDao {
    @Insert
    suspend fun insert(entry: TrashEntity): Long

    @Query("SELECT * FROM trash ORDER BY expiresAt ASC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash")
    suspend fun all(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE id = :id")
    suspend fun getById(id: Long): TrashEntity?

    @Query("SELECT * FROM trash WHERE expiresAt <= :now")
    suspend fun expired(now: Long): List<TrashEntity>

    @Query("DELETE FROM trash WHERE trashPath = :trashPath")
    suspend fun deleteByTrashPath(trashPath: String)

    @Delete
    suspend fun delete(entry: TrashEntity)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM trash")
    fun observeTotalSize(): Flow<Long>
}

@Dao
interface AutoRuleDao {
    @Query("SELECT * FROM auto_rules ORDER BY priority DESC, createdAt ASC")
    fun observeAll(): Flow<List<AutoRuleEntity>>

    @Query("SELECT * FROM auto_rules WHERE enabled = 1 ORDER BY priority DESC, createdAt ASC")
    suspend fun enabledRules(): List<AutoRuleEntity>

    @Insert
    suspend fun insert(rule: AutoRuleEntity): Long

    @Update
    suspend fun update(rule: AutoRuleEntity)

    @Query("UPDATE auto_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE auto_rules SET lastRunAt = :timestamp, filesMoved = filesMoved + :count WHERE id = :id")
    suspend fun recordRun(id: Long, timestamp: Long, count: Int)

    @Query("DELETE FROM auto_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 0")
    fun observe(): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress WHERE id = 0")
    suspend fun get(): UserProgressEntity?

    @Upsert
    suspend fun upsert(progress: UserProgressEntity)
}

@Dao
interface ThematicFolderDao {
    @Query("SELECT * FROM thematic_folders ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ThematicFolderEntity>>

    @Query("SELECT * FROM thematic_folders ORDER BY sortOrder, name")
    suspend fun all(): List<ThematicFolderEntity>

    /** IGNORE : un dossier déjà présent garde le choix (activé/désactivé) de l'utilisateur. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(folders: List<ThematicFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: ThematicFolderEntity)

    @Query("UPDATE thematic_folders SET enabled = :enabled WHERE id = :id AND locked = 0")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM thematic_folders WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("SELECT MAX(sortOrder) FROM thematic_folders WHERE locked = 0")
    suspend fun maxSortOrder(): Int?
}
