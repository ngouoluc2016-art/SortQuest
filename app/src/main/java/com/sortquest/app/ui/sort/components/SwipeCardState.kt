package com.sortquest.app.ui.sort.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.sortquest.app.domain.model.SwipeDirection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * État physique d'une carte : position, échelle, opacité, direction "armée".
 *
 * Règle de performance : les valeurs qui changent à chaque frame (offset, scale, alpha)
 * ne doivent être lues QUE dans graphicsLayer / drawBehind. Seuls [armedDirection] et
 * [dominantDirection] (qui changent rarement) sont destinés à la composition.
 *
 * @param folderTarget centre de l'icône "dossier" relativement au centre de la carte (classer)
 * @param vaultTarget  centre de l'icône "coffre-fort" relativement au centre de la carte
 */
@Stable
class SwipeCardState(
    private val widthPx: Float,
    private val heightPx: Float,
    private val folderTarget: Offset,
    private val vaultTarget: Offset,
) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    /** Direction dont le seuil est franchi (null = la carte reviendra au centre). */
    var armedDirection by mutableStateOf<SwipeDirection?>(null)
        private set

    val rotationDeg: Float get() = (offset.value.x / widthPx) * MAX_ROTATION_DEG * scale.value

    /** Progression 0..1 vers le seuil de la direction dominante. À lire en phase de dessin. */
    val dragFraction: Float
        get() {
            val o = offset.value
            val xr = abs(o.x) / (widthPx * H_THRESHOLD)
            val yr = abs(o.y) / (heightPx * V_THRESHOLD)
            return max(xr, yr).coerceAtMost(1f)
        }

    /** Direction dominante du glissement : ne change que quelques fois par geste (lecture en composition OK). */
    val dominantDirection: SwipeDirection? by derivedStateOf {
        val o = offset.value
        val xr = o.x / (widthPx * H_THRESHOLD)
        val yr = o.y / (heightPx * V_THRESHOLD)
        when {
            abs(xr) < 0.05f && abs(yr) < 0.05f -> null
            abs(xr) >= abs(yr) -> if (xr > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            else -> if (yr < 0) SwipeDirection.UP else SwipeDirection.DOWN
        }
    }

    /** Pour la carte suivante : 0 = au repos, 1 = la carte du dessus est partie. */
    val exitProgress: Float get() = if (scale.value < 1f) 1f - scale.value else dragFraction

    private fun directionAt(o: Offset): SwipeDirection? {
        val xr = o.x / widthPx
        val yr = o.y / heightPx
        return when {
            abs(xr) >= abs(yr) && abs(xr) > H_THRESHOLD -> if (xr > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            abs(yr) > abs(xr) && abs(yr) > V_THRESHOLD -> if (yr < 0) SwipeDirection.UP else SwipeDirection.DOWN
            else -> null
        }
    }

    fun directionFromVelocity(v: Velocity): SwipeDirection? {
        if (max(abs(v.x), abs(v.y)) < FLING_VELOCITY) return null
        return if (abs(v.x) >= abs(v.y)) {
            if (v.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (v.y < 0) SwipeDirection.UP else SwipeDirection.DOWN
        }
    }

    /** @return true si une NOUVELLE direction vient d'être armée (=> petit retour haptique). */
    suspend fun dragBy(delta: Offset): Boolean {
        offset.snapTo(offset.value + delta)
        val dir = directionAt(offset.value)
        val changed = dir != armedDirection
        armedDirection = dir
        return changed && dir != null
    }

    fun resolveRelease(velocity: Velocity): SwipeDirection? = armedDirection ?: directionFromVelocity(velocity)

    suspend fun reset() {
        armedDirection = null
        offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    /** Sortie de la carte : "rangement" animé pour classer/coffre, éjection pour corbeille/plus tard. */
    suspend fun dismiss(direction: SwipeDirection) = when (direction) {
        SwipeDirection.RIGHT -> flyToFolder(folderTarget)
        SwipeDirection.UP -> flyToFolder(vaultTarget)
        else -> flyOut(direction)
    }

    /**
     * Fly-to-Folder : la carte rétrécit et file vers l'icône du dossier.
     * Trajectoire en spring légèrement sous-amorti (petit rebond à l'arrivée),
     * rétrécissement et fondu en parallèle.
     */
    suspend fun flyToFolder(target: Offset) {
        armedDirection = null
        coroutineScope {
            launch {
                offset.animateTo(
                    target,
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(1f, 1f)),
                )
            }
            launch { scale.animateTo(0.08f, tween(durationMillis = 380, easing = FastOutSlowInEasing)) }
            launch { alpha.animateTo(0f, tween(durationMillis = 180, delayMillis = 260)) }
        }
    }

    suspend fun flyOut(direction: SwipeDirection) {
        armedDirection = direction
        val o = offset.value
        val target = when (direction) {
            SwipeDirection.RIGHT -> Offset(widthPx * 1.6f, o.y + heightPx * 0.05f)
            SwipeDirection.LEFT -> Offset(-widthPx * 1.6f, o.y + heightPx * 0.05f)
            SwipeDirection.UP -> Offset(o.x, -heightPx * 1.4f)
            SwipeDirection.DOWN -> Offset(o.x, heightPx * 1.4f)
        }
        offset.animateTo(target, tween(durationMillis = 260, easing = FastOutLinearInEasing))
    }

    val isAnimating: Boolean get() = offset.isRunning || scale.isRunning

    companion object {
        const val H_THRESHOLD = 0.30f
        const val V_THRESHOLD = 0.20f
        const val MAX_ROTATION_DEG = 12f
        const val FLING_VELOCITY = 1_800f
    }
}
