package com.sortquest.app.domain.organization

import com.sortquest.app.domain.model.FileFacts
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.OrganizationPreset
import com.sortquest.app.domain.model.ThematicFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OrganizationTest {

    private val nov2026 = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val folders = ThematicCatalog.defaults

    private fun facts(fileName: String, parent: String = "Download"): FileFacts {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return FileFacts(
            normalizedName = FileNameNormalizer.normalize(fileName.substringBeforeLast('.')),
            extension = ext,
            kind = FileKinds.fromExtension(ext),
            lastModified = nov2026,
            normalizedParent = FileNameNormalizer.normalize(parent),
        )
    }

    private fun theme(fileName: String, parent: String = "Download", list: List<ThematicFolder> = folders) =
        OrganizationEngine.place(facts(fileName, parent), OrganizationPreset.BY_THEME, list)

    // ------------------------------------------------------------ Par thème

    @Test fun `facture EDF dans Factures`() {
        val p = theme("Facture_EDF_octobre_2026.pdf")
        assertEquals("factures", p.folderId)
        assertEquals("Documents/SortQuest/Administratif/Factures", p.relativeDir)
    }

    @Test fun `le mot-cle le plus long gagne - releve de notes en Scolarite, pas en Banque`() {
        assertEquals("scolarite", theme("Releve_de_notes_S2.pdf").folderId)
        assertEquals("banque", theme("releve_compte_octobre.pdf").folderId)
    }

    @Test fun `contrat de travail en Travail, contrat d'assurance en Contrats`() {
        assertEquals("travail", theme("Contrat de travail CDI.pdf").folderId)
        assertEquals("contrats", theme("Contrat_assurance_habitation.pdf").folderId)
        assertEquals("travail", theme("bulletin_de_paie_septembre.pdf").folderId)
    }

    @Test fun `photo ticket de caisse scannee en Factures, photo normale en Photos par annee`() {
        assertEquals("factures", theme("ticket de caisse leclerc.jpg").folderId)
        val photo = theme("IMG_20260712_vacances.jpg")
        assertEquals("photos", photo.folderId)
        assertEquals("Documents/SortQuest/Photos/2026", photo.relativeDir)
    }

    @Test fun `capture d'ecran reconnue par le nom ou par le dossier parent`() {
        assertEquals("captures", theme("Screenshot_20261102.jpg").folderId)
        assertEquals("captures", theme("IMG_0001.png", parent = "Screenshots").folderId)
    }

    @Test fun `mot entier - Davis n'est pas un avis, cvs n'est pas un CV`() {
        assertEquals("documents", theme("Rapport Davis.pdf").folderId)
        assertEquals("documents", theme("export cvs.pdf").folderId)
    }

    @Test fun `un mot-cle admin ne s'applique pas a une archive ou une video`() {
        assertEquals("archives", theme("factures_2024.zip").folderId)
    }

    @Test fun `dossier desactive - repli sur le dossier suivant, puis Divers`() {
        val sansFactures = folders.map { if (it.id == "factures") it.copy(enabled = false) else it }
        assertEquals("documents", theme("Facture_EDF.pdf", list = sansFactures).folderId)
        val sansRien = folders.map { if (it.locked) it else it.copy(enabled = false) }
        assertEquals(ThematicCatalog.DIVERS_ID, theme("Facture_EDF.pdf", list = sansRien).folderId)
    }

    @Test fun `eBook et fichier inconnu`() {
        assertEquals("livres", theme("Les Miserables.epub").folderId)
        assertEquals("documents", theme("notes_reunion.txt").folderId) // .txt = document bureautique
        assertEquals(ThematicCatalog.DIVERS_ID, theme("firmware.xyz").folderId)
    }

    // ------------------------------------------------------------ Autres réorganisations

    @Test fun `par theme et par annee`() {
        val p = OrganizationEngine.place(facts("Facture_EDF.pdf"), OrganizationPreset.BY_THEME_AND_YEAR, folders)
        assertEquals("Documents/SortQuest/Administratif/Factures/2026", p.relativeDir)
    }

    @Test fun `par type`() {
        assertEquals("Documents/SortQuest/Par type/PDF",
            OrganizationEngine.place(facts("Facture.pdf"), OrganizationPreset.BY_TYPE, folders).relativeDir)
        assertEquals("Documents/SortQuest/Par type/Applications",
            OrganizationEngine.place(facts("app.apk"), OrganizationPreset.BY_TYPE, folders).relativeDir)
    }

    @Test fun `chronologique`() {
        assertEquals("Documents/SortQuest/Chronologie/2026/11 - Novembre",
            OrganizationEngine.place(facts("x.pdf"), OrganizationPreset.BY_DATE, folders).relativeDir)
    }

    @Test fun `chemin de dossier personnalise nettoye`() {
        assertEquals("Documents/SortQuest/Mes recettes", ThematicCatalog.customPath("  Mes/recettes: "))
    }

    // ------------------------------------------------------------ Protection

    @Test fun `fichiers et dossiers caches jamais touches`() {
        assertEquals(ProtectionReason.HIDDEN, ProtectedFilesPolicy.check("Download/.secret.pdf", 10))
        assertEquals(ProtectionReason.HIDDEN, ProtectedFilesPolicy.check("DCIM/.thumbnails/123.jpg", 10))
        assertEquals(ProtectionReason.HIDDEN, ProtectedFilesPolicy.check(".SortQuest_Trash/x.jpg", 10))
    }

    @Test fun `zones systeme, sons systeme, donnees d'application`() {
        assertEquals(ProtectionReason.SYSTEM_AREA, ProtectedFilesPolicy.check("Android/data/com.app/f.jpg", 10))
        assertEquals(ProtectionReason.SYSTEM_SOUNDS, ProtectedFilesPolicy.check("Music/Ringtones/ma_sonnerie.mp3", 10))
        assertEquals(ProtectionReason.SYSTEM_SOUNDS, ProtectedFilesPolicy.check("Notifications/bip.ogg", 10))
        assertEquals(ProtectionReason.APP_DATA, ProtectedFilesPolicy.check("Documents/MonAppli/cache/img.png", 10))
        assertEquals(ProtectionReason.APP_DATA, ProtectedFilesPolicy.check("Documents/WhatsApp/Databases/msg.crypt14", 10))
    }

    @Test fun `fichiers techniques et telechargements en cours`() {
        assertEquals(ProtectionReason.TECHNICAL, ProtectedFilesPolicy.check("Download/app.db", 10))
        assertEquals(ProtectionReason.TECHNICAL, ProtectedFilesPolicy.check("Documents/~\$rapport.docx", 10))
        assertEquals(ProtectionReason.IN_PROGRESS, ProtectedFilesPolicy.check("Download/film.mp4.crdownload", 10))
        assertEquals(ProtectionReason.IN_PROGRESS, ProtectedFilesPolicy.check("Download/fini.pdf", 10, modifiedAgoMs = 5_000))
        assertEquals(ProtectionReason.EMPTY, ProtectedFilesPolicy.check("Download/vide.pdf", 0))
    }

    @Test fun `fichiers utilisateur autorises`() {
        assertNull(ProtectedFilesPolicy.check("Download/Facture_EDF.pdf", 1_000))
        assertNull(ProtectedFilesPolicy.check("DCIM/Camera/IMG_1.jpg", 1_000))
        assertNull(ProtectedFilesPolicy.check("Pictures/Screenshots/Screenshot_1.png", 1_000))
        assertFalse(ProtectedFilesPolicy.isProtected("Documents/CV Gael.pdf", 1_000))
    }
}
