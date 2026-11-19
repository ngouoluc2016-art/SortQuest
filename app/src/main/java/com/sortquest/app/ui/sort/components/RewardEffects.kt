package com.sortquest.app.ui.sort.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Une "récompense" visuelle : explosion de particules + "+XP" qui s'envole. */
data class Burst(val id: Long, val color: Color, val xp: Int)

private data class Particle(val angle: Float, val speed: Float, val radius: Float)

@Composable
fun BurstEffect(burst: Burst, onFinished: (Long) -> Unit, modifier: Modifier = Modifier) {
    val progress = remember(burst.id) { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    val particles = remember(burst.id) {
        List(PARTICLE_COUNT) {
            Particle(
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                speed = 0.45f + Random.nextFloat() * 0.55f,
                radius = 3f + Random.nextFloat() * 5f,
            )
        }
    }
    LaunchedEffect(burst.id) {
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        finished(burst.id)
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val maxDist = min(size.width, size.height) * 0.45f
            particles.forEach { particle ->
                val d = maxDist * particle.speed * p
                drawCircle(
                    color = burst.color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                    radius = particle.radius.dp.toPx() * (1f - 0.5f * p),
                    center = center + Offset(cos(particle.angle) * d, sin(particle.angle) * d),
                )
            }
        }
        if (burst.xp > 0) {
            Text(
                "+${burst.xp} XP",
                style = MaterialTheme.typography.headlineSmall,
                color = burst.color,
                modifier = Modifier.graphicsLayer {
                    val p = progress.value
                    translationY = -120.dp.toPx() * p
                    alpha = 1f - p * p
                    val s = 0.8f + 0.4f * p
                    scaleX = s; scaleY = s
                },
            )
        }
    }
}

private const val PARTICLE_COUNT = 18
