package com.sortquest.app.domain.model

enum class SwipeDirection { RIGHT, LEFT, UP, DOWN }

enum class ActionType { MOVE, TRASH, VAULT, SKIP }

/** Origine d'une action : geste de l'utilisateur ou règle Auto-Pilot. */
enum class ActionSource { MANUAL, AUTO_PILOT }

/** Correspondance geste -> action métier. */
fun SwipeDirection.toAction(): ActionType = when (this) {
    SwipeDirection.RIGHT -> ActionType.MOVE   // valider le dossier suggéré
    SwipeDirection.LEFT -> ActionType.TRASH   // corbeille sécurisée 7 jours
    SwipeDirection.UP -> ActionType.VAULT     // coffre-fort / favoris
    SwipeDirection.DOWN -> ActionType.SKIP    // plus tard
}

/**
 * Trace persistée d'une action (table sort_history).
 * Suffisante à elle seule pour annuler, même après redémarrage de l'appli.
 */
data class SortRecord(
    val id: Long,
    /** Null si le fichier n'était pas indexé (cas possible en Auto-Pilot). */
    val fileId: Long?,
    val fileName: String,
    val action: ActionType,
    val source: ActionSource,
    val fromPath: String,
    val toPath: String?,
    val sizeBytes: Long,
    val wasDuplicate: Boolean,
    val xpGained: Int,
    val timestamp: Long,
    val ruleId: Long? = null,
    /** Identifiant commun à toutes les actions d'une même exécution Auto-Pilot. */
    val batchId: String? = null,
)

enum class ScanPhase { WALKING, DUPLICATES, DONE }

data class ScanProgress(
    val phase: ScanPhase,
    val scannedFiles: Int,
    val currentDir: String = "",
    val duplicatesFound: Int = 0,
    /** Phase DUPLICATES : groupes de même taille déjà vérifiés / total. */
    val groupsChecked: Int = 0,
    val groupsTotal: Int = 0,
)

data class PurgeResult(val deletedFiles: Int, val freedBytes: Long)

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val pendingCount: Int,
    val sortedCount: Int,
) {
    /** Santé du stockage (0..1) : 60 % avancement du tri, 40 % espace libre. */
    val health: Float
        get() {
            val total = pendingCount + sortedCount
            val sortedRatio = if (total == 0) 1f else sortedCount.toFloat() / total
            val freeRatio = if (totalBytes == 0L) 0f else freeBytes.toFloat() / totalBytes
            return (0.6f * sortedRatio + 0.4f * freeRatio).coerceIn(0f, 1f)
        }
}
