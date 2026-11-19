package com.sortquest.app.domain.model

enum class Level(val title: String, val minXp: Long) {
    NOVICE("Novice du Rangement", 0),
    APPRENTI("Apprenti Trieur", 300),
    ARCHIVISTE("Archiviste Zen", 1_000),
    CONSERVATEUR("Conservateur Méthodique", 3_000),
    MAITRE("Maître du Stockage", 8_000);

    val number: Int get() = ordinal + 1
    val next: Level? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun fromXp(xp: Long): Level = entries.last { xp >= it.minXp }
        fun fromIndex(index: Int): Level = entries.getOrElse(index) { NOVICE }
    }
}

/** Les 5 badges clés. Conditions de déblocage : voir GamificationManager.evaluateBadges. */
enum class Badge(val title: String, val description: String) {
    PREMIER_TRI("Premier Tri", "Trier ton tout premier fichier"),
    CHASSEUR_DOUBLONS("Chasseur de Doublons", "Supprimer 10 doublons"),
    UN_GO_LIBERE("1 Go Libéré", "Libérer 1 Go d'espace de stockage"),
    MAITRE_AUTO_PILOT("Maître de l'Auto-Pilot", "Laisser l'Auto-Pilot ranger 50 fichiers"),
    ARCHIVISTE_REGULIER("Archiviste Régulier", "Trier au moins un fichier 7 jours d'affilée"),
}

data class UserProgress(
    val totalXp: Long = 0,
    val bytesFreed: Long = 0,
    val filesSorted: Int = 0,
    val duplicatesTrashed: Int = 0,
    val autoPilotMoves: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /** Jour (LocalDate.toEpochDay) de la dernière action manuelle ; -1 = jamais. */
    val lastActiveEpochDay: Long = -1,
    val bestCombo: Int = 0,
    val badges: Set<Badge> = emptySet(),
    /** Dernier niveau déjà fêté à l'écran (la fête survit à une fermeture de l'appli). */
    val celebratedLevel: Level = Level.NOVICE,
    val seenBadges: Set<Badge> = emptySet(),
) {
    /** Le niveau découle TOUJOURS de l'XP : une seule source de vérité. */
    val level: Level get() = Level.fromXp(totalXp)

    val levelProgress: Float
        get() {
            val next = level.next ?: return 1f
            return ((totalXp - level.minXp).toFloat() / (next.minXp - level.minXp)).coerceIn(0f, 1f)
        }

    val xpToNextLevel: Long? get() = level.next?.let { it.minXp - totalXp }

    /** Série affichée : retombe à 0 si la dernière action date d'avant-hier ou plus. */
    fun streakAsOf(todayEpochDay: Long): Int =
        if (lastActiveEpochDay >= todayEpochDay - 1) currentStreak else 0

    val pendingLevelUp: Level? get() = level.takeIf { it.ordinal > celebratedLevel.ordinal }
    val pendingBadges: Set<Badge> get() = badges - seenBadges
    val hasPendingCelebration: Boolean get() = pendingLevelUp != null || pendingBadges.isNotEmpty()

    companion object {
        val EMPTY = UserProgress()
    }
}

data class ActionReward(
    /** XP total gagné, bonus de série inclus. */
    val xpGained: Int,
    val levelUp: Level? = null,
    val newBadges: List<Badge> = emptyList(),
    /** Part de [xpGained] due au bonus quotidien de série. */
    val streakBonus: Int = 0,
) {
    /** XP attribuable à l'action elle-même (celle qu'on retire en cas d'annulation). */
    val actionXp: Int get() = xpGained - streakBonus

    companion object {
        val NONE = ActionReward(0)
    }
}
