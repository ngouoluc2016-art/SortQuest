package com.sortquest.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sortquest.app.core.files.FileOpener
import com.sortquest.app.core.image.LocalThumbnail
import com.sortquest.app.core.image.ThumbnailType
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.ui.components.FileKindIcon
import com.sortquest.app.ui.components.rememberBytesFormatter
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FileInfo(val exists: Boolean, val size: Long, val modified: Long)

/** Consulter un fichier depuis SortQuest, avant comme après son déplacement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val formatBytes = rememberBytesFormatter()
    val file = remember(path) { File(path) }
    val ext = file.extension.lowercase()
    val kind = remember(ext) { FileKinds.fromExtension(ext) }
    val info by produceState<FileInfo?>(null, path) {
        value = withContext(Dispatchers.IO) { FileInfo(file.isFile, file.length(), file.lastModified()) }
    }
    val openExternal = {
        if (!FileOpener.open(context, path)) scope.launch { snackbar.showSnackbar("Aucune application ne sait ouvrir ce type de fichier") }
    }

    Box(Modifier.fillMaxSize().background(Neon.Background)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { if (!FileOpener.share(context, path)) scope.launch { snackbar.showSnackbar("Partage impossible") } }) {
                        Icon(Icons.Rounded.Share, "Partager")
                    }
                    IconButton(onClick = { openExternal() }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Ouvrir avec une autre application") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Neon.Background),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    info == null -> CircularProgressIndicator(color = Neon.Cyan)
                    info?.exists != true -> Text("Ce fichier n'existe plus à cet emplacement.", color = Neon.TextMuted)
                    kind == FileKind.IMAGE -> ZoomableImage(file)
                    kind == FileKind.PDF -> PdfPages(file)
                    ext in TEXT_EXT -> TextContent(file)
                    kind == FileKind.VIDEO -> MediaPreview(file, onPlay = { openExternal() })
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FileKindIcon(ext, size = 96.dp)
                        Text("Aperçu non disponible pour ce type de fichier", color = Neon.TextMuted)
                        Button(onClick = { openExternal() }, colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background)) {
                            Text(if (kind == FileKind.AUDIO) "Écouter avec…" else "Ouvrir avec…")
                        }
                    }
                }
            }

            // Fiche du fichier : où il se trouve maintenant
            info?.takeIf { it.exists }?.let { i ->
                Column(
                    Modifier.fillMaxWidth().background(Neon.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(statusOf(path), style = MaterialTheme.typography.labelLarge, color = statusColor(path))
                    Text(locationOf(path), style = MaterialTheme.typography.bodySmall, color = Neon.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatBytes(i.size)} · modifié le ${DATE.format(Date(i.modified))}", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun statusOf(path: String) = when {
    "/.SortQuest_Trash/" in path -> "Dans la corbeille (supprimé définitivement après 7 jours)"
    "/Documents/SortQuest/" in path -> "Rangé par SortQuest"
    else -> "À trier : emplacement d'origine"
}

private fun statusColor(path: String) = when {
    "/.SortQuest_Trash/" in path -> Neon.Red
    "/Documents/SortQuest/" in path -> Neon.Lime
    else -> Neon.Amber
}

private fun locationOf(path: String) =
    "Emplacement : " + path.substringBeforeLast('/').substringAfter("/storage/emulated/0/", path.substringBeforeLast('/'))

/** Image plein écran : pincer pour zoomer, faire glisser une fois zoomé, double-toucher pour basculer. */
@Composable
private fun ZoomableImage(file: File) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = remember(file) { ImageRequest.Builder(context).data(file).size(2048).build() },
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    )
}

/** Vidéo : image d'aperçu + lecture dans le lecteur de l'utilisateur. */
@Composable
private fun MediaPreview(file: File, onPlay: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = remember(file) { ImageRequest.Builder(context).data(LocalThumbnail(file.absolutePath, file.lastModified(), ThumbnailType.VIDEO)).build() },
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onPlay, modifier = Modifier.size(96.dp)) {
            Icon(Icons.Rounded.PlayCircle, "Lire la vidéo", tint = Neon.TextPrimary, modifier = Modifier.size(88.dp))
        }
    }
}

// ------------------------------------------------------------------ PDF multipage

/**
 * Document PDF ouvert UNE fois et partagé par toutes les pages visibles.
 * PdfRenderer n'est pas thread-safe : un verrou sérialise les rendus. Fermé en quittant l'écran.
 */
private class PdfDocument(private val file: File) {
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var closed = false

    private fun open(): PdfRenderer? {
        if (closed) return null
        renderer?.let { return it }
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).let { p ->
                pfd = p
                PdfRenderer(p).also { renderer = it }
            }
        }.getOrNull()
    }

    suspend fun pageCount(): Int = withContext(Dispatchers.IO) { mutex.withLock { open()?.pageCount ?: 0 } }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = open() ?: return@withLock null
            runCatching {
                r.openPage(index).use { page ->
                    val w = widthPx.coerceIn(400, 1600)
                    val h = (page.height * (w.toFloat() / page.width)).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    fun close() {
        closeScope.launch {
            mutex.withLock {
                closed = true
                runCatching { renderer?.close() }
                runCatching { pfd?.close() }
                renderer = null; pfd = null
            }
        }
    }
}

@Composable
private fun PdfPages(file: File) {
    val doc = remember(file) { PdfDocument(file) }
    DisposableEffect(doc) { onDispose { doc.close() } } // aucune fuite de descripteur de fichier
    val count by produceState(-1, doc) { value = doc.pageCount() }
    when (count) {
        -1 -> CircularProgressIndicator(color = Neon.Cyan)
        0 -> Text("PDF protégé par mot de passe ou illisible. Utilise « Ouvrir avec ».", color = Neon.TextMuted)
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count) { index ->
                    // Seules les pages visibles gardent leur image en mémoire
                    val bmp by produceState<Bitmap?>(null, index) { value = doc.render(index, widthPx) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val b = bmp
                        if (b != null) {
                            Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height))
                        } else {
                            Box(Modifier.fillMaxWidth().height(420.dp).background(Neon.Surface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Neon.Cyan)
                            }
                        }
                        Text("${index + 1} / $count", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Texte

@Composable
private fun TextContent(file: File) {
    val text by produceState<String?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                file.bufferedReader().use { r ->
                    val buf = CharArray(MAX_TEXT_CHARS)
                    val n = r.read(buf)
                    if (n <= 0) "" else String(buf, 0, n) + if (n == MAX_TEXT_CHARS) "\n\n[… aperçu limité aux $MAX_TEXT_CHARS premiers caractères]" else ""
                }
            }.getOrElse { "Lecture impossible : ${it.message}" }
        }
    }
    val t = text
    if (t == null) CircularProgressIndicator(color = Neon.Cyan) else {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
            Text(t, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Neon.TextPrimary)
        }
    }
}

private const val MAX_TEXT_CHARS = 100_000
private val TEXT_EXT = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "srt", "vtt", "yaml", "yml")
private val DATE = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE)
