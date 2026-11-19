package com.sortquest.app.domain.usecase

import com.sortquest.app.domain.model.ActionType
import com.sortquest.app.domain.model.DuplicateGroup
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileStatus
import com.sortquest.app.domain.model.HistoryEntry
import com.sortquest.app.domain.model.ScanProgress
import com.sortquest.app.domain.model.SortRecord
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.repository.FileHasher
import com.sortquest.app.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie l'algorithme en 3 passes sans disque : contenus simulés en mémoire. */
class DuplicateScannerUseCaseTest {

    /** "Disque" simulé : chemin -> contenu. */
    private val big = 200L * 1024 // > 128 Ko : le hash complet est nécessaire
    private val contents = mapOf(
        "/a/photo.jpg" to "AAAA-identique",
        "/b/photo (copie).jpg" to "AAAA-identique",   // vrai doublon de photo.jpg
        "/c/autre.jpg" to "AAAA-differentMilieu",     // même début/fin, milieu différent
        "/d/seul.pdf" to "contenu-unique",            // taille unique : jamais lu
    )

    private fun file(id: Long, path: String, size: Long, modified: Long) = FileItem(
        id = id, path = path, name = path.substringAfterLast('/'), extension = "jpg", mimeType = null,
        sizeBytes = size, lastModified = modified, kind = FileKind.IMAGE, category = FileCategory.MEDIAS,
        suggestedTargetDir = "", matchedKeyword = null, isDuplicate = false, status = FileStatus.PENDING,
    )

    private val files = listOf(
        file(1, "/a/photo.jpg", big, modified = 100),           // le plus ancien = original
        file(2, "/b/photo (copie).jpg", big, modified = 200),
        file(3, "/c/autre.jpg", big, modified = 300),
        file(4, "/d/seul.pdf", 999, modified = 400),
    )

    private class CountingHasher(private val contents: Map<String, String>) : FileHasher {
        val partialCalls = mutableListOf<String>()
        val fullCalls = mutableListOf<String>()
        // Empreinte partielle : seulement début + fin => "autre.jpg" collisionne volontairement
        override suspend fun partialHash(path: String) =
            contents.getValue(path).let { it.take(4) + it.takeLast(2) }.also { partialCalls += path }
        override suspend fun fullHash(path: String) = contents.getValue(path).also { fullCalls += path }
    }

    private class FakeRepo(private val files: List<FileItem>) : FileRepository {
        var marked: List<Long> = emptyList()
        override suspend fun filesGroupedBySize(maxSizeBytes: Long) =
            files.filter { it.sizeBytes <= maxSizeBytes }.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        override suspend fun saveHashes(fileId: Long, partialHash: String?, contentHash: String?) = Unit
        override suspend fun replaceDuplicateMarks(copyIds: List<Long>) { marked = copyIds }

        // Non utilisées par le scanner
        override fun indexStorage(): Flow<ScanProgress> = emptyFlow()
        override fun observeQueue(limit: Int): Flow<List<FileItem>> = emptyFlow()
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun applyAction(item: FileItem, action: ActionType, customTargetDir: String?): Result<SortRecord> = error("inutilisé")
        override suspend fun recordXp(historyId: Long, xp: Int) = Unit
        override fun observeCanUndo(): Flow<Boolean> = emptyFlow()
        override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> = emptyFlow()
        override suspend fun undoLast(): Result<SortRecord?> = error("inutilisé")
        override suspend fun undo(historyId: Long): Result<SortRecord?> = error("inutilisé")
        override suspend fun undoBatch(batchId: String): Result<List<SortRecord>> = error("inutilisé")
        override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> = emptyFlow()
        override fun observeLargestFiles(minSizeBytes: Long, limit: Int): Flow<List<FileItem>> = emptyFlow()
        override suspend fun storageStats(): StorageStats = error("inutilisé")
    }

    @Test fun `trouve le vrai doublon, garde le plus ancien, ignore le faux positif partiel`() = runBlocking {
        val repo = FakeRepo(files)
        val hasher = CountingHasher(contents)
        val progress = DuplicateScannerUseCase(repo, hasher)().toList()

        assertEquals(listOf(2L), repo.marked)                  // seule la copie est marquée
        assertEquals(1, progress.last().duplicatesFound)
        assertEquals(big, progress.last().reclaimableBytes)
        assertTrue(progress.last().done)
    }

    @Test fun `passe 1 - un fichier de taille unique n'est jamais lu`() = runBlocking {
        val hasher = CountingHasher(contents)
        DuplicateScannerUseCase(FakeRepo(files), hasher)().toList()
        assertTrue("/d/seul.pdf" !in hasher.partialCalls && "/d/seul.pdf" !in hasher.fullCalls)
    }

    @Test fun `passe 3 - le hash complet ne concerne que les survivants de la passe 2`() = runBlocking {
        val distinct = contents + ("/c/autre.jpg" to "ZZZZ-tout-autre-xx") // ne collisionne plus en partiel
        val hasher = CountingHasher(distinct)
        DuplicateScannerUseCase(FakeRepo(files), hasher)().toList()
        assertEquals(3, hasher.partialCalls.size)
        assertEquals(setOf("/a/photo.jpg", "/b/photo (copie).jpg"), hasher.fullCalls.toSet())
    }

    @Test fun `l'exemplaire deja range est garde, meme s'il est plus recent`() = runBlocking {
        // Cas réel vu sur émulateur : l'utilisateur classe une photo, sa copie reste dans Download
        val sortedCopy = files.take(2).let { (a, b) -> listOf(a, b.copy(status = FileStatus.SORTED)) }
        val repo = FakeRepo(sortedCopy)
        DuplicateScannerUseCase(repo, CountingHasher(contents))().toList()
        assertEquals(listOf(1L), repo.marked) // la copie NON rangée est proposée à la suppression
    }

    @Test fun `petit fichier - pas de hash complet, l'empreinte partielle couvre tout`() = runBlocking {
        val small = files.take(2).map { it.copy(sizeBytes = 1_000) }
        val hasher = CountingHasher(contents)
        val repo = FakeRepo(small)
        DuplicateScannerUseCase(repo, hasher)().toList()
        assertEquals(0, hasher.fullCalls.size)
        assertEquals(listOf(2L), repo.marked)
    }
}
