package com.sortquest.app.domain.organization

/** Pourquoi un fichier n'est jamais touché par SortQuest. */
enum class ProtectionReason(val label: String) {
    HIDDEN("Fichier ou dossier caché"),
    SYSTEM_AREA("Zone réservée au système Android et aux applications"),
    SYSTEM_SOUNDS("Sons du système (sonneries, notifications, alarmes)"),
    APP_DATA("Données ou cache d'une application"),
    TECHNICAL("Fichier technique (base de données, journal, verrou...)"),
    IN_PROGRESS("Fichier en cours de téléchargement ou d'écriture"),
    EMPTY("Fichier vide"),
}

/**
 * Politique de protection : SortQuest ne déplace, ne supprime ni n'indexe
 * aucun fichier dont le déplacement pourrait nuire au téléphone ou à une application.
 *
 * Appliquée à TROIS niveaux (défense en profondeur) :
 *  1. au scan (le fichier n'apparaît jamais dans l'appli),
 *  2. au moment du déplacement (refus même si une ancienne entrée existe en base),
 *  3. à l'Auto-Pilot.
 */
object ProtectedFilesPolicy {

    /** Dossiers dont le contenu est utilisé par le système (sonneries réglées, etc.). */
    private val SYSTEM_SOUND_DIRS = setOf("ringtones", "notifications", "alarms")

    /** Dossiers techniques d'applications, à n'importe quelle profondeur. */
    private val APP_DATA_DIRS = setOf(
        "android", "databases", "database", "cache", "caches", "logs", "log", "temp", "tmp",
        "shared_prefs", "files_internal", "lost.dir", "backups_internal",
    )

    /** Extensions de fichiers techniques (bases, bibliothèques, journaux, verrous...). */
    private val TECHNICAL_EXT = setOf(
        "db", "db-journal", "db-wal", "db-shm", "sqlite", "sqlite3", "realm",
        "so", "odex", "oat", "vdex", "dex", "art",
        "lock", "lck", "pid", "log", "tmp", "temp", "cache", "nomedia",
        "dat", "idx", "ini", "cfg", "conf", "sys", "bak",
    )

    /** Téléchargements inachevés (Chrome, Firefox, gestionnaires de téléchargement). */
    private val IN_PROGRESS_EXT = setOf("crdownload", "part", "partial", "download", "opdownload", "tmpdl")

    /** En dessous de ce délai depuis la dernière modification, le fichier est peut-être en cours d'écriture. */
    const val WRITE_GRACE_MS = 30_000L

    /**
     * @param relativePath chemin relatif à la racine du stockage, nom du fichier inclus
     *                     (ex. "Download/Facture.pdf")
     * @return la raison de protection, ou null si le fichier peut être organisé
     */
    fun check(relativePath: String, sizeBytes: Long, modifiedAgoMs: Long = Long.MAX_VALUE): ProtectionReason? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ProtectionReason.SYSTEM_AREA
        val name = segments.last()
        val dirs = segments.dropLast(1).map { it.lowercase() }

        if (segments.any { it.startsWith(".") }) return ProtectionReason.HIDDEN
        if (dirs.firstOrNull() == "android") return ProtectionReason.SYSTEM_AREA
        if (dirs.any { it in SYSTEM_SOUND_DIRS }) return ProtectionReason.SYSTEM_SOUNDS
        if (dirs.any { it in APP_DATA_DIRS }) return ProtectionReason.APP_DATA

        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (lower.startsWith("~$") || lower.startsWith(".~lock")) return ProtectionReason.TECHNICAL // verrous Office
        if (ext in IN_PROGRESS_EXT || lower.startsWith(".pending-")) return ProtectionReason.IN_PROGRESS
        if (ext in TECHNICAL_EXT || lower.startsWith("thumbdata")) return ProtectionReason.TECHNICAL
        if (sizeBytes <= 0L) return ProtectionReason.EMPTY
        if (modifiedAgoMs in 0 until WRITE_GRACE_MS) return ProtectionReason.IN_PROGRESS
        return null
    }

    fun isProtected(relativePath: String, sizeBytes: Long, modifiedAgoMs: Long = Long.MAX_VALUE): Boolean =
        check(relativePath, sizeBytes, modifiedAgoMs) != null
}
