package com.sortquest.app.ui.sort.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(28.dp)

/**
 * Carte de fichier swipable.
 * - [state] non nul = carte du dessus (gestes actifs) ; nul = carte en attente dans la pile.
 * - [onRelease] : appelé au lâcher validé, AVANT l'animation (retour son/haptique immédiat).
 * - [onSwiped] : appelé APRÈS l'animation de sortie (déclenche l'action métier).
 *
 * Performance 60/120 Hz : position, échelle, opacité, couleur de bordure et opacité du tampon
 * sont lues dans graphicsLayer / drawBehind uniquement. Pendant un glissement, la carte ne se
 * recompose que lorsque la direction dominante change (quelques fois par geste).
 */
@Composable
fun SwipeCard(
    item: FileItem,
    state: SwipeCardState?,
    onThresholdCrossed: () -> Unit,
    onRelease: (SwipeDirection) -> Unit,
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentThreshold by rememberUpdatedState(onThresholdCrossed)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentSwiped by rememberUpdatedState(onSwiped)

    // pointerInput AVANT graphicsLayer : coordonnées non transformées => vitesse de geste exacte
    val gestures = if (state != null) {
        Modifier.pointerInput(item.id) {
            val tracker = VelocityTracker()
            detectDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDrag = { change, dragAmount ->
                    if (state.isAnimating && state.scale.value < 1f) return@detectDragGestures // déjà en vol
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { if (state.dragBy(dragAmount)) currentThreshold() }
                },
                onDragEnd = {
                    val velocity = tracker.calculateVelocity()
                    scope.launch {
                        val direction = state.resolveRelease(velocity)
                        if (direction != null) {
                            currentRelease(direction)
                            state.dismiss(direction)
                            currentSwiped(direction)
                        } else {
                            state.reset()
                        }
                    }
                },
                onDragCancel = { scope.launch { state.reset() } },
            )
        }
    } else Modifier

    // Lecture en composition : change rarement
    val dragDir = state?.dominantDirection

    Box(
        modifier = modifier
            .then(gestures)
            .graphicsLayer {
                if (state != null) {
                    translationX = state.offset.value.x
                    translationY = state.offset.value.y
                    rotationZ = state.rotationDeg
                    scaleX = state.scale.value
                    scaleY = state.scale.value
                    alpha = state.alpha.value
                }
                shape = CardShape
                clip = true
                shadowElevation = 18.dp.toPx()
            }
            .background(Neon.Surface)
            .drawWithContent {
                drawContent()
                // Bordure néon dont l'intensité suit le glissement (lue au dessin uniquement)
                val fraction = state?.dragFraction ?: 0f
                val color = dragDir?.let { Neon.forDirection(it).copy(alpha = 0.25f + 0.75f * fraction) } ?: Neon.Outline
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(stroke),
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            FilePreview(item, Modifier.fillMaxWidth().weight(1f))
            CardInfo(item)
        }
        if (state != null && dragDir != null) {
            SwipeStamp(
                direction = dragDir,
                progress = { state.dragFraction },
                modifier = Modifier.align(stampAlignment(dragDir)).padding(20.dp),
            )
        }
    }
}

@Composable
private fun CardInfo(item: FileItem) {
    val context = LocalContext.current
    val categoryColor = Neon.forCategory(item.category)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            item.name, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(item.category.label, categoryColor)
            if (item.isDuplicate) Chip("DOUBLON", Neon.Red)
            item.matchedKeyword?.let { Chip("#$it", Neon.Cyan) }
        }
        Text(
            "${Formatter.formatShortFileSize(context, item.sizeBytes)} · ${DATE_FORMAT.format(Date(item.lastModified))}",
            style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Neon.Lime, modifier = Modifier.size(16.dp))
            Text(
                item.suggestedTargetDir.removePrefix("Documents/"),
                style = MaterialTheme.typography.labelSmall, color = Neon.Lime,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Tampon incliné qui apparaît progressivement pendant le glissement (opacité lue au dessin). */
@Composable
private fun SwipeStamp(direction: SwipeDirection, progress: () -> Float, modifier: Modifier) {
    val color = Neon.forDirection(direction)
    val label = when (direction) {
        SwipeDirection.RIGHT -> "CLASSER"
        SwipeDirection.LEFT -> "CORBEILLE"
        SwipeDirection.UP -> "COFFRE-FORT"
        SwipeDirection.DOWN -> "PLUS TARD"
    }
    val angle = when (direction) {
        SwipeDirection.RIGHT -> -14f
        SwipeDirection.LEFT -> 14f
        else -> 0f
    }
    Text(
        label,
        style = MaterialTheme.typography.headlineSmall,
        color = color,
        modifier = modifier
            .graphicsLayer {
                val p = progress()
                alpha = p
                val s = 0.8f + 0.2f * p
                scaleX = s; scaleY = s
                rotationZ = angle
            }
            .background(Neon.Background.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

private fun stampAlignment(d: SwipeDirection): Alignment = when (d) {
    SwipeDirection.RIGHT -> Alignment.TopStart
    SwipeDirection.LEFT -> Alignment.TopEnd
    SwipeDirection.UP -> Alignment.BottomCenter
    SwipeDirection.DOWN -> Alignment.TopCenter
}

private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
