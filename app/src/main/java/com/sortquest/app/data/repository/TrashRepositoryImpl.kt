package com.sortquest.app.data.repository

import androidx.room.withTransaction
import com.sortquest.app.data.file.MediaStoreNotifier
import com.sortquest.app.data.file.SafeFileOperations
import com.sortquest.app.data.file.StorageLock
import com.sortquest.app.data.file.StoragePaths
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.local.entity.TrashEntity
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.model.FileStatus
import com.sortquest.app.domain.model.PurgeResult
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.TrashItem
import com.sortquest.app.domain.repository.TrashRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ops: SafeFileOperations,
    private val lock: StorageLock,
    private val undoEngine: UndoEngine,
    private val mediaStore: MediaStoreNotifier,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TrashRepository {

    private val trashDao = db.trashDao()
    private val fileDao = db.fileDao()
    private val historyDao = db.historyDao()

    override fun observeTrash(): Flow<List<TrashItem>> =
        trashDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun restore(trashId: Long): Result<SortRecord?> = withContext(io) {
        lock.mutex.withLock {
            runCatching<SortRecord?> {
                val entry = trashDao.getById(trashId) ?: return@runCatching null
                // Cas normal : on annule la ligne d'historique qui a envoyé le fichier ici
                val history = historyDao.undoableByDestination(entry.trashPath)
                if (history != null) return@runCatching undoEngine.undoLocked(history)

                // Repli (historique absent ou expiré) : restauration directe, sans XP à retirer
                val current = File(entry.trashPath)
                if (!current.isFile) {
                    trashDao.delete(entry)
                    throw FileNotFoundException("${entry.fileName} n'est plus dans la corbeille")
                }
                val parent = File(entry.originalPath).parentFile ?: throw IOException("Dossier d'origine inconnu")
                val restored = ops.move(current, parent)
                db.withTransaction {
                    fileDao.updateState(entry.fileId, restored.absolutePath, FileStatus.PENDING, 0L)
                    trashDao.delete(entry)
                }
                mediaStore.notifyMoved(current, restored)
                null
            }
        }
    }

    override suspend fun deleteForever(trashId: Long): Result<Unit> = withContext(io) {
        lock.mutex.withLock {
            runCatching {
                val entry = trashDao.getById(trashId) ?: return@runCatching
                if (!deleteLocked(entry)) throw IOException("Suppression impossible : ${entry.fileName}")
            }
        }
    }

    override suspend fun emptyTrash(): PurgeResult = withContext(io) {
        lock.mutex.withLock { purgeLocked(trashDao.all()) }
    }

    override suspend fun purgeExpired(): PurgeResult = withContext(io) {
        lock.mutex.withLock { purgeLocked(trashDao.expired(System.currentTimeMillis())) }
    }

    private suspend fun purgeLocked(entries: List<TrashEntity>): PurgeResult {
        var count = 0
        var bytes = 0L
        for (entry in entries) {
            if (deleteLocked(entry)) {
                count++
                bytes += entry.sizeBytes
            }
        }
        return PurgeResult(count, bytes)
    }

    /**
     * Suppression définitive d'UN élément.
     * Sécurité : on ne supprime JAMAIS un fichier situé hors du dossier corbeille,
     * même si la base contenait un chemin inattendu.
     */
    private suspend fun deleteLocked(entry: TrashEntity): Boolean {
        val f = File(entry.trashPath)
        val trashRoot = StoragePaths.trashDir.canonicalPath + File.separator
        if (!f.canonicalPath.startsWith(trashRoot)) {
            trashDao.delete(entry) // entrée incohérente : on l'oublie sans toucher au disque
            return false
        }
        if (f.exists() && !f.delete()) return false
        db.withTransaction {
            trashDao.delete(entry)
            fileDao.deleteById(entry.fileId)
            historyDao.invalidateByDestination(entry.trashPath) // plus d'annulation possible
        }
        return true
    }
}
