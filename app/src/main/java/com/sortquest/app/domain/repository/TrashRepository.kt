package com.sortquest.app.domain.repository

import com.sortquest.app.domain.model.PurgeResult
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashRepository {
    fun observeTrash(): Flow<List<TrashItem>>

    /**
     * Remet le fichier à son emplacement d'origine ("nom (1).ext" si le nom a été repris).
     * @return la ligne d'historique annulée (pour retirer l'XP), ou null si aucune.
     */
    suspend fun restore(trashId: Long): Result<SortRecord?>

    suspend fun deleteForever(trashId: Long): Result<Unit>
    suspend fun emptyTrash(): PurgeResult

    /** Suppression définitive des éléments de plus de 7 jours (TrashCleanupWorker). */
    suspend fun purgeExpired(): PurgeResult

    /**
     * Fichiers présents dans le dossier corbeille mais inconnus de la base (ex. réinstallation
     * de l'appli) : ils redeviennent visibles et restaurables, avec un nouveau délai de 7 jours.
     */
    suspend fun adoptOrphans(): Int
}
