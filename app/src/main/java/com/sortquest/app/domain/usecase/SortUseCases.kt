package com.sortquest.app.domain.usecase

import com.sortquest.app.domain.gamification.GameEvent
import com.sortquest.app.domain.model.ActionReward
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.ScanPhase
import com.sortquest.app.domain.model.ScanProgress
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.domain.model.toAction
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class SortOutcome(
    val item: FileItem,
    val record: SortRecord,
    val reward: ActionReward,
)

/** Scan complet : indexation du stockage, puis détection des doublons. */
class ScanStorageUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val duplicateScanner: DuplicateScannerUseCase,
) {
    operator fun invoke(): Flow<ScanProgress> = flow {
        var scanned = 0
        fileRepository.indexStorage().collect { p ->
            scanned = p.scannedFiles
            emit(p)
        }
        var found = 0
        duplicateScanner().collect { d ->
            found = d.duplicatesFound
            emit(
                ScanProgress(
                    phase = ScanPhase.DUPLICATES,
                    scannedFiles = scanned,
                    duplicatesFound = d.duplicatesFound,
                    groupsChecked = d.groupsChecked,
                    groupsTotal = d.groupsTotal,
                )
            )
        }
        emit(ScanProgress(ScanPhase.DONE, scanned, duplicatesFound = found))
    }
}

/** Swipe -> action disque journalisée -> XP / badges -> XP mémorisée sur l'historique. */
class SortFileUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(
        item: FileItem,
        direction: SwipeDirection,
        combo: Int,
        inExpressSession: Boolean,
        customTargetDir: String? = null,
        batchId: String? = null,
    ): Result<SortOutcome> {
        val action = direction.toAction()
        return fileRepository.applyAction(item, action, customTargetDir, batchId).mapCatching { record ->
            val reward = progressRepository.apply(
                GameEvent.FileSorted(action, item.sizeBytes, item.isDuplicate, combo, inExpressSession)
            )
            if (reward.actionXp > 0) fileRepository.recordXp(record.id, reward.actionXp)
            SortOutcome(item, record.copy(xpGained = reward.actionXp), reward)
        }
    }
}

/**
 * Annule une action PERSISTÉE (fonctionne aussi après redémarrage de l'appli).
 * [historyId] null = la dernière action, sinon une action précise (snackbar).
 */
class UndoLastActionUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(historyId: Long? = null): Result<SortRecord?> =
        (if (historyId != null) fileRepository.undo(historyId) else fileRepository.undoLast()).mapCatching { record ->
            record?.also { progressRepository.apply(it.toRevertEvent()) }
        }
}

/** Annule d'un coup toute une exécution Auto-Pilot. */
class UndoAutoPilotRunUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(batchId: String): Result<Int> =
        fileRepository.undoBatch(batchId).mapCatching { records ->
            records.forEach { progressRepository.apply(it.toRevertEvent()) }
            records.size
        }
}

class CompleteExpressSessionUseCase @Inject constructor(
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(sorted: Int, target: Int, remainingSeconds: Int): ActionReward =
        progressRepository.apply(GameEvent.ExpressCompleted(sorted, target, remainingSeconds))
}

private fun SortRecord.toRevertEvent() = GameEvent.ActionReverted(
    action = action,
    source = source,
    sizeBytes = sizeBytes,
    wasDuplicate = wasDuplicate,
    xpGained = xpGained,
)
