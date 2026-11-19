package com.sortquest.app.data.file

import android.webkit.MimeTypeMap
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.domain.model.SortRule
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catégorisation légère, 100 % locale : extension + mots-clés dans le nom + règles utilisateur.
 * Ordre de priorité : règle Auto-Pilot > fichier lourd > type technique > mots-clés.
 */
@Singleton
class FileCategorizer @Inject constructor() {

    data class Result(
        val kind: FileKind,
        val mimeType: String?,
        val category: FileCategory,
        val targetDir: String,
        val matchedKeyword: String?,
    )

    fun categorize(file: File, rules: List<SortRule>): Result {
        val ext = file.extension.lowercase()
        val kind = kindOf(ext)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        val normalized = FileNameNormalizer.normalize(file.nameWithoutExtension)
        val size = file.length()
        val base = StoragePaths.OUTPUT_RELATIVE

        // 1. Règles utilisateur (déjà triées par priorité décroissante)
        rules.firstOrNull { it.matches(normalized, ext, size) }?.let { rule ->
            return Result(kind, mime, defaultCategory(kind, file), rule.targetDir.trim('/'), rule.keyword)
        }

        // 2. Fichiers lourds : à examiner en priorité, quel que soit leur type
        if (size >= LARGE_FILE_BYTES) {
            return Result(kind, mime, FileCategory.DOUBLONS_LOURDS,
                "$base/${FileCategory.DOUBLONS_LOURDS.folderName}", null)
        }

        // 3. Mots-clés administratifs (y compris photos de tickets/factures)
        val keywordHit = KEYWORD_FOLDERS.entries.firstOrNull { (kw, _) -> containsWord(normalized, kw) }
        if (keywordHit != null && kind in setOf(FileKind.PDF, FileKind.DOCUMENT, FileKind.IMAGE)) {
            return Result(kind, mime, FileCategory.ADMINISTRATIF,
                "$base/${FileCategory.ADMINISTRATIF.folderName}/${keywordHit.value}", keywordHit.key)
        }

        // 4. Type technique
        val category = defaultCategory(kind, file)
        val target = when (category) {
            FileCategory.MEDIAS -> "$base/${category.folderName}/${yearOf(file)}"
            FileCategory.ADMINISTRATIF -> "$base/${category.folderName}/Divers"
            else -> "$base/${category.folderName}"
        }
        return Result(kind, mime, category, target, null)
    }

    private fun defaultCategory(kind: FileKind, file: File): FileCategory = when (kind) {
        FileKind.APK -> FileCategory.INSTALLATEURS
        FileKind.ARCHIVE -> FileCategory.ARCHIVES
        FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO -> FileCategory.MEDIAS
        FileKind.PDF, FileKind.DOCUMENT -> FileCategory.ADMINISTRATIF
        FileKind.OTHER -> FileCategory.TELECHARGEMENTS
    }.let { cat ->
        // Un document "neutre" laissé dans Download reste dans le vrac
        if (cat == FileCategory.ADMINISTRATIF && file.parentFile?.name.equals("Download", true)) {
            FileCategory.TELECHARGEMENTS
        } else cat
    }

    private fun yearOf(file: File): Int =
        Calendar.getInstance().apply { timeInMillis = file.lastModified() }.get(Calendar.YEAR)

    /** Mot entier pour éviter les faux positifs ("avis" dans "Davis"). */
    private fun containsWord(text: String, word: String): Boolean =
        Regex("(^|[^a-z0-9])${Regex.escape(word)}([^a-z0-9]|$)").containsMatchIn(text)

    companion object {
        const val LARGE_FILE_BYTES = 200L * 1024 * 1024 // 200 Mo

        fun kindOf(ext: String): FileKind = FileKinds.fromExtension(ext)

        /** Mot-clé normalisé -> sous-dossier de Administratif. Ordre = priorité. */
        val KEYWORD_FOLDERS: Map<String, String> = linkedMapOf(
            "facture" to "Factures", "invoice" to "Factures", "quittance" to "Factures",
            "edf" to "Factures", "engie" to "Factures", "ticket" to "Factures",
            "recu" to "Factures", "receipt" to "Factures",
            "contrat" to "Contrats", "devis" to "Contrats", "assurance" to "Contrats",
            "mutuelle" to "Contrats", "bail" to "Contrats",
            "bulletin" to "Paie", "fiche de paie" to "Paie", "salaire" to "Paie",
            "releve" to "Banque", "rib" to "Banque",
            "impot" to "Impots-Social", "impots" to "Impots-Social", "caf" to "Impots-Social",
            "cpam" to "Impots-Social", "ameli" to "Impots-Social", "urssaf" to "Impots-Social",
            "attestation" to "Impots-Social", "avis" to "Impots-Social",
        )
    }
}
