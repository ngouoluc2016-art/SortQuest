package com.sortquest.app.data.file

import android.os.Environment
import java.io.File
import java.nio.file.Files

/**
 * Point unique de vérité sur les chemins manipulés par SortQuest.
 * Toute écriture passe par [requireSafe] : on ne sort jamais du stockage partagé
 * et on ne touche jamais aux zones privées des autres applis.
 */
object StoragePaths {

    @Suppress("DEPRECATION") // toujours valide avec MANAGE_EXTERNAL_STORAGE
    val root: File get() = Environment.getExternalStorageDirectory()

    const val OUTPUT_RELATIVE = "Documents/SortQuest"
    const val VAULT_RELATIVE = "$OUTPUT_RELATIVE/Coffre-fort"
    /**
     * Corbeille : dossier caché + .nomedia dans le stockage partagé (pas dans le dossier privé
     * de l'appli). Même volume = déplacement instantané, et une désinstallation ne détruit pas
     * des fichiers que l'utilisateur pensait pouvoir encore récupérer.
     */
    const val TRASH_RELATIVE = ".SortQuest_Trash"

    val outputRoot: File get() = File(root, OUTPUT_RELATIVE)
    val vaultDir: File get() = File(root, VAULT_RELATIVE)
    val trashDir: File get() = File(root, TRASH_RELATIVE)
    val downloadsDir: File get() = File(root, Environment.DIRECTORY_DOWNLOADS)

    /** Dossiers analysés (cahier des charges : Download, Documents, DCIM...). */
    fun scanRoots(): List<File> = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_MUSIC,
    ).map { File(root, it) }.filter { it.isDirectory }

    /** Zones à ne JAMAIS modifier (données d'autres applis). */
    private val forbiddenRelative = listOf("Android/data", "Android/obb")

    /** Dossiers ignorés pendant le scan. */
    fun shouldSkipDir(dir: File): Boolean {
        if (dir.name.startsWith(".")) return true                 // cachés (.thumbnails, .trash...)
        if (Files.isSymbolicLink(dir.toPath())) return true       // évite les boucles
        val canonical = dir.canonicalPath
        if (canonical == outputRoot.canonicalPath) return true     // déjà rangé par SortQuest
        if (canonical == trashDir.canonicalPath) return true
        return File(canonical).relativeToOrNull(root.canonicalFile)?.path
            ?.let { rel -> rel == "Android" || rel.startsWith("Android/") } == true
    }

    fun resolve(relativeOrAbsolute: String): File {
        val f = File(relativeOrAbsolute)
        return if (f.isAbsolute) f else File(root, relativeOrAbsolute.trim('/'))
    }

    fun relative(file: File): String =
        file.canonicalFile.relativeToOrNull(root.canonicalFile)?.path ?: file.path

    fun isSafe(file: File): Boolean {
        val rootCanonical = root.canonicalPath
        val canonical = file.canonicalPath
        if (canonical != rootCanonical && !canonical.startsWith("$rootCanonical/")) return false
        val rel = canonical.removePrefix(rootCanonical).trimStart('/')
        return forbiddenRelative.none { rel == it || rel.startsWith("$it/") }
    }

    fun requireSafe(vararg files: File) {
        files.forEach { require(isSafe(it)) { "Chemin refusé (hors zone autorisée) : ${it.path}" } }
    }
}
