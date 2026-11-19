package com.sortquest.app.ui.dashboard

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sortquest.app.domain.model.Badge
import com.sortquest.app.domain.model.StorageStats
import com.sortquest.app.domain.model.UserProgress
import com.sortquest.app.ui.sort.ExpressSession
import com.sortquest.app.ui.theme.Neon
import java.time.LocalDate

/**
 * Tableau de bord gamifié : jauge Santé du Stockage, niveau, barre d'XP, série, espace libéré,
 * badges. Bordure néon animée en continu (coût négligeable : dessin dans drawBehind uniquement).
 */
@Composable
fun DashboardHeader(
    progress: UserProgress,
    storage: StorageStats?,
    pendingCount: Int,
    combo: Int,
    express: ExpressSession?,
    onStartExpress: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var badgeDetail by remember { mutableStateOf<Badge?>(null) }
    val today = remember { LocalDate.now().toEpochDay() }

    // Halo qui tourne autour de la carte
    val sweep by rememberInfiniteTransition(label = "border").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val r = CornerRadius(24.dp.toPx())
                drawRoundRect(Neon.Surface, cornerRadius = r)
                val shift = size.width * sweep
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Neon.Cyan, Neon.Magenta, Neon.Lime, Neon.Cyan),
                        start = Offset(shift - size.width, 0f),
                        end = Offset(shift + size.width, size.height),
                    ),
                    cornerRadius = r,
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StorageHealthGauge(storage?.health ?: 0f, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelPill(progress.level.number)
                    Spacer(Modifier.size(8.dp))
                    // Le titre glisse quand le niveau change
                    AnimatedContent(
                        targetState = progress.level.title,
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "levelTitle",
                        modifier = Modifier.weight(1f),
                    ) { title ->
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary, maxLines = 1)
                    }
                    IconButton(onClick = onOpenRules, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Règles et Auto-Pilot", tint = Neon.TextMuted)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Paramètres", tint = Neon.TextMuted)
                    }
                }
                XpBar(progress.levelProgress, Modifier.fillMaxWidth().height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        progress.xpToNextLevel?.let { "${progress.totalXp} XP · encore $it" } ?: "${progress.totalXp} XP · niveau max",
                        style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    ComboChip(combo)
                }
            }
        }

        // Statistiques clés
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Icons.Rounded.LocalFireDepartment, "${progress.streakAsOf(today)} j", "série", Neon.Amber, Modifier.weight(1f))
            StatTile(Icons.Rounded.Storage, Formatter.formatShortFileSize(context, progress.bytesFreed), "libérés", Neon.Lime, Modifier.weight(1f))
            StatTile(Icons.Rounded.Flag, "$pendingCount", "à trier", Neon.Cyan, Modifier.weight(1f))
        }

        // Badges : verrouillés en gris, débloqués en néon (le dernier gagné pulse)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Badge.entries.forEach { badge ->
                BadgeIcon(
                    badge = badge,
                    unlocked = badge in progress.badges,
                    isNew = badge in progress.pendingBadges,
                    onClick = { badgeDetail = badge },
                )
            }
        }

        if (express != null) ExpressBar(express) else {
            OutlinedButton(onClick = onStartExpress, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Icon(Icons.Rounded.Bolt, null, tint = Neon.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Session Express 2 min", style = MaterialTheme.typography.labelLarge, color = Neon.Amber)
            }
        }
    }

    badgeDetail?.let { badge ->
        val unlocked = badge in progress.badges
        AlertDialog(
            onDismissRequest = { badgeDetail = null },
            icon = { Icon(badge.icon, null, tint = if (unlocked) badge.color else Neon.TextMuted) },
            title = { Text(badge.title) },
            text = { Text((if (unlocked) "Débloqué : " else "À débloquer : ") + badge.description.lowercase()) },
            confirmButton = { TextButton(onClick = { badgeDetail = null }) { Text("OK") } },
            containerColor = Neon.Surface,
        )
    }
}

@Composable
private fun LevelPill(number: Int) {
    Text(
        "NIV. $number",
        style = MaterialTheme.typography.labelSmall,
        color = Neon.Background,
        modifier = Modifier
            .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Magenta)), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Row(
        modifier.background(Neon.SurfaceHigh, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(value, style = MaterialTheme.typography.labelLarge, color = Neon.TextPrimary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        }
    }
}

@Composable
private fun BadgeIcon(badge: Badge, unlocked: Boolean, isNew: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (isNew) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val color = if (unlocked) badge.color else Neon.Outline
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .background(if (unlocked) color.copy(alpha = 0.15f) else Neon.SurfaceHigh, CircleShape)
            .border(1.5.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (unlocked) badge.icon else Icons.Rounded.Lock,
            contentDescription = badge.title + if (unlocked) " (débloqué)" else " (verrouillé)",
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}

val Badge.icon: ImageVector
    get() = when (this) {
        Badge.PREMIER_TRI -> Icons.Rounded.Flag
        Badge.CHASSEUR_DOUBLONS -> Icons.Rounded.ContentCopy
        Badge.UN_GO_LIBERE -> Icons.Rounded.Storage
        Badge.MAITRE_AUTO_PILOT -> Icons.Rounded.AutoMode
        Badge.ARCHIVISTE_REGULIER -> Icons.Rounded.CalendarMonth
    }

val Badge.color: Color
    get() = when (this) {
        Badge.PREMIER_TRI -> Neon.Cyan
        Badge.CHASSEUR_DOUBLONS -> Neon.Red
        Badge.UN_GO_LIBERE -> Neon.Lime
        Badge.MAITRE_AUTO_PILOT -> Neon.Violet
        Badge.ARCHIVISTE_REGULIER -> Neon.Amber
    }

/** Jauge "Santé du Stockage" : arc 270°, rouge -> ambre -> vert néon. */
@Composable
fun StorageHealthGauge(health: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(health, tween(900), label = "health")
    val color = if (animated < 0.5f) lerp(Neon.Red, Neon.Amber, animated * 2)
    else lerp(Neon.Amber, Neon.Lime, (animated - 0.5f) * 2)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Neon.SurfaceHigh, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(animated * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = color)
            Text("santé", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        }
    }
}

@Composable
fun XpBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction, spring(stiffness = Spring.StiffnessLow), label = "xp")
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Neon.SurfaceHigh, cornerRadius = r)
        if (animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Magenta)),
                size = Size(size.width * animated, size.height),
                cornerRadius = r,
            )
        }
    }
}

@Composable
private fun ComboChip(combo: Int) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo >= 2) {
            scale.snapTo(1.35f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    AnimatedVisibility(visible = combo >= 2, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        val color = if (combo >= 10) Neon.Magenta else if (combo >= 5) Neon.Amber else Neon.Cyan
        Text(
            "COMBO x$combo",
            style = MaterialTheme.typography.labelLarge,
            color = Neon.Background,
            modifier = Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(color, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ExpressBar(express: ExpressSession) {
    val timeFraction = express.remainingSeconds / ExpressSession.DURATION_SECONDS.toFloat()
    val urgent = express.remainingSeconds <= 20
    Column(
        Modifier.fillMaxWidth().background(Neon.SurfaceHigh, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Text(
                "%d:%02d".format(express.remainingSeconds / 60, express.remainingSeconds % 60),
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) Neon.Red else Neon.Amber,
            )
            Spacer(Modifier.weight(1f))
            Text("${express.sorted}/${express.target} · combo actif", style = MaterialTheme.typography.labelLarge, color = Neon.TextPrimary)
        }
        XpBar(express.sorted / express.target.toFloat(), Modifier.fillMaxWidth().height(6.dp))
        Box(Modifier.fillMaxWidth(timeFraction).height(2.dp).background(if (urgent) Neon.Red else Neon.Amber.copy(alpha = 0.6f)))
    }
}
