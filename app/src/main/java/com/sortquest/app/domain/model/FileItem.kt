package com.sortquest.app.domain.model

/** Nature technique du fichier (sert à choisir l'aperçu et la catégorie par défaut). */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, APK, ARCHIVE, OTHER }

object FileKinds {
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng")
    private val VIDEO = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi")
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr")
    private val DOCS = setOf("doc", "docx", "odt", "rtf", "txt", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp")
    private val APK = setOf("apk", "xapk", "apks", "apkm")
    private val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

    fun fromExtension(ext: String): FileKind = when (ext.lowercase()) {
        in IMAGE -> FileKind.IMAGE
        in VIDEO -> FileKind.VIDEO
        in AUDIO -> FileKind.AUDIO
        "pdf" -> FileKind.PDF
        in DOCS -> FileKind.DOCUMENT
        in APK -> FileKind.APK
        in ARCHIVES -> FileKind.ARCHIVE
        else -> FileKind.OTHER
    }
}

/** Dossiers thématiques proposés par SortQuest. [folderName] = sous-dossier de Documents/SortQuest. */
enum class FileCategory(val label: String, val folderName: String) {
    ADMINISTRATIF("Administratif & Factures", "Administratif"),
    MEDIAS("Médias & Souvenirs", "Medias"),
    TELECHARGEMENTS("Téléchargements en vrac", "Telechargements"),
    INSTALLATEURS("Installateurs (APK)", "Installateurs"),
    ARCHIVES("Archives", "Archives"),
    DOUBLONS_LOURDS("Doublons & Fichiers lourds", "A_verifier"),
}

/** Cycle de vie d'un fichier dans SortQuest. */
enum class FileStatus { PENDING, SORTED, TRASHED, SKIPPED, VAULTED }

data class FileItem(
    val id: Long,
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: FileKind,
    val category: FileCategory,
    /** Chemin RELATIF à la racine du stockage, ex. "Documents/SortQuest/Administratif/Factures". */
    val suggestedTargetDir: String,
    val matchedKeyword: String?,
    val isDuplicate: Boolean,
    val status: FileStatus,
    /** Empreinte des 64 premiers + 64 derniers Ko (null = non calculée ou périmée). */
    val partialHash: String? = null,
    /** SHA-256 complet (null = non calculé ou périmé). */
    val contentHash: String? = null,
)
