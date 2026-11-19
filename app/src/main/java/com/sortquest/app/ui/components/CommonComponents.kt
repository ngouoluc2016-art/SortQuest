package com.sortquest.app.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sortquest.app.domain.model.FileKind
import com.sortquest.app.domain.model.FileKinds
import com.sortquest.app.ui.theme.Neon
import com.sortquest.app.workers.ScanStatus

/**
 * Bloc "squelette" animé (shimmer) affiché pendant un chargement.
 * L'animation n'est lue qu'en phase de dessin (drawBehind) : aucune recomposition par frame.
 */
@Composable
fun ShimmerBlock(height: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 16.dp) {
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Neon.Surface, RoundedCornerShape(cornerRadius))
            .drawBehind {
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Neon.Surface, Neon.SurfaceHigh, Neon.Surface),
                        start = Offset(w * progress - w, 0f),
                        end = Offset(w * progress, size.height),
                    )
                )
            },
    )
}

@Composable
fun ShimmerList(count: Int = 5, itemHeight: Dp = 72.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { ShimmerBlock(itemHeight) }
    }
}

/** Bandeau d'avancement d'un scan (déterminé pendant la recherche de doublons). */
@Composable
fun ScanStatusBanner(status: ScanStatus, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(status.label, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
        val fraction = status.fraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                color = Neon.Cyan, trackColor = Neon.SurfaceHigh)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Cyan, trackColor = Neon.SurfaceHigh)
        }
    }
}

@Composable
fun FileKindIcon(extension: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val kind = FileKinds.fromExtension(extension)
    val (icon, color) = kindVisual(kind)
    Box(
        modifier.size(size).background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f)) }
}

private fun kindVisual(kind: FileKind): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (kind) {
    FileKind.IMAGE -> Icons.Rounded.Image to Neon.Magenta
    FileKind.VIDEO -> Icons.Rounded.Movie to Neon.Magenta
    FileKind.AUDIO -> Icons.Rounded.MusicNote to Neon.Violet
    FileKind.PDF -> Icons.Rounded.PictureAsPdf to Neon.Red
    FileKind.DOCUMENT -> Icons.Rounded.Description to Neon.Cyan
    FileKind.APK -> Icons.Rounded.Android to Neon.Lime
    FileKind.ARCHIVE -> Icons.Rounded.Archive to Neon.Amber
    FileKind.OTHER -> Icons.Rounded.InsertDriveFile to Neon.TextMuted
}

@Composable
fun rememberBytesFormatter(): (Long) -> String {
    val context = LocalContext.current
    return { bytes -> Formatter.formatShortFileSize(context, bytes) }
}

/** Dossier parent lisible : "/storage/emulated/0/Download/x.pdf" -> "Download". */
fun String.readableParent(): String =
    substringBeforeLast('/').substringAfter("/storage/emulated/0/", substringBeforeLast('/'))
        .ifEmpty { "Stockage interne" }

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = Neon.Lime, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted)
    }
}
