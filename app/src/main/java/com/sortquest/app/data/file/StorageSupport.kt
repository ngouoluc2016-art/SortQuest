package com.sortquest.app.data.file

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verrou UNIQUE pour toute opération disque + base (swipe, annulation, Auto-Pilot, purge).
 * Garantit qu'un swipe et l'Auto-Pilot ne déplacent jamais le même fichier en même temps.
 */
@Singleton
class StorageLock @Inject constructor() {
    val mutex = Mutex()
}

/** Tient la galerie et les autres applis à jour après un déplacement. */
@Singleton
class MediaStoreNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** L'ancien chemin disparaît de l'index MediaStore, le nouveau est indexé. */
    fun notifyMoved(from: File, to: File) {
        MediaScannerConnection.scanFile(context, arrayOf(from.absolutePath, to.absolutePath), null, null)
    }
}
