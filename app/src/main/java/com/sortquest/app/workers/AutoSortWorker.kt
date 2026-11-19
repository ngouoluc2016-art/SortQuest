package com.sortquest.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortquest.app.R
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.usecase.RunAutoPilotUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Applique les règles Auto-Pilot au dossier Download.
 * Deux modes : à la demande ("Exécuter maintenant") et périodique (toutes les 12 h).
 * Progression publiée via setProgress -> barre de progression de l'écran Règles.
 */
@HiltWorker
class AutoSortWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runAutoPilot: RunAutoPilotUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!StoragePermissionHelper.hasAllFilesAccess(applicationContext)) {
            return Result.failure(workDataOf(KEY_ERROR to ERROR_PERMISSION))
        }
        return try {
            var moved = 0
            var bytes = 0L
            var errors = 0
            var batchId = ""
            runAutoPilot().collect { p ->
                moved = p.moved; bytes = p.bytesMoved; errors = p.errors; batchId = p.batchId
                setProgress(
                    workDataOf(
                        KEY_PROCESSED to p.processed,
                        KEY_TOTAL to p.total,
                        KEY_MOVED to p.moved,
                        KEY_CURRENT to p.currentFile,
                    )
                )
            }
            Result.success(
                workDataOf(KEY_MOVED to moved, KEY_BYTES to bytes, KEY_ERRORS to errors, KEY_BATCH to batchId)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Chaque fichier est déjà traité de façon atomique : relancer ne crée pas de doublon d'action
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Erreur inconnue")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        WorkNotifications.foregroundInfo(
            applicationContext, NOTIF_ID, applicationContext.getString(R.string.notif_autopilot_title),
        )

    companion object {
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_MOVED = "moved"
        const val KEY_BYTES = "bytes"
        const val KEY_ERRORS = "errors"
        const val KEY_BATCH = "batchId"
        const val KEY_CURRENT = "current"
        const val KEY_ERROR = "error"
        const val ERROR_PERMISSION = "permission"

        private const val UNIQUE_NOW = "sortquest_autopilot_now"
        private const val UNIQUE_PERIODIC = "sortquest_autopilot_periodic"
        private const val NOTIF_ID = 43
        private const val MAX_RETRIES = 2
        private const val PERIOD_HOURS = 12L

        /** Exécution immédiate. REPLACE : l'id renvoyé est toujours celui de la requête active. */
        fun runNow(workManager: WorkManager): UUID {
            val request = OneTimeWorkRequestBuilder<AutoSortWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }

        fun setPeriodic(workManager: WorkManager, enabled: Boolean) {
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoSortWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** L'état "Auto-Pilot automatique" EST la présence du travail périodique : pas de doublon d'état. */
        fun observePeriodicEnabled(workManager: WorkManager): Flow<Boolean> =
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_PERIODIC)
                .map { infos -> infos.any { !it.state.isFinished } }
                .distinctUntilChanged()

        /** Exécution immédiate en cours (ou en attente), quelle que soit l'origine du lancement. */
        fun observeNowRunning(workManager: WorkManager): Flow<WorkInfo?> =
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NOW)
                .map { infos -> infos.firstOrNull { !it.state.isFinished } }

        fun observeRun(workManager: WorkManager, id: UUID): Flow<WorkInfo?> =
            workManager.getWorkInfoByIdFlow(id)
    }
}
