package com.sortquest.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.sortquest.app.ui.theme.Neon
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ConfettiStyle {
    /** Pluie depuis le haut de l'écran (montée de niveau). */
    RAIN,
    /** Explosion depuis le centre avec gravité (Session Express réussie). */
    BURST,
}

private data class Piece(
    val x: Float,          // RAIN : position horizontale 0..1
    val angle: Float,      // BURST : direction de départ (radians)
    val speed: Float,
    val drift: Float,
    val sizeDp: Float,
    val spin: Float,       // degrés par seconde
    val color: Color,
    val delay: Float,      // secondes
    val isCircle: Boolean,
)

/**
 * Confettis 100 % Canvas, réutilisables partout (aucune dépendance, aucune image).
 *
 * - [trigger] : changer sa valeur relance l'animation ; null = rien n'est dessiné.
 * - Performance : les pièces sont tirées au sort une seule fois par déclenchement ; à chaque frame,
 *   seul le temps écoulé change et il n'est lu que dans la phase de DESSIN du Canvas.
 *   => aucune recomposition pendant l'animation, fluide à 60/120 Hz.
 * - Ne capte aucun toucher : l'interface reste utilisable pendant la fête.
 */
@Composable
fun ConfettiOverlay(
    trigger: Any?,
    modifier: Modifier = Modifier,
    style: ConfettiStyle = ConfettiStyle.RAIN,
    pieceCount: Int = 90,
    durationMs: Long = 2_600,
    palette: List<Color> = listOf(Neon.Cyan, Neon.Magenta, Neon.Lime, Neon.Amber, Neon.Violet),
) {
    if (trigger == null) return
    val pieces = remember(trigger, style, pieceCount) {
        val rnd = Random(trigger.hashCode())
        List(pieceCount) { i ->
            Piece(
                x = rnd.nextFloat(),
                angle = (rnd.nextFloat() * 2f * Math.PI).toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.65f,
                drift = (rnd.nextFloat() - 0.5f) * 0.15f,
                sizeDp = 5f + rnd.nextFloat() * 8f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                color = palette[i % palette.size],
                delay = if (style == ConfettiStyle.RAIN) rnd.nextFloat() * 0.35f else rnd.nextFloat() * 0.08f,
                isCircle = rnd.nextFloat() < 0.25f,
            )
        }
    }
    var elapsed by remember(trigger) { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        val start = withFrameMillis { it }
        while (elapsed < durationMs) {
            elapsed = withFrameMillis { it } - start // une écriture par frame, lue au dessin uniquement
        }
    }

    Canvas(modifier) {
        val ms = elapsed
        if (ms >= durationMs) return@Canvas
        val t = ms / 1000f
        val fade = (1f - ms.toFloat() / durationMs).coerceIn(0f, 1f).let { if (it > 0.3f) 1f else it / 0.3f }
        pieces.forEach { p ->
            val tt = (t - p.delay).coerceAtLeast(0f)
            if (tt <= 0f) return@forEach
            val pos = when (style) {
                ConfettiStyle.RAIN -> Offset(
                    (p.x + p.drift * tt + 0.02f * sin(tt * 6f + p.x * 10f)) * size.width,
                    -40f + tt * p.speed * size.height,
                )
                ConfettiStyle.BURST -> {
                    val v = p.speed * size.minDimension * 1.6f            // vitesse initiale (px/s)
                    val g = size.height * 1.4f                             // gravité (px/s²)
                    Offset(
                        center.x + cos(p.angle) * v * tt,
                        center.y + sin(p.angle) * v * tt + 0.5f * g * tt * tt,
                    )
                }
            }
            if (pos.y > size.height + 40f) return@forEach
            val px = p.sizeDp * density
            val color = p.color.copy(alpha = fade)
            if (p.isCircle) {
                drawCircle(color, radius = px / 2.5f, center = pos)
            } else {
                rotate(p.spin * tt, pivot = pos) {
                    drawRect(color, topLeft = Offset(pos.x - px / 2, pos.y - px / 4), size = Size(px, px / 2))
                }
            }
        }
    }
}
