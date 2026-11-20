package com.sortquest.app.data.file

import android.webkit.MimeTypeMap
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.FileFacts
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.Placement
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.model.ThematicFolder
import com.sortquest.app.domain.organization.OrganizationEngine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Configuration d'organisation en vigueur (gamme de dossiers + réorganisation choisie + règles). */
data class OrganizationConfig(
    val folders: List<ThematicFolder>,
    val preset: OrganizationPreset,
    val rules: List<SortRule>,
)

/**
 * Catégorisation 100 % locale. Ordre de priorité :
 *  1. règle Auto-Pilot de l'utilisateur (intention explicite) ;
 *  2. réorganisation choisie (moteur pur du domaine : thème, thème + année, type, chronologie).
 */
@Singleton
class FileCategorizer @Inject constructor() {

    data class Result(
        val kind: FileKind,
        val mimeType: String?,
        val category: FileCategory,
        val placement: Placement,
    )

    fun categorize(file: File, config: OrganizationConfig): Result =
        categorize(file.name, file.length(), file.lastModified(), file.parentFile?.name.orEmpty(), config)

    fun categorize(fileName: String, sizeBytes: Long, lastModified: Long, parentName: String, config: OrganizationConfig): Result {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val kind = FileKinds.fromExtension(ext)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        val normalized = FileNameNormalizer.normalize(fileName.substringBeforeLast('.'))

        val placement = config.rules.firstOrNull { it.matches(normalized, ext, sizeBytes) }?.let { rule ->
            val dir = rule.targetDir.trim('/')
            Placement(dir, dir.substringAfterLast('/'), FolderIcon.CUSTOM, folderId = null, matchedKeyword = rule.keyword)
        } ?: OrganizationEngine.place(
            FileFacts(normalized, ext, kind, lastModified, FileNameNormalizer.normalize(parentName)),
            config.preset,
            config.folders,
        )
        return Result(kind, mime, categoryOf(placement.icon, kind), placement)
    }

    /** Catégorie large (couleur des étiquettes) déduite de la destination. */
    private fun categoryOf(icon: FolderIcon, kind: FileKind): FileCategory = when (icon) {
        FolderIcon.RECEIPT, FolderIcon.BANK, FolderIcon.TAX, FolderIcon.HEALTH, FolderIcon.CONTRACT,
        FolderIcon.WORK, FolderIcon.SCHOOL, FolderIcon.HOME, FolderIcon.VEHICLE, FolderIcon.TRAVEL,
        FolderIcon.IDENTITY, FolderIcon.DOCUMENT -> FileCategory.ADMINISTRATIF
        FolderIcon.PHOTO, FolderIcon.VIDEO, FolderIcon.MUSIC, FolderIcon.SCREENSHOT -> FileCategory.MEDIAS
        FolderIcon.ARCHIVE -> FileCategory.ARCHIVES
        FolderIcon.APP -> FileCategory.INSTALLATEURS
        else -> when (kind) {
            FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO -> FileCategory.MEDIAS
            FileKind.PDF, FileKind.DOCUMENT -> FileCategory.ADMINISTRATIF
            FileKind.ARCHIVE -> FileCategory.ARCHIVES
            FileKind.APK -> FileCategory.INSTALLATEURS
            FileKind.OTHER -> FileCategory.TELECHARGEMENTS
        }
    }

    companion object {
        fun kindOf(ext: String): FileKind = FileKinds.fromExtension(ext)
    }
}
