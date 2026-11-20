package com.sortquest.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.sortquest.app.domain.repository.FeedbackSettings
import com.sortquest.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Une seule instance de DataStore par fichier et par processus (exigence de la bibliothèque). */
internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore : écritures transactionnelles hors thread UI, lecture en Flow. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<FeedbackSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e } // fichier corrompu : valeurs par défaut
        .map { p ->
            FeedbackSettings(
                soundEnabled = p[SOUND] ?: true,
                hapticsEnabled = p[HAPTICS] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[SOUND] = enabled }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[HAPTICS] = enabled }
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
    }
}
