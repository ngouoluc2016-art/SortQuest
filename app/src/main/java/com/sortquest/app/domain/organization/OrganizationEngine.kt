package com.sortquest.app.domain.organization

import com.sortquest.app.domain.model.FileFacts
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.Placement
import com.sortquest.app.domain.model.ThematicFolder
import java.time.Instant
import java.time.ZoneId

/**
 * Calcule la destination d'un fichier selon la réorganisation choisie.
 * Fonction pure : mêmes entrées -> même résultat, testable sans Android.
 */
object OrganizationEngine {

    private const val BASE = ThematicCatalog.BASE
    private val MONTHS = listOf(
        "Janvier", "Fevrier", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Aout", "Septembre", "Octobre", "Novembre", "Decembre",
    )

    fun place(facts: FileFacts, preset: OrganizationPreset, folders: List<ThematicFolder>): Placement =
        when (preset) {
            OrganizationPreset.BY_THEME -> byTheme(facts, folders, yearForAll = false)
            OrganizationPreset.BY_THEME_AND_YEAR -> byTheme(facts, folders, yearForAll = true)
            OrganizationPreset.BY_TYPE -> byType(facts)
            OrganizationPreset.BY_DATE -> byDate(facts)
        }

    // ------------------------------------------------------------------ Par thème

    private fun byTheme(f: FileFacts, folders: List<ThematicFolder>, yearForAll: Boolean): Placement {
        val active = folders.filter { it.enabled || it.locked }.sortedBy { it.sortOrder }
        val year = yearOf(f.lastModified)

        // 1. Dossiers à mots-clés : le mot-clé le plus long gagne, l'ordre départage les égalités
        var best: Pair<ThematicFolder, String>? = null
        for (folder in active.filter { it.isKeywordFolder && it.acceptsType(f.kind, f.extension) }) {
            val kw = folder.keywords
                .filter { containsWord(f.normalizedName, it) || containsWord(f.normalizedParent, it) }
                .maxByOrNull { it.length } ?: continue
            if (best == null || kw.length > best.second.length) best = folder to kw
        }
        best?.let { (folder, kw) -> return placementIn(folder, year, yearForAll, kw) }

        // 2. Dossiers par type (photos, vidéos, documents...)
        active.firstOrNull { !it.isKeywordFolder && !it.locked && it.acceptsType(f.kind, f.extension) && (it.kinds.isNotEmpty() || it.extensions.isNotEmpty()) }
            ?.let { return placementIn(it, year, yearForAll, null) }

        // 3. Repli : Divers (toujours actif)
        val divers = folders.firstOrNull { it.id == ThematicCatalog.DIVERS_ID }
            ?: ThematicCatalog.defaults.first { it.id == ThematicCatalog.DIVERS_ID }
        return placementIn(divers, year, yearForAll, null)
    }

    private fun placementIn(folder: ThematicFolder, year: Int, yearForAll: Boolean, kw: String?): Placement {
        val withYear = yearForAll || folder.splitByYear
        return Placement(
            relativeDir = if (withYear) "${folder.relativePath}/$year" else folder.relativePath,
            label = if (withYear) "${folder.name} / $year" else folder.name,
            icon = folder.icon,
            folderId = folder.id,
            matchedKeyword = kw,
        )
    }

    // ------------------------------------------------------------------ Par type

    private val EBOOKS = setOf("epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr", "djvu")

    private fun byType(f: FileFacts): Placement {
        val (name, icon) = when {
            f.extension in EBOOKS -> "Livres" to FolderIcon.BOOK
            else -> when (f.kind) {
                FileKind.IMAGE -> "Images" to FolderIcon.PHOTO
                FileKind.VIDEO -> "Videos" to FolderIcon.VIDEO
                FileKind.AUDIO -> "Audio" to FolderIcon.MUSIC
                FileKind.PDF -> "PDF" to FolderIcon.DOCUMENT
                FileKind.DOCUMENT -> "Documents" to FolderIcon.DOCUMENT
                FileKind.ARCHIVE -> "Archives" to FolderIcon.ARCHIVE
                FileKind.APK -> "Applications" to FolderIcon.APP
                FileKind.OTHER -> "Autres" to FolderIcon.OTHER
            }
        }
        return Placement("$BASE/Par type/$name", name, icon, folderId = null)
    }

    // ------------------------------------------------------------------ Chronologique

    private fun byDate(f: FileFacts): Placement {
        val date = Instant.ofEpochMilli(f.lastModified).atZone(ZoneId.systemDefault()).toLocalDate()
        val month = "%02d - %s".format(date.monthValue, MONTHS[date.monthValue - 1])
        return Placement("$BASE/Chronologie/${date.year}/$month", "${date.year} / $month", FolderIcon.CALENDAR, folderId = null)
    }

    // ------------------------------------------------------------------ Outils

    private fun yearOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).year

    /** Mot ou expression entière : "avis" ne correspond pas à "Davis", "cv" pas à "cvs". */
    fun containsWord(text: String, keyword: String): Boolean {
        if (text.isEmpty()) return false
        val k = FileNameNormalizer.normalize(keyword)
        if (k.isEmpty()) return false
        var from = 0
        while (true) {
            val i = text.indexOf(k, from)
            if (i < 0) return false
            val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
            val afterOk = i + k.length == text.length || !text[i + k.length].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            from = i + 1
        }
    }
}
