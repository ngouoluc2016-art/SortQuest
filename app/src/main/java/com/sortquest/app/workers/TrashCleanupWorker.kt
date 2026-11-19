package com.sortquest.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.usecase.PurgeExpiredTrashUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Une fois par jour : suppression définitive des éléments de corbeille de plus de 7 jours.
 * Contraintes douces (batterie non faible, appareil inactif) : aucune gêne pour l'utilisateur.
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val purgeExpiredTrash: PurgeExpiredTrashUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Sans accès, rien n'est supprimé : on réessaiera au prochain passage
        if (!StoragePermissionHelper.hasAllFilesAccess(applicationContext)) return Result.success()
        return try {
            val result = purgeExpiredTrash()
            Result.success(workDataOf(KEY_DELETED to result.deletedFiles, KEY_FREED to result.freedBytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_DELETED = "deleted"
        const val KEY_FREED = "freed"
        private const val UNIQUE_NAME = "sortquest_trash_cleanup"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .build()
            // KEEP : appeler schedule() à chaque démarrage ne crée jamais de doublon de tâche
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
