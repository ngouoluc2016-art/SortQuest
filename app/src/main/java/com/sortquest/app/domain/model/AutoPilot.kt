package com.sortquest.app.domain.model

/** Avancement d'une exécution Auto-Pilot (émis fichier par fichier). */
data class AutoPilotProgress(
    val batchId: String,
    val processed: Int,
    val total: Int,
    val moved: Int,
    val bytesMoved: Long,
    val errors: Int = 0,
    val currentFile: String? = null,
    val done: Boolean = false,
) {
    val fraction: Float get() = if (total == 0) 1f else processed.toFloat() / total
}
