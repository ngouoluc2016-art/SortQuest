package com.sortquest.app.domain.organization

import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FolderIcon
import com.sortquest.app.domain.model.ThematicFolder

/**
 * Gamme de dossiers thématiques livrée avec SortQuest.
 * L'ordre ([ThematicFolder.sortOrder]) est aussi l'ordre de priorité : le premier dossier
 * qui correspond l'emporte (ex. "bulletin de paie" -> Travail avant Scolarité).
 * Les mots-clés sont écrits sans accents et en minuscules (comparaison normalisée).
 */
object ThematicCatalog {
    const val BASE = "Documents/SortQuest"
    private const val ADMIN = "$BASE/Administratif"
    const val DIVERS_ID = "divers"

    private val ADMIN_KINDS = setOf(FileKind.PDF, FileKind.DOCUMENT, FileKind.IMAGE)

    val defaults: List<ThematicFolder> = listOf(
        ThematicFolder(
            "factures", "Factures", "Électricité, gaz, téléphone, internet, tickets et reçus",
            "$ADMIN/Factures", FolderIcon.RECEIPT,
            keywords = setOf("facture", "factures", "invoice", "quittance", "edf", "engie", "totalenergies",
                "echeancier", "avoir", "recu", "receipt", "ticket de caisse"),
            kinds = ADMIN_KINDS, sortOrder = 10,
        ),
        ThematicFolder(
            "banque", "Banque", "Relevés, RIB, prêts, épargne",
            "$ADMIN/Banque", FolderIcon.BANK,
            keywords = setOf("releve", "releves", "rib", "iban", "virement", "banque", "bancaire", "pret",
                "credit", "epargne", "livret a"),
            kinds = ADMIN_KINDS, sortOrder = 20,
        ),
        ThematicFolder(
            "travail", "Travail", "Bulletins de paie, contrats de travail, CV, attestations employeur",
            "$ADMIN/Travail", FolderIcon.WORK,
            keywords = setOf("bulletin de paie", "fiche de paie", "paie", "salaire", "cv", "curriculum",
                "lettre de motivation", "contrat de travail", "attestation employeur", "france travail",
                "pole emploi", "entretien annuel"),
            kinds = ADMIN_KINDS, sortOrder = 30,
        ),
        ThematicFolder(
            "impots", "Impôts & Social", "Impôts, CAF, URSSAF, retraite, attestations",
            "$ADMIN/Impots et social", FolderIcon.TAX,
            keywords = setOf("impot", "impots", "avis d imposition", "taxe", "caf", "urssaf", "retraite",
                "cpam", "ameli", "attestation", "declaration"),
            kinds = ADMIN_KINDS, sortOrder = 40,
        ),
        ThematicFolder(
            "sante", "Santé", "Ordonnances, analyses, mutuelle, comptes rendus",
            "$ADMIN/Sante", FolderIcon.HEALTH,
            keywords = setOf("ordonnance", "analyse", "analyses", "radio", "medecin", "mutuelle", "sante",
                "vaccin", "vaccination", "hopital", "laboratoire", "compte rendu medical"),
            kinds = ADMIN_KINDS, sortOrder = 50,
        ),
        ThematicFolder(
            "contrats", "Contrats & Assurances", "Contrats, assurances, devis, garanties",
            "$ADMIN/Contrats et assurances", FolderIcon.CONTRACT,
            keywords = setOf("contrat", "assurance", "devis", "avenant", "conditions generales", "sinistre",
                "garantie", "resiliation"),
            kinds = ADMIN_KINDS, sortOrder = 60,
        ),
        ThematicFolder(
            "logement", "Logement", "Bail, loyer, état des lieux, notaire, copropriété",
            "$ADMIN/Logement", FolderIcon.HOME,
            keywords = setOf("bail", "loyer", "etat des lieux", "notaire", "copropriete", "vefa", "acte de vente",
                "syndic", "taxe fonciere"),
            kinds = ADMIN_KINDS, sortOrder = 70,
        ),
        ThematicFolder(
            "scolarite", "Scolarité & Formation", "Diplômes, certificats, inscriptions, cours",
            "$ADMIN/Scolarite et formation", FolderIcon.SCHOOL,
            keywords = setOf("diplome", "certificat de scolarite", "scolarite", "inscription", "ecole", "college",
                "lycee", "universite", "formation", "cours", "releve de notes", "bulletin scolaire", "rncp"),
            kinds = ADMIN_KINDS, sortOrder = 80,
        ),
        ThematicFolder(
            "identite", "Identité", "Carte d'identité, passeport, actes, justificatifs de domicile",
            "$ADMIN/Identite", FolderIcon.IDENTITY,
            keywords = setOf("carte d identite", "cni", "passeport", "acte de naissance", "livret de famille",
                "justificatif de domicile", "titre de sejour"),
            kinds = ADMIN_KINDS, sortOrder = 90,
        ),
        ThematicFolder(
            "vehicule", "Véhicule", "Carte grise, permis, entretien, contrôle technique",
            "$ADMIN/Vehicule", FolderIcon.VEHICLE,
            keywords = setOf("carte grise", "certificat d immatriculation", "permis", "controle technique",
                "entretien", "vidange", "garage"),
            kinds = ADMIN_KINDS, sortOrder = 100,
        ),
        ThematicFolder(
            "voyages", "Voyages", "Billets, réservations, cartes d'embarquement",
            "$BASE/Voyages", FolderIcon.TRAVEL,
            keywords = setOf("billet", "boarding pass", "carte d embarquement", "reservation", "hotel", "sncf",
                "itineraire", "vol", "e ticket"),
            kinds = ADMIN_KINDS, sortOrder = 110,
        ),
        ThematicFolder(
            "captures", "Captures d'écran", "Captures et enregistrements d'écran",
            "$BASE/Captures d'ecran", FolderIcon.SCREENSHOT,
            keywords = setOf("screenshot", "screenshots", "capture d ecran", "captures d ecran", "screen recording",
                "screenrecord"),
            kinds = setOf(FileKind.IMAGE, FileKind.VIDEO), splitByYear = true, sortOrder = 120,
        ),
        ThematicFolder(
            "photos", "Photos", "Photos, classées par année",
            "$BASE/Photos", FolderIcon.PHOTO, kinds = setOf(FileKind.IMAGE), splitByYear = true, sortOrder = 200,
        ),
        ThematicFolder(
            "videos", "Vidéos", "Vidéos, classées par année",
            "$BASE/Videos", FolderIcon.VIDEO, kinds = setOf(FileKind.VIDEO), splitByYear = true, sortOrder = 210,
        ),
        ThematicFolder(
            "musique", "Musique & Audio", "Musique, podcasts, mémos vocaux",
            "$BASE/Musique et audio", FolderIcon.MUSIC, kinds = setOf(FileKind.AUDIO), sortOrder = 220,
        ),
        ThematicFolder(
            "livres", "Livres & eBooks", "EPUB, MOBI, bandes dessinées",
            "$BASE/Livres", FolderIcon.BOOK, extensions = setOf("epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr", "djvu"),
            sortOrder = 230,
        ),
        ThematicFolder(
            "documents", "Documents", "PDF et documents bureautiques sans thème reconnu",
            "$BASE/Documents", FolderIcon.DOCUMENT, kinds = setOf(FileKind.PDF, FileKind.DOCUMENT), sortOrder = 240,
        ),
        ThematicFolder(
            "archives", "Archives", "ZIP, RAR, 7z...",
            "$BASE/Archives", FolderIcon.ARCHIVE, kinds = setOf(FileKind.ARCHIVE), sortOrder = 250,
        ),
        ThematicFolder(
            "applications", "Installateurs (APK)", "Fichiers d'installation d'applications",
            "$BASE/Installateurs", FolderIcon.APP, kinds = setOf(FileKind.APK), sortOrder = 260,
        ),
        ThematicFolder(
            DIVERS_ID, "Divers", "Tout ce qui ne correspond à aucun autre dossier",
            "$BASE/Divers", FolderIcon.OTHER, locked = true, sortOrder = 1_000,
        ),
    )

    /** Chemin sûr pour un dossier personnalisé créé par l'utilisateur. */
    fun customPath(name: String): String {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim('.', ' ')
        return "$BASE/${clean.ifEmpty { "Mon dossier" }}"
    }
}
