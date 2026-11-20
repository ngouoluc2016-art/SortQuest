package com.sortquest.app.core.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Ouverture et partage d'un fichier avec une autre application.
 * Passe par un FileProvider : l'autre appli reçoit un accès temporaire, en lecture seule,
 * à CE fichier uniquement (jamais un chemin brut, refusé depuis Android 7).
 */
object FileOpener {

    fun mimeOf(path: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(path).extension.lowercase()) ?: "*/*"

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** @return false si aucune application ne sait ouvrir ce type de fichier. */
    fun open(context: Context, path: String): Boolean = launch(context) {
        Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(context, File(path)), mimeOf(path))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .let { Intent.createChooser(it, "Ouvrir avec") }
    }

    fun share(context: Context, path: String): Boolean = launch(context) {
        Intent(Intent.ACTION_SEND).setType(mimeOf(path))
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, File(path)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .let { Intent.createChooser(it, "Partager") }
    }

    private fun launch(context: Context, build: () -> Intent): Boolean = try {
        context.startActivity(build().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: IllegalArgumentException) { // fichier hors des chemins autorisés
        false
    }
}
