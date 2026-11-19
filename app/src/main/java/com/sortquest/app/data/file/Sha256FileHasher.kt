package com.sortquest.app.data.file

import com.sortquest.app.di.IoDispatcher
import com.sortquest.app.domain.repository.FileHasher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empreintes SHA-256, mémoire constante (un tampon de 64 Ko), toujours sur Dispatchers.IO.
 * Le calcul complet vérifie l'annulation entre chaque bloc : un gros fichier ne bloque jamais
 * l'arrêt du scan (fermeture de l'appli, worker annulé).
 */
@Singleton
class Sha256FileHasher @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : FileHasher {

    override suspend fun partialHash(path: String): String = withContext(io) {
        val file = File(path)
        val size = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(size.toString().toByteArray()) // la taille fait partie de l'empreinte
        val buffer = ByteArray(FileHasher.CHUNK_BYTES)
        RandomAccessFile(file, "r").use { raf ->
            // 64 premiers Ko
            val headLen = minOf(size, FileHasher.CHUNK_BYTES.toLong()).toInt()
            raf.readFully(buffer, 0, headLen) // readFully : jamais de lecture partielle silencieuse
            digest.update(buffer, 0, headLen)
            // 64 derniers Ko (si le fichier dépasse 64 Ko)
            if (size > FileHasher.CHUNK_BYTES) {
                val tailStart = maxOf(FileHasher.CHUNK_BYTES.toLong(), size - FileHasher.CHUNK_BYTES)
                raf.seek(tailStart)
                val tailLen = (size - tailStart).toInt()
                raf.readFully(buffer, 0, tailLen)
                digest.update(buffer, 0, tailLen)
            }
        }
        digest.digest().toHex()
    }

    override suspend fun fullHash(path: String): String = withContext(io) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FileHasher.CHUNK_BYTES)
        File(path).inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
