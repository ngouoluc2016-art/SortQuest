package com.sortquest.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortquest.app.R
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.usecase.ScanStorageUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sortquest.app.domain.model.ScanPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Analyse complète du stockage, hors du thread UI, résistante à la fermeture de l'appli. */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanStorage: ScanStorageUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!StoragePermissionHelper.hasAllFilesAccess(applicationContext)) {
            return Result.failure(workDataOf(KEY_ERROR to "permission"))
        }
        return try {
            var scanned = 0
            var duplicates = 0
            scanStorage().collect { progress ->
                scanned = progress.scannedFiles
                duplicates = progress.duplicatesFound
                setProgress(
                    workDataOf(
                        KEY_SCANNED to scanned,
                        KEY_PHASE to progress.phase.name,
                        KEY_GROUPS_CHECKED to progress.groupsChecked,
                        KEY_GROUPS_TOTAL to progress.groupsTotal,
                    )
                )
            }
            Result.success(workDataOf(KEY_SCANNED to scanned, KEY_DUPLICATES to duplicates))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "erreur")))
        }
    }

    /** Requis pour une requête "expedited" sur Android 11 et moins (service de premier plan). */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        WorkNotifications.foregroundInfo(
            applicationContext, NOTIF_ID, applicationContext.getString(R.string.notif_scan_title),
        )

    companion object {
        const val UNIQUE_NAME = "sortquest_scan"
        const val KEY_SCANNED = "scanned"
        const val KEY_DUPLICATES = "duplicates"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val KEY_GROUPS_CHECKED = "groupsChecked"
        const val KEY_GROUPS_TOTAL = "groupsTotal"
        private const val NOTIF_ID = 42
        private const val MAX_RETRIES = 2

        /** État du scan pour l'UI (null = aucun scan en cours). */
        fun observeStatus(workManager: WorkManager): Flow<ScanStatus?> =
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { infos ->
                infos.firstOrNull { !it.state.isFinished }?.let { info ->
                    val p = info.progress
                    ScanStatus(
                        phase = p.getString(KEY_PHASE)?.let { runCatching { ScanPhase.valueOf(it) }.getOrNull() }
                            ?: ScanPhase.WALKING,
                        scannedFiles = p.getInt(KEY_SCANNED, 0),
                        groupsChecked = p.getInt(KEY_GROUPS_CHECKED, 0),
                        groupsTotal = p.getInt(KEY_GROUPS_TOTAL, 0),
                    )
                }
            }.distinctUntilChanged()

        /** KEEP : un seul scan à la fois, les demandes en double sont ignorées. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Avancement d'un scan, lisible par l'UI. */
data class ScanStatus(
    val phase: ScanPhase,
    val scannedFiles: Int,
    val groupsChecked: Int,
    val groupsTotal: Int,
) {
    /** null = indéterminé (phase d'indexation : nombre total de fichiers inconnu). */
    val fraction: Float?
        get() = if (phase == ScanPhase.DUPLICATES && groupsTotal > 0) groupsChecked.toFloat() / groupsTotal else null

    val label: String
        get() = when (phase) {
            ScanPhase.WALKING -> "Indexation : $scannedFiles fichiers"
            ScanPhase.DUPLICATES -> "Recherche de doublons : $groupsChecked/$groupsTotal groupes"
            ScanPhase.DONE -> "Analyse terminée"
        }
}
