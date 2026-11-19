package com.sortquest.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileKind
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Largeur des miniatures en pixels : assez pour une carte plein écran, ~60 Ko en WebP. */
const val THUMB_PX = 720

enum class ThumbnailType { PDF, VIDEO }

/** Modèle Coil : "la miniature de ce fichier local". [lastModified] invalide le cache si le fichier change. */
data class LocalThumbnail(val path: String, val lastModified: Long, val type: ThumbnailType)

/** Modèle à passer à Coil pour l'aperçu d'un fichier (null = pas d'aperçu image). */
fun FileItem.thumbnailModel(): Any? = when (kind) {
    FileKind.IMAGE -> File(path)                                         // décodage direct, sous-échantillonné
    FileKind.VIDEO -> LocalThumbnail(path, lastModified, ThumbnailType.VIDEO)
    FileKind.PDF -> LocalThumbnail(path, lastModified, ThumbnailType.PDF)
    else -> null
}

/** Requête identique pour l'affichage et le préchargement => même clé de cache mémoire. */
fun thumbnailRequest(context: Context, model: Any): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        .size(THUMB_PX)
        .allowHardware(true) // bitmaps en mémoire GPU : pas de pression sur le tas Java
        .build()

/** Précharge les miniatures des cartes suivantes : elles sont prêtes avant d'arriver à l'écran. */
fun ImageLoader.prefetch(context: Context, items: List<FileItem>) {
    items.forEach { item -> item.thumbnailModel()?.let { enqueue(thumbnailRequest(context, it)) } }
}

class LocalThumbnailKeyer : Keyer<LocalThumbnail> {
    override fun key(data: LocalThumbnail, options: Options): String =
        "${data.type}:${data.path}:${data.lastModified}"
}

/**
 * Fetcher Coil : produit la miniature (1re page PDF via PdfRenderer, image à 1 s pour une vidéo),
 * l'enregistre en WebP dans un cache disque dédié, puis la confie au décodeur standard de Coil
 * (qui gère le sous-échantillonnage et le cache mémoire).
 *
 * - Au 2e affichage (même après redémarrage) : lecture directe du WebP, aucun rendu.
 * - Au plus 2 rendus simultanés (sémaphore) : pas de pic CPU/mémoire en faisant défiler vite.
 * - Écriture atomique (fichier temporaire puis renommage) : jamais de miniature à moitié écrite.
 */
class LocalThumbnailFetcher(
    private val data: LocalThumbnail,
    private val cache: ThumbnailDiskCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val cached = cache.fileFor(data)
        val dataSource = if (cached.isFile) {
            DataSource.DISK
        } else {
            renderPermits.withPermit {
                if (!cached.isFile) { // un autre appel l'a peut-être déjà produit
                    val bitmap = when (data.type) {
                        ThumbnailType.PDF -> renderPdfFirstPage(File(data.path))
                        ThumbnailType.VIDEO -> extractVideoFrame(data.path)
                    } ?: throw IOException("Aperçu impossible : ${data.path}")
                    cache.write(cached, bitmap)
                }
            }
            DataSource.DISK
        }
        return SourceResult(
            source = ImageSource(file = cached.toOkioPath(), diskCacheKey = null),
            mimeType = "image/webp",
            dataSource = dataSource,
        )
    }

    class Factory(context: Context) : Fetcher.Factory<LocalThumbnail> {
        private val cache = ThumbnailDiskCache(File(context.cacheDir, "thumbnails"))
        override fun create(data: LocalThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalThumbnailFetcher(data, cache)
    }

    private companion object {
        val renderPermits = Semaphore(2)

        /** PdfRenderer natif. PDF chiffré ou corrompu -> null (l'UI affiche une icône). */
        fun renderPdfFirstPage(file: File): Bitmap? = runCatching<Bitmap?> {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val scale = THUMB_PX.toFloat() / page.width
                        val height = (page.height * scale).toInt().coerceIn(1, THUMB_PX * 2)
                        Bitmap.createBitmap(THUMB_PX, height, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(Color.WHITE) // fond blanc (sinon transparent)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull()

        fun extractVideoFrame(path: String): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(path)
                val timeUs = 1_000_000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_PX, THUMB_PX)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}

/** Cache disque des miniatures, plafonné en taille (les plus anciennes sont supprimées en premier). */
class ThumbnailDiskCache(private val dir: File, private val maxBytes: Long = 48L * 1024 * 1024) {

    private val writes = AtomicInteger(0)

    fun fileFor(data: LocalThumbnail): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("${data.type}|${data.path}|${data.lastModified}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.webp")
    }

    fun write(target: File, bitmap: Bitmap) {
        dir.mkdirs()
        val tmp = File(dir, "${target.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else Bitmap.CompressFormat.WEBP
                bitmap.compress(format, 80, out)
            }
            if (!tmp.renameTo(target)) throw IOException("Écriture de la miniature impossible")
        } finally {
            tmp.delete()
            bitmap.recycle() // la version WebP suffit : on libère tout de suite la mémoire native
        }
        if (writes.incrementAndGet() % TRIM_EVERY == 0) trim()
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }

    private companion object { const val TRIM_EVERY = 25 }
}
