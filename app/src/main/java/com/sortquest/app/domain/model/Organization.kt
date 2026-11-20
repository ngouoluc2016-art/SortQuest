package com.sortquest.app.domain.model

/** Pictogramme d'un dossier thématique (traduit en icône Material côté UI). */
enum class FolderIcon {
    RECEIPT, BANK, TAX, HEALTH, CONTRACT, WORK, SCHOOL, HOME, VEHICLE, TRAVEL, IDENTITY,
    SCREENSHOT, PHOTO, VIDEO, MUSIC, BOOK, DOCUMENT, ARCHIVE, APP, CALENDAR, OTHER, CUSTOM,
}

/**
 * Dossier thématique de la gamme SortQuest. L'utilisateur choisit ceux qu'il veut utiliser.
 *
 * Règle de correspondance :
 *  - dossier "à mots-clés" (keywords non vide) : le nom du fichier OU de son dossier contient
 *    un des mots-clés (mot entier, sans accents), ET le type est accepté ([kinds]/[extensions]) ;
 *  - dossier "par type" (keywords vide) : le type ou l'extension du fichier est accepté.
 */
data class ThematicFolder(
    val id: String,
    val name: String,
    val description: String,
    /** Relatif à la racine du stockage, ex. "Documents/SortQuest/Administratif/Factures". */
    val relativePath: String,
    val icon: FolderIcon,
    val keywords: Set<String> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val kinds: Set<FileKind> = emptySet(),
    val enabled: Boolean = true,
    val builtIn: Boolean = true,
    /** Dossier indispensable (ex. Divers) : ne peut pas être désactivé. */
    val locked: Boolean = false,
    /** Range les fichiers dans un sous-dossier par année (photos, vidéos). */
    val splitByYear: Boolean = false,
    val sortOrder: Int,
) {
    val isKeywordFolder: Boolean get() = keywords.isNotEmpty()

    fun acceptsType(kind: FileKind, extension: String): Boolean = when {
        kinds.isEmpty() && extensions.isEmpty() -> true
        else -> kind in kinds || extension.lowercase() in extensions
    }
}

/** Réorganisations prêtes à l'emploi, au choix de l'utilisateur. */
enum class OrganizationPreset(val title: String, val description: String, val example: String) {
    BY_THEME(
        "Par thème",
        "Factures, banque, santé, travail, photos... Utilise ta gamme de dossiers thématiques.",
        "SortQuest/Administratif/Factures/Facture_EDF.pdf",
    ),
    BY_THEME_AND_YEAR(
        "Par thème et par année",
        "Comme « Par thème », avec un sous-dossier par année. Idéal pour l'administratif.",
        "SortQuest/Administratif/Factures/2026/Facture_EDF.pdf",
    ),
    BY_TYPE(
        "Par type de fichier",
        "Images, vidéos, audio, PDF, documents, archives, applications.",
        "SortQuest/Par type/PDF/Facture_EDF.pdf",
    ),
    BY_DATE(
        "Chronologique",
        "Un dossier par année puis par mois, selon la date du fichier.",
        "SortQuest/Chronologie/2026/11 - Novembre/Facture_EDF.pdf",
    ),
}

/** Ce que le moteur sait d'un fichier (aucun accès disque : calcul pur). */
data class FileFacts(
    val normalizedName: String,      // nom sans extension, passé par FileNameNormalizer
    val extension: String,
    val kind: FileKind,
    val lastModified: Long,
    val normalizedParent: String,    // nom du dossier parent, normalisé
)

/** Destination proposée pour un fichier. */
data class Placement(
    val relativeDir: String,
    /** Libellé court affiché à l'utilisateur, ex. "Factures" ou "Photos / 2026". */
    val label: String,
    val icon: FolderIcon,
    val folderId: String?,
    val matchedKeyword: String? = null,
)
