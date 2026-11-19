package com.sortquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sortquest.app.domain.model.ActionSource
import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileStatus

/** Index des fichiers analysés (file de tri). Les enums sont stockés par leur nom. */
@Entity(
    tableName = "files",
    indices = [
        Index(value = ["path"], unique = true), Index("status"), Index("sizeBytes"), Index("contentHash"),
    ],
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: FileKind,
    val category: FileCategory,
    val suggestedTargetDir: String,
    val matchedKeyword: String?,
    /** Cache d'empreintes (scanner de doublons). Ignoré si le fichier a été modifié depuis. */
    val partialHash: String? = null,
    val contentHash: String? = null,
    val isDuplicate: Boolean = false,
    val status: FileStatus = FileStatus.PENDING,
    val skippedUntil: Long = 0,
    val scannedAt: Long,
)

/**
 * Journal de TOUTES les actions (manuelles et Auto-Pilot).
 * Contient tout le nécessaire pour annuler sans état en mémoire : origine, destination,
 * nom, taille, XP gagnée. L'annulation reste donc possible après fermeture de l'appli.
 */
@Entity(
    tableName = "sort_history",
    indices = [Index("fileId"), Index("timestamp"), Index("batchId"), Index("undone")],
)
data class SortHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long?,
    val fileName: String,
    val action: ActionType,
    val source: ActionSource,
    val ruleId: Long? = null,
    val batchId: String? = null,
    val fromPath: String,
    val toPath: String?,
    val sizeBytes: Long,
    val wasDuplicate: Boolean = false,
    val xpGained: Int = 0,
    val timestamp: Long,
    val undone: Boolean = false,
)

/** Corbeille interne : rétention 7 jours avant suppression définitive. */
@Entity(
    tableName = "trash",
    indices = [Index(value = ["trashPath"], unique = true), Index("fileId"), Index("expiresAt")],
)
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    /** Nom d'origine (le fichier peut avoir été renommé "x (1)" dans la corbeille). */
    val fileName: String,
    val originalPath: String,
    val trashPath: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val expiresAt: Long,
)

/** Règle Auto-Pilot : mot-clé ET extension ET taille minimale -> dossier cible. */
@Entity(tableName = "auto_rules", indices = [Index("enabled")])
data class AutoRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keyword: String?,
    /** Extensions séparées par des virgules, sans point : "pdf,docx". Vide = toutes. */
    val extensionsCsv: String,
    /** 0 = pas de condition de taille. */
    val minSizeBytes: Long,
    /** Relatif à la racine du stockage. */
    val targetDir: String,
    val enabled: Boolean,
    val priority: Int,
    val createdAt: Long,
    val lastRunAt: Long?,
    val filesMoved: Int,
)

/** Ligne unique (id = 0) : progression gamifiée. */
@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 0,
    val totalXp: Long = 0,
    /** Index de Level, recalculé depuis l'XP à chaque écriture (redondant mais lisible en SQL). */
    val levelIndex: Int = 0,
    val bytesFreed: Long = 0,
    val filesSorted: Int = 0,
    val duplicatesTrashed: Int = 0,
    val autoPilotMoves: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveEpochDay: Long = -1,
    val bestCombo: Int = 0,
    val badgesCsv: String = "",
    val celebratedLevelIndex: Int = 0,
    val seenBadgesCsv: String = "",
)
