package com.sortquest.app.domain.repository

import com.sortquest.app.domain.model.AutoPilotProgress
import com.sortquest.app.domain.model.SortRule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun observeRules(): Flow<List<SortRule>>
    /** @return l'id de la règle créée ou mise à jour. */
    suspend fun saveRule(rule: SortRule): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun deleteRule(id: Long)
    /** Nombre de fichiers actuellement dans Download que la règle déplacerait. */
    suspend fun previewMatches(rule: SortRule): Int
}

interface AutoPilotRepository {
    /** Applique les règles actives au dossier Download. Flow froid, annulable. */
    fun run(batchId: String): Flow<AutoPilotProgress>
}
