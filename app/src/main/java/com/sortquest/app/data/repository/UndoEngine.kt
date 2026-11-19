package com.sortquest.app.data.repository

import androidx.room.withTransaction
import com.sortquest.app.data.file.MediaStoreNotifier
import com.sortquest.app.data.file.SafeFileOperations
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.FileStatus
import com.sortquest.app.domain.model.SortRecord
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logique d'annulation partagée par l'écran de tri, l'historique, l'Auto-Pilot et la corbeille.
 * L'appelant DOIT détenir StorageLock et être sur Dispatchers.IO.
 */
@Singleton
class UndoEngine @Inject constructor(
    private val db: AppDatabase,
    private val ops: SafeFileOperations,
    private val mediaStore: MediaStoreNotifier,
) {
    private val fileDao = db.fileDao()
    private val historyDao = db.historyDao()
    private val trashDao = db.trashDao()

    suspend fun undoLocked(entry: SortHistoryEntity): SortRecord {
        val original = File(entry.fromPath)
        var restoredPath = entry.fromPath

        if (entry.action != ActionType.SKIP) {
            val current = File(entry.toPath ?: throw IOException("Historique incomplet"))
            if (!current.isFile) {
                historyDao.markUndone(entry.id) // impossible à défaire : retiré de la pile
                throw FileNotFoundException("${entry.fileName} n'est plus disponible")
            }
            val parent = original.parentFile ?: throw IOException("Dossier d'origine inconnu")
            // Nom d'origine repris entre-temps -> "nom (1).ext", jamais d'écrasement
            val restored = ops.move(current, parent)
            restoredPath = restored.absolutePath
            mediaStore.notifyMoved(current, restored)
        }

        db.withTransaction {
            entry.fileId?.let { fileDao.updateState(it, restoredPath, FileStatus.PENDING, 0L) }
            if (entry.action == ActionType.TRASH) entry.toPath?.let { trashDao.deleteByTrashPath(it) }
            historyDao.markUndone(entry.id)
        }
        return entry.toDomain()
    }
}
