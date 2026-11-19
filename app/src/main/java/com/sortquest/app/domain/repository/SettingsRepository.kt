package com.sortquest.app.domain.repository

import kotlinx.coroutines.flow.Flow

/** Préférences d'expérience, réglables indépendamment. */
data class FeedbackSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<FeedbackSettings>
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
}
