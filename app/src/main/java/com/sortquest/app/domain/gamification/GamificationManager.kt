package com.sortquest.app.domain.gamification

import com.sortquest.app.domain.model.ActionReward
import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.Badge
import com.sortquest.app.domain.model.UserProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tout ce qui peut faire bouger la progression. */
sealed interface GameEvent {
    data class FileSorted(
        val action: ActionType,
        val sizeBytes: Long,
        val isDuplicate: Boolean,
        val combo: Int,
        val inExpressSession: Boolean,
    ) : GameEvent

    data class AutoPilotRun(val filesMoved: Int) : GameEvent

    data class ExpressCompleted(val sorted: Int, val target: Int, val remainingSeconds: Int) : GameEvent

    data class ActionReverted(
        val action: ActionType,
        val source: ActionSource,
        val sizeBytes: Long,
        val wasDuplicate: Boolean,
        val xpGained: Int,
    ) : GameEvent
}

/**
 * Moteur de gamification : fonction PURE (progression + événement + date) -> nouvelle progression.
 * Aucune dépendance Android, aucun accès disque : testable unitairement en quelques lignes.
 *
 * Barème :
 *  - Fichier classé ou mis au coffre-fort ........ +10 XP
 *  - Fichier mis à la corbeille ................... +5 XP (+1 par tranche de 10 Mo, max +20)
 *  - Doublon mis à la corbeille ................... +25 XP (+ bonus de taille)
 *  - "Plus tard" .................................. 0 XP
 *  - Combo en Session Express ..................... x(1 + 0,1 x combo), plafonné à x2
 *  - Session Express réussie ...................... +50 XP + 2 XP par seconde restante
 *  - Auto-Pilot ................................... +2 XP par fichier rangé
 *  - Bonus de série (1re action manuelle du jour) . +5 XP x jours de série (max +35)
 */
@Singleton
class GamificationManager @Inject constructor() {

    data class Outcome(val progress: UserProgress, val reward: ActionReward)

    fun apply(current: UserProgress, event: GameEvent, todayEpochDay: Long): Outcome {
        var p = current
        var actionXp = 0
        var streakBonus = 0

        when (event) {
            is GameEvent.FileSorted -> {
                if (event.action == ActionType.SKIP) return Outcome(current, ActionReward.NONE)
                actionXp = xpForSort(event)
                val isTrash = event.action == ActionType.TRASH
                p = p.copy(
                    filesSorted = p.filesSorted + 1,
                    bytesFreed = p.bytesFreed + if (isTrash) event.sizeBytes else 0,
                    duplicatesTrashed = p.duplicatesTrashed + if (isTrash && event.isDuplicate) 1 else 0,
                    bestCombo = max(p.bestCombo, event.combo),
                )
                val (withStreak, bonus) = updateStreak(p, todayEpochDay)
                p = withStreak
                streakBonus = bonus
            }

            is GameEvent.AutoPilotRun -> {
                actionXp = event.filesMoved * AUTO_PILOT_XP_PER_FILE
                p = p.copy(autoPilotMoves = p.autoPilotMoves + event.filesMoved)
            }

            is GameEvent.ExpressCompleted -> {
                if (event.sorted >= event.target) {
                    actionXp = EXPRESS_SUCCESS_XP + EXPRESS_XP_PER_SECOND_LEFT * event.remainingSeconds
                }
            }

            is GameEvent.ActionReverted -> {
                if (event.action == ActionType.SKIP) return Outcome(current, ActionReward.NONE)
                val isTrash = event.action == ActionType.TRASH
                val reverted = if (event.source == ActionSource.AUTO_PILOT) {
                    p.copy(autoPilotMoves = (p.autoPilotMoves - 1).coerceAtLeast(0))
                } else {
                    p.copy(
                        filesSorted = (p.filesSorted - 1).coerceAtLeast(0),
                        bytesFreed = (p.bytesFreed - if (isTrash) event.sizeBytes else 0).coerceAtLeast(0),
                        duplicatesTrashed = (p.duplicatesTrashed - if (isTrash && event.wasDuplicate) 1 else 0)
                            .coerceAtLeast(0),
                    )
                }
                // Les badges et la série restent acquis : on ne "punit" pas une annulation.
                return Outcome(
                    reverted.copy(totalXp = (reverted.totalXp - event.xpGained).coerceAtLeast(0)),
                    ActionReward.NONE,
                )
            }
        }

        val gained = actionXp + streakBonus
        p = p.copy(totalXp = p.totalXp + gained)
        val newBadges = evaluateBadges(p) - p.badges
        p = p.copy(badges = p.badges + newBadges)

        return Outcome(
            progress = p,
            reward = ActionReward(
                xpGained = gained,
                levelUp = p.level.takeIf { it.ordinal > current.level.ordinal },
                newBadges = newBadges.toList(),
                streakBonus = streakBonus,
            ),
        )
    }

    fun xpForSort(e: GameEvent.FileSorted): Int {
        val sizeBonus = min((e.sizeBytes / TEN_MB).toInt(), MAX_SIZE_BONUS)
        val base = when (e.action) {
            ActionType.MOVE, ActionType.VAULT -> SORT_XP
            ActionType.TRASH -> (if (e.isDuplicate) DUPLICATE_XP else TRASH_XP) + sizeBonus
            ActionType.SKIP -> 0
        }
        if (!e.inExpressSession) return base
        val multiplier = 1f + min(e.combo, MAX_COMBO_STEPS) * COMBO_STEP
        return (base * multiplier).roundToInt()
    }

    /** Série de jours consécutifs : +1 si hier, inchangée si aujourd'hui, sinon repart à 1. */
    private fun updateStreak(p: UserProgress, today: Long): Pair<UserProgress, Int> {
        if (p.lastActiveEpochDay == today) return p to 0
        val streak = if (p.lastActiveEpochDay == today - 1) p.currentStreak + 1 else 1
        val bonus = STREAK_XP_PER_DAY * min(streak, MAX_STREAK_DAYS_BONUS)
        return p.copy(
            currentStreak = streak,
            bestStreak = max(p.bestStreak, streak),
            lastActiveEpochDay = today,
        ) to bonus
    }

    fun evaluateBadges(p: UserProgress): Set<Badge> = buildSet {
        if (p.filesSorted >= 1) add(Badge.PREMIER_TRI)
        if (p.duplicatesTrashed >= 10) add(Badge.CHASSEUR_DOUBLONS)
        if (p.bytesFreed >= ONE_GB) add(Badge.UN_GO_LIBERE)
        if (p.autoPilotMoves >= 50) add(Badge.MAITRE_AUTO_PILOT)
        if (p.bestStreak >= 7) add(Badge.ARCHIVISTE_REGULIER)
    }

    companion object {
        const val SORT_XP = 10
        const val TRASH_XP = 5
        const val DUPLICATE_XP = 25
        const val AUTO_PILOT_XP_PER_FILE = 2
        const val EXPRESS_SUCCESS_XP = 50
        const val EXPRESS_XP_PER_SECOND_LEFT = 2
        const val STREAK_XP_PER_DAY = 5
        const val MAX_STREAK_DAYS_BONUS = 7
        const val COMBO_WINDOW_MS = 3_000L
        private const val COMBO_STEP = 0.1f
        private const val MAX_COMBO_STEPS = 10
        private const val MAX_SIZE_BONUS = 20
        private const val TEN_MB = 10L * 1024 * 1024
        private const val ONE_GB = 1024L * 1024 * 1024
    }
}
