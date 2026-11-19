package com.sortquest.app.data.repository

import com.sortquest.app.data.local.dao.UserProgressDao
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.data.mapper.toEntity
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.gamification.GameEvent
import com.sortquest.app.domain.gamification.GamificationManager
import com.sortquest.app.domain.model.ActionReward
import com.sortquest.app.domain.model.UserProgress
import com.sortquest.app.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Persistance de la progression ; toute la logique de jeu est déléguée au GamificationManager. */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val dao: UserProgressDao,
    private val manager: GamificationManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProgressRepository {

    /** Lecture-calcul-écriture atomique (swipe et worker Auto-Pilot peuvent écrire en même temps). */
    private val mutex = Mutex()

    override fun observe(): Flow<UserProgress> =
        dao.observe().map { it?.toDomain() ?: UserProgress.EMPTY }.distinctUntilChanged()

    override suspend fun apply(event: GameEvent): ActionReward = withContext(io) {
        mutex.withLock {
            val current = dao.get()?.toDomain() ?: UserProgress.EMPTY
            val outcome = manager.apply(current, event, LocalDate.now().toEpochDay())
            if (outcome.progress != current) dao.upsert(outcome.progress.toEntity())
            outcome.reward
        }
    }

    override suspend fun markCelebrated() = withContext(io) {
        mutex.withLock {
            val current = dao.get()?.toDomain() ?: return@withLock
            dao.upsert(current.copy(celebratedLevel = current.level, seenBadges = current.badges).toEntity())
        }
    }
}
