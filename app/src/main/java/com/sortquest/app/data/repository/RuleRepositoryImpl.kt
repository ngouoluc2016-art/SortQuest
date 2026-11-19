package com.sortquest.app.data.repository

import com.sortquest.app.data.file.StoragePaths
import com.sortquest.app.data.local.AppDatabase
import com.sortquest.app.data.mapper.toDomain
import com.sortquest.app.data.mapper.toEntity
import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.model.FileNameNormalizer
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepositoryImpl @Inject constructor(
    db: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RuleRepository {

    private val dao = db.autoRuleDao()

    override fun observeRules(): Flow<List<SortRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveRule(rule: SortRule): Long = withContext(io) {
        // Dernier filet de sécurité côté data : le dossier doit rester dans la zone autorisée
        require(StoragePaths.isSafe(StoragePaths.resolve(rule.targetDir))) { "Dossier cible non autorisé" }
        val entity = rule.toEntity()
        if (rule.id == 0L) dao.insert(entity) else {
            dao.update(entity)
            rule.id
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(io) { dao.setEnabled(id, enabled) }

    override suspend fun deleteRule(id: Long) = withContext(io) { dao.delete(id) }

    override suspend fun previewMatches(rule: SortRule): Int = withContext(io) {
        StoragePaths.downloadsDir.listFiles().orEmpty().count { f ->
            f.isFile && !f.isHidden && rule.matches(
                FileNameNormalizer.normalize(f.nameWithoutExtension),
                f.extension.lowercase(),
                f.length(),
            )
        }
    }
}
