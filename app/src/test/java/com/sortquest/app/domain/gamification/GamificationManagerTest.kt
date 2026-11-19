package com.sortquest.app.domain.gamification

import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.Badge
import com.sortquest.app.domain.model.Level
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.model.UserProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationManagerTest {

    private val manager = GamificationManager()
    private val day = 20_000L

    private fun sorted(action: ActionType, size: Long = 0, dup: Boolean = false, combo: Int = 1, express: Boolean = false) =
        GameEvent.FileSorted(action, size, dup, combo, express)

    @Test fun `premier tri - 10 XP, bonus de serie, badge Premier Tri`() {
        val out = manager.apply(UserProgress.EMPTY, sorted(ActionType.MOVE), day)
        assertEquals(10, out.reward.actionXp)
        assertEquals(5, out.reward.streakBonus) // 1 jour de série
        assertEquals(15L, out.progress.totalXp)
        assertEquals(listOf(Badge.PREMIER_TRI), out.reward.newBadges)
    }

    @Test fun `doublon a la corbeille - 25 XP plus bonus de taille`() {
        val fiftyMb = 50L * 1024 * 1024
        val out = manager.apply(UserProgress.EMPTY, sorted(ActionType.TRASH, fiftyMb, dup = true), day)
        assertEquals(25 + 5, out.reward.actionXp)
        assertEquals(fiftyMb, out.progress.bytesFreed)
        assertEquals(1, out.progress.duplicatesTrashed)
    }

    @Test fun `combo multiplie l'XP uniquement en Session Express`() {
        assertEquals(10, manager.xpForSort(sorted(ActionType.MOVE, combo = 5, express = false)))
        assertEquals(15, manager.xpForSort(sorted(ActionType.MOVE, combo = 5, express = true)))
        assertEquals(20, manager.xpForSort(sorted(ActionType.MOVE, combo = 50, express = true))) // plafond x2
    }

    @Test fun `plus tard ne rapporte rien et ne touche pas la serie`() {
        val out = manager.apply(UserProgress.EMPTY, sorted(ActionType.SKIP), day)
        assertEquals(UserProgress.EMPTY, out.progress)
    }

    @Test fun `serie - +1 le lendemain, repart a 1 apres un trou, badge a 7 jours`() {
        var p = UserProgress.EMPTY
        repeat(7) { i -> p = manager.apply(p, sorted(ActionType.MOVE), day + i).progress }
        assertEquals(7, p.currentStreak)
        assertTrue(Badge.ARCHIVISTE_REGULIER in p.badges)

        p = manager.apply(p, sorted(ActionType.MOVE), day + 10).progress
        assertEquals(1, p.currentStreak)
        assertEquals(7, p.bestStreak)
        assertEquals(0, p.streakAsOf(day + 12))
    }

    @Test fun `deuxieme action du meme jour - pas de second bonus de serie`() {
        val p1 = manager.apply(UserProgress.EMPTY, sorted(ActionType.MOVE), day).progress
        val out = manager.apply(p1, sorted(ActionType.MOVE), day)
        assertEquals(0, out.reward.streakBonus)
    }

    @Test fun `Auto-Pilot - 2 XP par fichier, badge a 50 fichiers`() {
        val out = manager.apply(UserProgress.EMPTY, GameEvent.AutoPilotRun(50), day)
        assertEquals(100L, out.progress.totalXp)
        assertTrue(Badge.MAITRE_AUTO_PILOT in out.reward.newBadges)
    }

    @Test fun `1 Go libere debloque le badge`() {
        val oneGb = 1024L * 1024 * 1024
        val out = manager.apply(UserProgress.EMPTY, sorted(ActionType.TRASH, oneGb), day)
        assertTrue(Badge.UN_GO_LIBERE in out.progress.badges)
    }

    @Test fun `annulation - retire XP et compteurs, garde les badges`() {
        val sortedOut = manager.apply(UserProgress.EMPTY, sorted(ActionType.TRASH, dup = true), day)
        val reverted = manager.apply(
            sortedOut.progress,
            GameEvent.ActionReverted(ActionType.TRASH, ActionSource.MANUAL, 0, true, sortedOut.reward.actionXp),
            day,
        ).progress
        assertEquals(5L, reverted.totalXp) // seul le bonus de série reste
        assertEquals(0, reverted.filesSorted)
        assertEquals(0, reverted.duplicatesTrashed)
        assertTrue(Badge.PREMIER_TRI in reverted.badges)
    }

    @Test fun `montee de niveau detectee et en attente de celebration`() {
        val start = UserProgress(totalXp = 295)
        val out = manager.apply(start, sorted(ActionType.MOVE), day)
        assertEquals(Level.APPRENTI, out.reward.levelUp)
        assertEquals(Level.APPRENTI, out.progress.pendingLevelUp)
    }

    @Test fun `Session Express ratee - aucun bonus`() {
        val out = manager.apply(UserProgress.EMPTY, GameEvent.ExpressCompleted(12, 20, 0), day)
        assertEquals(0, out.reward.xpGained)
        assertNull(out.reward.levelUp)
    }

    @Test fun `regle - conditions cumulatives, accents ignores`() {
        val rule = SortRule(name = "EDF", keyword = "Électricité", extensions = setOf("pdf"),
            minSizeBytes = 1_000, targetDir = "Documents/Factures")
        assertTrue(rule.matches("facture electricite mars", "pdf", 5_000))
        assertFalse(rule.matches("facture electricite mars", "jpg", 5_000))
        assertFalse(rule.matches("facture electricite mars", "pdf", 10))
        assertFalse(SortRule(name = "vide", targetDir = "Documents").matches("x", "pdf", 1))
    }
}
