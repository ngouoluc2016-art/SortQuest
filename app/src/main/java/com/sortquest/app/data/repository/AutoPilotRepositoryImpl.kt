package com.sortquest.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.data.file.MediaStoreNotifier
import com.sortquest.app.data.file.SafeFileOperations
import com.sortquest.app.data.file.StorageLock
import com.sortquest.app.data.file.StoragePaths
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.local.entity.SortHistoryEntity
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.gamification.GamificationManager
import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.AutoPilotProgress
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.FileStatus
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.organization.ProtectedFilesPolicy
import com.sortquest.app.domain.repository.AutoPilotRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moteur Auto-Pilot : applique les règles actives aux fichiers posés à la racine de Download.
 *
 * Choix de conception :
 *  - Racine de Download uniquement (pas les sous-dossiers, souvent créés par d'autres applis).
 *  - Première règle qui correspond gagne (ordre : priorité puis ancienneté).
 *  - Un fichier = une transaction : une erreur n'interrompt pas l'exécution.
 *  - Verrou relâché entre deux fichiers : l'utilisateur peut swiper pendant l'exécution.
 *  - Chaque déplacement est journalisé avec un batchId commun -> "Annuler cette exécution".
 */
@Singleton
class AutoPilotRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val ops: SafeFileOperations,
    private val lock: StorageLock,
    private val mediaStore: MediaStoreNotifier,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AutoPilotRepository {

    private val ruleDao = db.autoRuleDao()
    private val fileDao = db.fileDao()
    private val historyDao = db.historyDao()

    override fun run(batchId: String): Flow<AutoPilotProgress> = flow {
        if (!StoragePermissionHelper.hasAllFilesAccess(context)) {
            throw SecurityException("Accès au stockage non accordé")
        }
        val rules = ruleDao.enabledRules().map { it.toDomain() }
        val candidates = if (rules.isEmpty()) emptyList() else listCandidates()

        var progress = AutoPilotProgress(batchId, processed = 0, total = candidates.size, moved = 0, bytesMoved = 0)
        emit(progress)

        val movedPerRule = HashMap<Long, Int>()
        for (file in candidates) {
            currentCoroutineContext().ensureActive() // annulation propre entre deux fichiers
            val rule = firstMatchingRule(file, rules)
            var movedBytes = 0L
            var error = false

            if (rule != null) {
                runCatching { moveWithRule(file, rule, batchId) }
                    .onSuccess { bytes ->
                        if (bytes != null) {
                            movedBytes = bytes
                            movedPerRule.merge(rule.id, 1, Int::plus)
                        }
                    }
                    .onFailure { error = true }
            }

            progress = progress.copy(
                processed = progress.processed + 1,
                moved = movedPerRule.values.sum(),
                bytesMoved = progress.bytesMoved + movedBytes,
                errors = progress.errors + if (error) 1 else 0,
                currentFile = file.name,
            )
            emit(progress)
        }

        // Statistiques par règle (affichées dans l'écran Règles)
        val now = System.currentTimeMillis()
        movedPerRule.forEach { (ruleId, count) -> ruleDao.recordRun(ruleId, now, count) }

        emit(progress.copy(currentFile = null, done = true))
    }.flowOn(io)

    private fun listCandidates(): List<File> {
        val now = System.currentTimeMillis()
        return StoragePaths.downloadsDir.listFiles()
            ?.filter {
                it.isFile && !isPartialDownload(it) &&
                    // Même politique que le scan : caché, technique, en cours d'écriture -> ignoré
                    !ProtectedFilesPolicy.isProtected(StoragePaths.relative(it), it.length(), now - it.lastModified())
            }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }

    /** Téléchargements en cours de Chrome / Firefox / Android : à ne surtout pas déplacer. */
    private fun isPartialDownload(f: File): Boolean {
        val ext = f.extension.lowercase()
        return ext in setOf("crdownload", "part", "tmp", "download") || f.name.startsWith(".pending-")
    }

    private fun firstMatchingRule(file: File, rules: List<SortRule>): SortRule? {
        val normalized = FileNameNormalizer.normalize(file.nameWithoutExtension)
        val ext = file.extension.lowercase()
        val size = file.length()
        return rules.firstOrNull { it.matches(normalized, ext, size) }
    }

    /** @return taille déplacée en octets, ou null si rien n'a été déplacé (déjà en place, disparu). */
    private suspend fun moveWithRule(source: File, rule: SortRule, batchId: String): Long? =
        lock.mutex.withLock {
            if (!source.isFile) return@withLock null // supprimé entre-temps
            val targetDir = StoragePaths.resolve(rule.targetDir)
            StoragePaths.requireSafe(targetDir)
            if (source.parentFile?.canonicalPath == targetDir.canonicalPath) return@withLock null

            val size = source.length()
            val moved = ops.move(source, targetDir) // conflit de nom -> "fichier (1).pdf"
            try {
                db.withTransaction {
                    val indexed = fileDao.getByPath(source.absolutePath)
                    indexed?.let { fileDao.updateState(it.id, moved.absolutePath, FileStatus.SORTED, 0L) }
                    historyDao.insert(
                        SortHistoryEntity(
                            fileId = indexed?.id,
                            fileName = source.name,
                            action = ActionType.MOVE,
                            source = ActionSource.AUTO_PILOT,
                            ruleId = rule.id,
                            batchId = batchId,
                            fromPath = source.absolutePath,
                            toPath = moved.absolutePath,
                            sizeBytes = size,
                            wasDuplicate = indexed?.isDuplicate ?: false,
                            xpGained = GamificationManager.AUTO_PILOT_XP_PER_FILE,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                }
            } catch (t: Throwable) {
                runCatching { ops.move(moved, source.parentFile!!) } // retour arrière disque
                throw t
            }
            mediaStore.notifyMoved(source, moved)
            size
        }
}
