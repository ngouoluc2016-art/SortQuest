package com.sortquest.app.ui.sort.components

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.sortquest.app.core.feedback.FeedbackEvent
import com.sortquest.app.core.feedback.FeedbackManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Façade Compose du FeedbackManager : son + vibration synchronisés, réglages respectés.
 * La View n'est référencée que par cet objet, lui-même limité à la composition qui l'a créé
 * (remember) : aucune View ne fuit vers un singleton.
 */
class SortHaptics(private val view: View, private val feedback: FeedbackManager) {
    fun tick() = feedback.perform(FeedbackEvent.THRESHOLD, view)
    fun sorted() = feedback.perform(FeedbackEvent.SORTED, view)
    fun vaulted() = feedback.perform(FeedbackEvent.VAULTED, view)
    fun trashed() = feedback.perform(FeedbackEvent.TRASHED, view)
    fun skipped() = feedback.perform(FeedbackEvent.SKIPPED, view)
    fun undo() = feedback.perform(FeedbackEvent.UNDO, view)
    fun celebrate() = feedback.perform(FeedbackEvent.CELEBRATE, view)
    fun error() = feedback.perform(FeedbackEvent.ERROR, view)

    // Alias conservés pour les appels existants
    fun confirm() = sorted()
    fun reject() = error()
    fun light() = skipped()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedbackEntryPoint {
    fun feedbackManager(): FeedbackManager
}

fun Context.feedbackManager(): FeedbackManager =
    EntryPointAccessors.fromApplication(applicationContext, FeedbackEntryPoint::class.java).feedbackManager()

@Composable
fun rememberSortHaptics(): SortHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { SortHaptics(view, context.feedbackManager()) }
}
