package com.sortquest.app.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortquest.app.domain.model.Badge
import com.sortquest.app.domain.model.Level
import com.sortquest.app.domain.repository.ProgressRepository
import com.sortquest.app.ui.components.ConfettiOverlay
import com.sortquest.app.ui.components.ConfettiStyle
import com.sortquest.app.ui.dashboard.color
import com.sortquest.app.ui.dashboard.icon
import com.sortquest.app.ui.sort.components.rememberSortHaptics
import com.sortquest.app.ui.theme.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Celebration(val level: Level?, val badges: List<Badge>)

/**
 * Observe la progression PERSISTÉE : une montée de niveau obtenue pendant que l'appli
 * était fermée (Auto-Pilot nocturne) est fêtée à la prochaine ouverture.
 */
@HiltViewModel
class CelebrationViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    val celebration: StateFlow<Celebration?> = progressRepository.observe()
        .map { p ->
            if (p.hasPendingCelebration) Celebration(p.pendingLevelUp, p.pendingBadges.sortedBy { it.ordinal }) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() {
        viewModelScope.launch { progressRepository.markCelebrated() }
    }
}

/** À placer à la racine de la navigation : la fête s'affiche par-dessus n'importe quel écran. */
@Composable
fun CelebrationHost(
    viewModel: CelebrationViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        celebration?.let { CelebrationDialog(it, onDismiss = viewModel::dismiss) }
    }
}

@Composable
private fun CelebrationDialog(celebration: Celebration, onDismiss: () -> Unit) {
    val haptics = rememberSortHaptics()
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(celebration) {
        haptics.celebrate()
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Toucher en dehors de la carte = fermer (geste attendu par l'utilisateur)
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
            contentAlignment = Alignment.Center,
        ) {
            ConfettiOverlay(trigger = celebration, modifier = Modifier.fillMaxSize(), style = ConfettiStyle.RAIN)
            Column(
                Modifier
                    .padding(28.dp)
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    // la carte absorbe ses propres touchers : seul l'extérieur ferme la fenêtre
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .background(Neon.Surface, RoundedCornerShape(28.dp))
                    .border(2.dp, Brush.linearGradient(listOf(Neon.Cyan, Neon.Magenta)), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                celebration.level?.let { level ->
                    Box(
                        Modifier.size(96.dp)
                            .background(Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
                            .border(3.dp, Neon.Cyan, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${level.number}", style = MaterialTheme.typography.headlineSmall, color = Neon.Cyan)
                    }
                    Text("Niveau supérieur !", style = MaterialTheme.typography.headlineSmall, color = Neon.TextPrimary)
                    Text("Tu es désormais ${level.title}", style = MaterialTheme.typography.titleMedium,
                        color = Neon.Magenta, textAlign = TextAlign.Center)
                }
                if (celebration.badges.isNotEmpty()) {
                    Text(
                        if (celebration.badges.size > 1) "Nouveaux badges" else "Nouveau badge",
                        style = MaterialTheme.typography.labelLarge, color = Neon.TextMuted,
                    )
                    celebration.badges.forEach { badge ->
                        Row(
                            Modifier.fillMaxWidth().background(Neon.SurfaceHigh, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(badge.icon, null, tint = badge.color, modifier = Modifier.size(28.dp))
                            Column {
                                Text(badge.title, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
                                Text(badge.description, style = MaterialTheme.typography.bodySmall, color = Neon.TextMuted)
                            }
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background),
                ) { Text("Continuer") }
            }
        }
    }
}
