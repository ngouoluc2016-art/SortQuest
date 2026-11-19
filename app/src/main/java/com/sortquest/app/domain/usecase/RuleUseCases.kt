package com.sortquest.app.domain.usecase

import com.sortquest.app.domain.gamification.GameEvent
import com.sortquest.app.domain.model.AutoPilotProgress
import com.sortquest.app.domain.model.SortRule
import com.sortquest.app.domain.repository.AutoPilotRepository
import com.sortquest.app.domain.repository.ProgressRepository
import com.sortquest.app.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject

class ObserveRulesUseCase @Inject constructor(private val repo: RuleRepository) {
    operator fun invoke(): Flow<List<SortRule>> = repo.observeRules()
}

/** Erreurs de saisie lisibles, affichées telles quelles dans l'éditeur. */
class RuleValidationException(message: String) : IllegalArgumentException(message)

class SaveRuleUseCase @Inject constructor(private val repo: RuleRepository) {
    suspend operator fun invoke(rule: SortRule): Result<Long> = runCatching {
        val target = rule.targetDir.trim().trim('/')
        if (!rule.hasCondition) {
            throw RuleValidationException("Ajoute au moins une condition : mot-clé, extension ou taille.")
        }
        if (target.isEmpty()) throw RuleValidationException("Choisis un dossier de destination.")
        if (target.split('/').any { it == ".." || it == "." }) {
            throw RuleValidationException("Chemin de destination invalide.")
        }
        if (target.startsWith("Android", ignoreCase = true)) {
            throw RuleValidationException("Le dossier Android est réservé au système.")
        }
        val name = rule.name.trim().ifEmpty { defaultName(rule, target) }
        repo.saveRule(
            rule.copy(
                name = name,
                keyword = rule.keyword?.trim()?.ifEmpty { null },
                extensions = rule.extensions.map { it.trim().lowercase().removePrefix(".") }
                    .filter { it.isNotEmpty() }.toSet(),
                targetDir = target,
                createdAt = if (rule.createdAt == 0L) System.currentTimeMillis() else rule.createdAt,
            )
        )
    }

    private fun defaultName(rule: SortRule, target: String): String {
        val what = listOfNotNull(
            rule.keyword?.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
            rule.extensions.takeIf { it.isNotEmpty() }?.joinToString("/") { it.uppercase() },
        ).joinToString(" ").ifEmpty { "Fichiers lourds" }
        return "$what -> ${target.substringAfterLast('/')}"
    }
}

class ToggleRuleUseCase @Inject constructor(private val repo: RuleRepository) {
    suspend operator fun invoke(id: Long, enabled: Boolean) = repo.setEnabled(id, enabled)
}

class DeleteRuleUseCase @Inject constructor(private val repo: RuleRepository) {
    suspend operator fun invoke(id: Long) = repo.deleteRule(id)
}

class PreviewRuleUseCase @Inject constructor(private val repo: RuleRepository) {
    suspend operator fun invoke(rule: SortRule): Int =
        if (rule.hasCondition) repo.previewMatches(rule.copy(enabled = true)) else 0
}

/** Lance l'Auto-Pilot puis crédite l'XP correspondante en fin d'exécution. */
class RunAutoPilotUseCase @Inject constructor(
    private val autoPilotRepository: AutoPilotRepository,
    private val progressRepository: ProgressRepository,
) {
    operator fun invoke(): Flow<AutoPilotProgress> = flow {
        val batchId = UUID.randomUUID().toString()
        var last: AutoPilotProgress? = null
        autoPilotRepository.run(batchId).collect { progress ->
            last = progress
            emit(progress)
        }
        val moved = last?.moved ?: 0
        if (moved > 0) progressRepository.apply(GameEvent.AutoPilotRun(moved))
    }
}
