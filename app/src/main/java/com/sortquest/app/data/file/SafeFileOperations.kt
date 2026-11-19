package com.sortquest.app.data.file

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opérations disque bas niveau, sans état.
 *
 * Garanties :
 *  1. Jamais d'écrasement : le nom cible est RÉSERVÉ atomiquement (création exclusive)
 *     avant le déplacement -> aucune fenêtre de course entre "exists()" et "move()".
 *  2. Déplacement atomique (rename) sur le même volume ; sinon copie + fsync + vérification
 *     de taille, puis suppression de la source.
 *  3. En cas d'échec, la source n'est jamais perdue.
 */
@Singleton
class SafeFileOperations @Inject constructor() {

    /** Déplace [source] dans [targetDir] et renvoie le fichier final (renommé si conflit). */
    @Throws(IOException::class)
    fun move(source: File, targetDir: File): File {
        if (!source.isFile) throw IOException("Source introuvable : ${source.path}")
        StoragePaths.requireSafe(source, targetDir)

        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            throw IOException("Impossible de créer le dossier ${targetDir.path}")
        }
        if (source.parentFile?.canonicalPath == targetDir.canonicalPath) return source // déjà en place

        val target = reserveTarget(targetDir, source.name)
        try {
            try {
                // REPLACE_EXISTING ne remplace QUE notre propre réservation vide
                Files.move(
                    source.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                copyThenDelete(source, target) // volumes différents (carte SD...)
            }
        } catch (t: Throwable) {
            if (source.exists()) target.delete() // on retire la réservation / copie partielle
            throw t
        }
        return target
    }

    /**
     * Trouve un nom libre ("photo.jpg", "photo (1).jpg", ...) et le crée vide de façon
     * exclusive (O_EXCL) : si un autre processus le prend entre-temps, on passe au suivant.
     */
    @Throws(IOException::class)
    fun reserveTarget(dir: File, fileName: String): File {
        val dot = fileName.lastIndexOf('.')
        val hasExt = dot > 0 // ".nomedia" n'a pas d'extension
        val rawBase = if (hasExt) fileName.substring(0, dot) else fileName
        val ext = if (hasExt) fileName.substring(dot) else ""
        val base = rawBase.replace(SUFFIX_REGEX, "") // évite "photo (1) (1).jpg"

        val candidates = sequenceOf(fileName) +
            (1..MAX_SUFFIX).asSequence().map { "$base ($it)$ext" } +
            sequenceOf("$base (${System.currentTimeMillis()})$ext")

        for (name in candidates) {
            val candidate = File(dir, name)
            try {
                Files.createFile(candidate.toPath())
                return candidate
            } catch (_: FileAlreadyExistsException) {
                continue
            }
        }
        throw IOException("Aucun nom disponible pour $fileName dans ${dir.path}")
    }

    @Throws(IOException::class)
    private fun copyThenDelete(source: File, target: File) {
        source.inputStream().use { input ->
            FileOutputStream(target, false).use { output ->
                input.copyTo(output, BUFFER)
                output.flush()
                output.fd.sync() // données réellement écrites avant de supprimer la source
            }
        }
        if (target.length() != source.length()) {
            target.delete()
            throw IOException("Copie incomplète de ${source.name}")
        }
        target.setLastModified(source.lastModified())
        if (!source.delete()) {
            target.delete() // on garde un seul exemplaire, à l'emplacement d'origine
            throw IOException("Impossible de supprimer la source ${source.path}")
        }
    }

    /** Empêche la galerie d'indexer un dossier (corbeille). */
    fun ensureNoMedia(dir: File) {
        if (!dir.isDirectory) dir.mkdirs()
        File(dir, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }

    /** Empreinte SHA-256 pour la détection de doublons. */
    @Throws(IOException::class)
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_SUFFIX = 9_999
        val SUFFIX_REGEX = Regex(""" \(\d+\)$""")
    }
}
