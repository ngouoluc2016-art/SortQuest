package com.sortquest.app.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RawRes
import com.sortquest.app.R
import com.sortquest.app.di.ApplicationScope
import com.sortquest.app.domain.repository.FeedbackSettings
import com.sortquest.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Moments de retour sensoriel. Un événement = un son + une vibration, joués ensemble. */
enum class FeedbackEvent(@RawRes val sound: Int?, val haptic: Int) {
    /** Seuil de swipe franchi : pas de son (trop fréquent), petit tic. */
    THRESHOLD(null, Haptics.THRESHOLD),
    SORTED(R.raw.sq_pop, Haptics.CONFIRM),
    VAULTED(R.raw.sq_whoosh, Haptics.CONFIRM),
    TRASHED(R.raw.sq_trash, Haptics.REJECT),
    SKIPPED(R.raw.sq_tick, HapticFeedbackConstants.KEYBOARD_TAP),
    UNDO(R.raw.sq_whoosh, HapticFeedbackConstants.KEYBOARD_TAP),
    CELEBRATE(R.raw.sq_levelup, HapticFeedbackConstants.LONG_PRESS),
    ERROR(null, Haptics.REJECT),
}

private object Haptics {
    val THRESHOLD = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
    val CONFIRM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    val REJECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
}

/**
 * Sons courts + haptique, synchronisés.
 *
 *  - SoundPool : sons pré-chargés en mémoire, latence de quelques ms (contrairement à MediaPlayer).
 *  - Mode silencieux / vibreur respecté : aucun son si la sonnerie n'est pas en mode normal.
 *  - Haptique via View.performHapticFeedback : suit aussi le réglage système "Vibrations au toucher".
 *  - Deux interrupteurs indépendants (écran Paramètres), lus depuis un StateFlow : zéro attente.
 *
 * Singleton applicatif : ne garde AUCUNE référence à une View ou une Activity (pas de fuite).
 */
@Singleton
class FeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val settings: StateFlow<FeedbackSettings> =
        settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, FeedbackSettings())

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Créé au premier son : aucun coût au démarrage de l'appli. */
    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool -> pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) } }
    }

    private val soundIds = HashMap<Int, Int>() // ressource -> id SoundPool
    private val loaded = java.util.Collections.synchronizedSet(HashSet<Int>())

    /** Précharge les sons (appelé une fois au démarrage, en arrière-plan). */
    fun preload() {
        FeedbackEvent.entries.mapNotNull { it.sound }.distinct().forEach { res ->
            if (res !in soundIds) soundIds[res] = soundPool.load(context, res, 1)
        }
    }

    /** Joue l'événement : [view] sert uniquement à l'haptique (peut être null). */
    fun perform(event: FeedbackEvent, view: View?) {
        val s = settings.value
        if (s.hapticsEnabled) view?.performHapticFeedback(event.haptic)
        if (s.soundEnabled) event.sound?.let(::playSound)
    }

    private fun playSound(@RawRes res: Int) {
        // Respect du mode silencieux ET vibreur
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = soundIds[res] ?: soundPool.load(context, res, 1).also { soundIds[res] = it }
        if (id !in loaded) return // pas encore décodé : on saute plutôt que d'ajouter de la latence
        soundPool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        /** Volumes volontairement bas : retour discret, jamais intrusif. */
        const val VOLUME = 0.35f
    }
}
