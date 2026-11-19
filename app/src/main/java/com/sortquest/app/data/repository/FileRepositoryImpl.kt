package com.sortquest.app.data.repository

import android.content.Context
import android.os.StatFs
import androidx.room.withTransaction
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.data.file.FileCategorizer
import com.sortquest.app.data.file.MediaStoreNotifier
import com.sortquest.app.data.file.SafeFileOperations
import com.sortquest.app.data.file.StorageLock
import com.sortquest.app.data.file.StoragePaths
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.local.entity.FileEntity
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.data.mapper.toHistoryEntry
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileStatus
import com.sortquest.app.domain.model.HistoryEntry
import com.sortquest.app.domain.model.KeepPreference
import com.sortquest.app.domain.model.ScanPhase
import com.sortquest.app.domain.model.ScanProgress
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val categorizer: FileCategorizer,
    private val ops: SafeFileOperations,
    private val lock: StorageLock,
    private val undoEngine: UndoEngine,
    private val mediaStore: MediaStoreNotifier,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FileRepository {

    private val fileDao = db.fileDao()
    private val historyDao = db.historyDao()
    private val trashDao = db.trashDao()
    private val ruleDao = db.autoRuleDao()

    // ------------------------------------------------------------------ Indexation

    override fun indexStorage(): Flow<ScanProgress> = flow {
        if (!StoragePermissionHelper.hasAllFilesAccess(context)) {
            throw SecurityException("Accès au stockage non accordé")
        }
        val rules = ruleDao.enabledRules().map { it.toDomain() }
        val now = System.currentTimeMillis()
        val batch = ArrayList<FileEntity>(BATCH_SIZE)
        var scanned = 0

        for (root in StoragePaths.scanRoots()) {
            val walker = root.walkTopDown()
                .maxDepth(MAX_DEPTH)
                .onEnter { dir -> dir == root || !StoragePaths.shouldSkipDir(dir) }
                .onFail { _, _ -> /* dossier illisible : ignoré, le scan continue */ }

            for (file in walker) {
                currentCoroutineContext().ensureActive()
                if (!file.isFile || file.isHidden || file.length() == 0L) continue

                val result = categorizer.categorize(file, rules)
                batch += FileEntity(
                    path = file.absolutePath,
                    name = file.name,
                    extension = file.extension.lowercase(),
                    mimeType = result.mimeType,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    kind = result.kind,
                    category = result.category,
                    suggestedTargetDir = result.targetDir,
                    matchedKeyword = result.matchedKeyword,
                    scannedAt = now,
                )
                scanned++
                if (batch.size >= BATCH_SIZE) {
                    fileDao.insertAll(batch.toList())
                    batch.clear()
                }
                if (scanned % PROGRESS_EVERY == 0) {
                    emit(ScanProgress(ScanPhase.WALKING, scanned, file.parent.orEmpty()))
                }
            }
        }
        if (batch.isNotEmpty()) fileDao.insertAll(batch.toList())
        removeVanishedEntries()
        emit(ScanProgress(ScanPhase.WALKING, scanned))
    }.flowOn(io)

    private suspend fun removeVanishedEntries() {
        val gone = fileDao.pendingPaths().filterNot { File(it.path).exists() }.map { it.id }
        gone.chunked(SQL_CHUNK).forEach { fileDao.deleteByIds(it) }
    }

    // ------------------------------------------------------------------ Lecture

    override fun observeQueue(limit: Int): Flow<List<FileItem>> =
        fileDao.observeQueue(System.currentTimeMillis(), limit)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observePendingCount(): Flow<Int> = fileDao.observePendingCount()

    override fun observeCanUndo(): Flow<Boolean> =
        historyDao.observeHasUndoable(undoWindowStart()).distinctUntilChanged()

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(limit).map { list -> list.map { it.toHistoryEntry() } }

    override suspend fun storageStats(): StorageStats = withContext(io) {
        val stat = StatFs(StoragePaths.root.path)
        StorageStats(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            pendingCount = fileDao.pendingCount(),
            sortedCount = fileDao.sortedCount(),
        )
    }

    // ------------------------------------------------------------------ Actions

    override suspend fun applyAction(
        item: FileItem,
        action: ActionType,
        customTargetDir: String?,
    ): Result<SortRecord> = withContext(io) {
        lock.mutex.withLock {
            runCatching {
                val source = File(item.path)
                if (action != ActionType.SKIP && !source.isFile) {
                    fileDao.deleteById(item.id)
                    throw FileNotFoundException("Fichier introuvable : ${item.name}")
                }
                val now = System.currentTimeMillis()

                // 1. Disque
                val moved: File? = when (action) {
                    ActionType.MOVE ->
                        ops.move(source, StoragePaths.resolve(customTargetDir ?: item.suggestedTargetDir))
                    ActionType.VAULT -> ops.move(source, StoragePaths.vaultDir)
                    ActionType.TRASH -> {
                        ops.ensureNoMedia(StoragePaths.trashDir)
                        ops.move(source, StoragePaths.trashDir)
                    }
                    ActionType.SKIP -> null
                }

                // 2. Base (transaction). En cas d'échec : retour arrière disque.
                try {
                    val entry = SortHistoryEntity(
                        fileId = item.id,
                        fileName = item.name,
                        action = action,
                        source = ActionSource.MANUAL,
                        fromPath = source.absolutePath,
                        toPath = moved?.absolutePath,
                        sizeBytes = item.sizeBytes,
                        wasDuplicate = item.isDuplicate,
                        timestamp = now,
                    )
                    val historyId = db.withTransaction {
                        val (status, skippedUntil) = when (action) {
                            ActionType.MOVE -> FileStatus.SORTED to 0L
                            ActionType.VAULT -> FileStatus.VAULTED to 0L
                            ActionType.TRASH -> FileStatus.TRASHED to 0L
                            ActionType.SKIP -> FileStatus.SKIPPED to now + SKIP_DELAY_MS
                        }
                        fileDao.updateState(item.id, moved?.absolutePath ?: item.path, status, skippedUntil)
                        if (action == ActionType.TRASH && moved != null) {
                            trashDao.insert(
                                TrashEntity(
                                    fileId = item.id,
                                    fileName = item.name,
                                    originalPath = source.absolutePath,
                                    trashPath = moved.absolutePath,
                                    sizeBytes = item.sizeBytes,
                                    trashedAt = now,
                                    expiresAt = now + TRASH_RETENTION_MS, // timestamp + 7 jours
                                )
                            )
                        }
                        historyDao.insert(entry)
                    }
                    moved?.let { mediaStore.notifyMoved(source, it) }
                    entry.copy(id = historyId).toDomain()
                } catch (dbError: Throwable) {
                    if (moved != null && moved != source) {
                        runCatching { ops.move(moved, source.parentFile!!) }
                    }
                    throw dbError
                }
            }
        }
    }

    override suspend fun recordXp(historyId: Long, xp: Int) = withContext(io) {
        historyDao.setXp(historyId, xp)
    }

    // ------------------------------------------------------------------ Annulation

    override suspend fun undoLast(): Result<SortRecord?> = withContext(io) {
        lock.mutex.withLock {
            runCatching<SortRecord?> {
                val entry = historyDao.lastUndoable(undoWindowStart()) ?: return@runCatching null
                undoEngine.undoLocked(entry)
            }
        }
    }

    override suspend fun undo(historyId: Long): Result<SortRecord?> = withContext(io) {
        lock.mutex.withLock {
            runCatching<SortRecord?> {
                val entry = historyDao.undoableById(historyId) ?: return@runCatching null
                undoEngine.undoLocked(entry)
            }
        }
    }

    override suspend fun undoBatch(batchId: String): Result<List<SortRecord>> = withContext(io) {
        lock.mutex.withLock {
            runCatching {
                val undone = mutableListOf<SortRecord>()
                var failures = 0
                for (entry in historyDao.byBatch(batchId)) {
                    runCatching { undoEngine.undoLocked(entry) }
                        .onSuccess { undone += it }
                        .onFailure { failures++ }
                }
                if (undone.isEmpty() && failures > 0) throw IOException("Aucun fichier n'a pu être restauré")
                undone
            }
        }
    }

    // ------------------------------------------------------------------ Nettoyage

    override suspend fun filesGroupedBySize(maxSizeBytes: Long): List<List<FileItem>> = withContext(io) {
        fileDao.sharedSizes(maxSizeBytes).mapNotNull { size ->
            val group = fileDao.activeBySize(size).mapNotNull { entity ->
                val f = File(entity.path)
                when {
                    !f.isFile -> null // disparu depuis l'indexation
                    // Fichier modifié depuis le dernier calcul : cache d'empreintes invalide
                    f.lastModified() != entity.lastModified || f.length() != entity.sizeBytes ->
                        entity.toDomain().copy(partialHash = null, contentHash = null)
                    else -> entity.toDomain()
                }
            }
            group.takeIf { it.size > 1 }
        }
    }

    override suspend fun saveHashes(fileId: Long, partialHash: String?, contentHash: String?) = withContext(io) {
        fileDao.setHashes(fileId, partialHash, contentHash)
    }

    override suspend fun replaceDuplicateMarks(copyIds: List<Long>) = withContext(io) {
        db.withTransaction {
            fileDao.clearDuplicateMarks()
            copyIds.chunked(SQL_CHUNK).forEach { fileDao.markDuplicates(it) }
        }
    }

    override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> =
        fileDao.observeDuplicateFiles().map { files ->
            files.groupBy { it.contentHash!! }
                .filterValues { it.size > 1 }
                .map { (hash, group) ->
                    DuplicateGroup(
                        hash = hash,
                        sizeBytes = group.first().sizeBytes,
                        files = group.map { it.toDomain() }.sortedWith(KeepPreference),
                    )
                }
                .sortedByDescending { it.reclaimableBytes }
        }.flowOn(io)

    override fun observeLargestFiles(minSizeBytes: Long, limit: Int): Flow<List<FileItem>> =
        fileDao.observeLargest(minSizeBytes, limit).map { list -> list.map { it.toDomain() } }

    private fun undoWindowStart(): Long = System.currentTimeMillis() - TRASH_RETENTION_MS

    companion object {
        private const val MAX_DEPTH = 8
        private const val BATCH_SIZE = 200
        private const val PROGRESS_EVERY = 50
        private const val SQL_CHUNK = 900
        val TRASH_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
        private val SKIP_DELAY_MS = TimeUnit.HOURS.toMillis(24)
    }
}
