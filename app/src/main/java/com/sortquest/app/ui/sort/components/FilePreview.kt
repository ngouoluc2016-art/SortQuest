package com.sortquest.app.ui.sort.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sortquest.app.core.image.thumbnailModel
import com.sortquest.app.core.image.thumbnailRequest
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Aperçu adapté au type :
 *  - image, vidéo, PDF : miniature Coil (cache mémoire + cache disque WebP pour PDF/vidéo) ;
 *  - audio : pochette intégrée (sous-échantillonnée) ou titre/artiste/durée ;
 *  - autres : grande icône du type.
 * Tant que la miniature charge, l'icône du type est affichée : jamais de carte vide.
 */
@Composable
fun FilePreview(item: FileItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(Neon.SurfaceHigh, Neon.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        val model = remember(item.id, item.path, item.lastModified) { item.thumbnailModel() }
        when {
            model != null -> Thumbnail(item, model)
            item.kind == FileKind.AUDIO -> AudioInfo(item)
            else -> KindIcon(iconFor(item.kind), item.extension.uppercase())
        }
    }
}

@Composable
private fun Thumbnail(item: FileItem, model: Any) {
    val context = LocalContext.current
    var loaded by remember(model) { mutableStateOf(false) }
    val request = remember(model) { thumbnailRequest(context, model) }

    if (!loaded) KindIcon(iconFor(item.kind), item.extension.uppercase()) // repli pendant le chargement / en cas d'échec
    AsyncImage(
        model = request,
        contentDescription = item.name,
        // PDF : page entière visible ; photo/vidéo : remplit la carte
        contentScale = if (item.kind == FileKind.PDF) ContentScale.Fit else ContentScale.Crop,
        onSuccess = { loaded = true },
        modifier = Modifier.fillMaxSize().then(if (item.kind == FileKind.PDF) Modifier.padding(12.dp) else Modifier),
    )
    if (loaded && item.kind == FileKind.VIDEO) {
        Icon(Icons.Rounded.PlayCircle, null, tint = Neon.TextPrimary.copy(alpha = 0.85f), modifier = Modifier.size(64.dp))
    }
}

private data class AudioMeta(val title: String?, val artist: String?, val durationMs: Long?, val cover: Bitmap?)

@Composable
private fun AudioInfo(item: FileItem) {
    val meta by produceState<AudioMeta?>(initialValue = null, item.path) {
        value = withContext(Dispatchers.IO) { readAudioMeta(item.path) }
    }
    val m = meta
    if (m?.cover != null) {
        Image(m.cover.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.MusicNote, null, tint = Neon.Magenta, modifier = Modifier.size(72.dp))
            Text(
                m?.title ?: item.name, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            val details = listOfNotNull(m?.artist, m?.durationMs?.let(::formatDuration)).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        }
    }
}

private fun readAudioMeta(path: String): AudioMeta? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        AudioMeta(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            cover = retriever.embeddedPicture?.let { decodeSampled(it, maxPx = 512) },
        )
    } catch (e: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Décode une pochette en la réduisant AVANT décodage (une pochette 3000 px = 36 Mo sinon). */
private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    })
}

@Composable
private fun KindIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Neon.Cyan, modifier = Modifier.size(84.dp))
        Text(label.ifEmpty { "FICHIER" }, style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted)
    }
}

private fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.APK -> Icons.Rounded.Android
    FileKind.ARCHIVE -> Icons.Rounded.Archive
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.VIDEO -> Icons.Rounded.Movie
    else -> Icons.Rounded.InsertDriveFile
}

private fun formatDuration(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(min, sec)
}
