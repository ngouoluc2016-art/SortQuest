package com.sortquest.app.domain.model

import java.text.Normalizer

/** Normalisation commune des noms : minuscules, sans accents, séparateurs uniformisés. */
object FileNameNormalizer {
    private val MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[_\\-.]+")

    /** "Facture_EDF-Été" -> "facture edf ete" */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(SEPARATORS, " ")
            .trim()
}

/**
 * Règle Auto-Pilot. Toutes les conditions renseignées doivent être vraies (ET logique).
 * Exemple : keyword = "EDF", extensions = {pdf}, targetDir = "Documents/Factures".
 */
data class SortRule(
    val id: Long = 0,
    val name: String,
    val keyword: String? = null,
    val extensions: Set<String> = emptySet(),
    val minSizeBytes: Long = 0,
    /** Relatif à la racine du stockage, ex. "Documents/Factures". */
    val targetDir: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Long = 0,
    val lastRunAt: Long? = null,
    val filesMoved: Int = 0,
) {
    /** Une règle sans aucune condition déplacerait TOUT : interdit. */
    val hasCondition: Boolean
        get() = !keyword.isNullOrBlank() || extensions.isNotEmpty() || minSizeBytes > 0

    /** [normalizedName] = nom sans extension passé par [FileNameNormalizer]. */
    fun matches(normalizedName: String, extension: String, sizeBytes: Long): Boolean {
        if (!enabled || !hasCondition) return false
        val kwOk = keyword.isNullOrBlank() ||
            normalizedName.contains(FileNameNormalizer.normalize(keyword))
        val extOk = extensions.isEmpty() || extension.lowercase() in extensions
        val sizeOk = sizeBytes >= minSizeBytes
        return kwOk && extOk && sizeOk
    }
}
