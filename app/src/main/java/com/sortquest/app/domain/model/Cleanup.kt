package com.sortquest.app.domain.model

import kotlin.math.ceil

/** Groupe de fichiers au contenu strictement identique (même SHA-256). */
data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    /** Triés du plus ancien au plus récent : le premier est l'original proposé par défaut. */
    val files: List<FileItem>,
) {
    val copies: Int get() = files.size - 1
    /** Espace récupérable si on ne garde qu'un exemplaire. */
    val reclaimableBytes: Long get() = sizeBytes * copies
}

data class DuplicateScanProgress(
    val groupsChecked: Int,
    val groupsTotal: Int,
    val duplicatesFound: Int,
    val reclaimableBytes: Long,
    val done: Boolean = false,
)

/** Élément de la corbeille interne. */
data class TrashItem(
    val id: Long,
    val fileId: Long,
    val fileName: String,
    val originalPath: String,
    val trashPath: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val expiresAt: Long,
) {
    fun daysLeft(now: Long): Int = ceil((expiresAt - now).coerceAtLeast(0) / DAY_MS).toInt()

    /** 1 = vient d'être supprimé, 0 = suppression définitive imminente. */
    fun remainingFraction(now: Long): Float =
        ((expiresAt - now).toFloat() / (expiresAt - trashedAt).coerceAtLeast(1)).coerceIn(0f, 1f)

    private companion object { const val DAY_MS = 24.0 * 60 * 60 * 1000 }
}

/** Ligne d'historique telle qu'affichée dans l'onglet Auto-Pilot > Historique. */
data class HistoryEntry(val record: SortRecord, val undone: Boolean)
