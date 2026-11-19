package com.sortquest.app.domain.repository

import com.sortquest.app.domain.gamification.GameEvent
import com.sortquest.app.domain.model.ActionReward
import com.sortquest.app.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun observe(): Flow<UserProgress>

    /** Applique un événement via le GamificationManager et persiste le résultat (atomique). */
    suspend fun apply(event: GameEvent): ActionReward

    /** La fête (niveau + nouveaux badges) a été affichée : ne plus la montrer. */
    suspend fun markCelebrated()
}
