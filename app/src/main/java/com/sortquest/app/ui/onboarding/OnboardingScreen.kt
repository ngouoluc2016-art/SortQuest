package com.sortquest.app.ui.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * Accueil en 3 étapes : 1. le tri par swipe, 2. la confidentialité, 3. l'autorisation d'accès.
 * L'accès est revérifié à chaque retour sur l'appli (ON_RESUME) : dès qu'il est accordé dans
 * les Paramètres, [onFinished] est appelé automatiquement.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = StoragePermissionHelper.hasAllFilesAccess(context)
                if (granted) currentOnFinished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } // pas de fuite d'observateur
    }

    // Android 8 à 10 : permissions classiques
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        granted = results.values.all { it }
        if (granted) currentOnFinished()
    }

    Column(Modifier.fillMaxSize().background(Neon.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < PAGE_COUNT - 1) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGE_COUNT - 1) } }) {
                    Text("Passer", color = Neon.TextMuted)
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> OnboardingPage(
                    illustration = { SwipeIllustration() },
                    title = "Range en jouant",
                    body = "Une carte = un fichier. Glisse à droite pour le classer, à gauche pour la corbeille, " +
                        "en haut pour le coffre-fort, en bas pour plus tard. Gagne de l'XP et des badges.",
                )
                1 -> OnboardingPage(
                    illustration = { PrivacyIllustration() },
                    title = "100 % local, 100 % privé",
                    body = "Tout se passe sur ton téléphone. Aucun fichier, aucun nom de fichier, aucune statistique " +
                        "n'est envoyé sur Internet. SortQuest ne demande même pas l'accès au réseau.",
                )
                else -> PermissionPage(granted)
            }
        }

        PagerDots(current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Retour")
                }
            }
            Spacer(Modifier.weight(1f))
            val isLast = pager.currentPage == PAGE_COUNT - 1
            Button(
                onClick = {
                    when {
                        !isLast -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        granted -> currentOnFinished()
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                            StoragePermissionHelper.openAllFilesAccessSettings(context)
                        else -> legacyLauncher.launch(StoragePermissionHelper.legacyPermissions())
                    }
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background),
            ) {
                Text(
                    when {
                        !isLast -> "Suivant"
                        granted -> "Commencer"
                        else -> "Ouvrir les paramètres"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!isLast) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(illustration: @Composable () -> Unit, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Neon.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Neon.TextMuted, textAlign = TextAlign.Center)
    }
}

/** Étape 3 : explication + guide visuel de l'écran système. */
@Composable
private fun PermissionPage(granted: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (granted) "Accès accordé" else "Une dernière autorisation",
            style = MaterialTheme.typography.headlineSmall,
            color = if (granted) Neon.Lime else Neon.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Android ne laisse voir que les photos par défaut. Pour ranger aussi tes PDF, APK et archives, " +
                "SortQuest a besoin de l'accès « Tous les fichiers ». Tu peux le retirer à tout moment.",
            style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SettingsGuide(granted)
            Spacer(Modifier.height(16.dp))
            GuideStep(1, "Touche « Ouvrir les paramètres »")
            GuideStep(2, "Active « Autoriser la gestion de tous les fichiers »")
            GuideStep(3, "Reviens : SortQuest démarre tout seul")
        } else {
            GuideStep(1, "Touche « Ouvrir les paramètres » puis « Autoriser »")
        }
    }
}

/** Maquette animée de l'écran système : l'interrupteur s'active en boucle pour montrer le geste. */
@Composable
private fun SettingsGuide(granted: Boolean) {
    val loop by rememberInfiniteTransition(label = "guide").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes { durationMillis = 2_400; 0f at 0; 0f at 900; 1f at 1_100; 1f at 2_400 },
            RepeatMode.Restart,
        ),
        label = "toggle",
    )
    val on = granted || loop > 0.5f
    val border by animateColorAsState(if (on) Neon.Lime else Neon.Outline, label = "border")
    Column(
        Modifier.fillMaxWidth()
            .background(Neon.Surface, RoundedCornerShape(18.dp))
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Paramètres  >  Accès à tous les fichiers", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Neon.Cyan, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("S", style = MaterialTheme.typography.titleMedium, color = Neon.Background)
            }
            Text("SortQuest", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Autoriser la gestion de tous les fichiers", style = MaterialTheme.typography.bodyMedium,
                color = Neon.TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = on, onCheckedChange = null, // maquette : non interactive
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Lime, checkedThumbColor = Neon.Background),
            )
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).background(Neon.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary)
    }
}

@Composable
private fun PagerDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGE_COUNT) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier.height(8.dp).width(width)
                    .background(if (i == current) Neon.Cyan else Neon.Outline, CircleShape),
            )
        }
    }
}

// ------------------------------------------------------------------ Illustrations

/** Carte qui oscille gauche/droite avec les couleurs des actions. */
@Composable
private fun SwipeIllustration() {
    val t by rememberInfiniteTransition(label = "swipe").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing",
    )
    val glow = if (t > 0) Neon.Lime else Neon.Red
    Box(contentAlignment = Alignment.Center) {
        // cartes en dessous
        repeat(2) { i ->
            Box(
                Modifier.size(width = 150.dp, height = 190.dp)
                    .graphicsLayer { translationY = (2 - i) * 14f; scaleX = 0.88f + i * 0.06f; scaleY = 0.88f + i * 0.06f }
                    .background(Neon.SurfaceHigh, RoundedCornerShape(22.dp)),
            )
        }
        Box(
            Modifier.size(width = 150.dp, height = 190.dp)
                .graphicsLayer { translationX = t * 60f; rotationZ = t * 10f }
                .background(Neon.Surface, RoundedCornerShape(22.dp))
                .border(2.dp, glow.copy(alpha = 0.3f + 0.7f * kotlin.math.abs(t)), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Folder, null, tint = Neon.Cyan, modifier = Modifier.size(56.dp))
                Text(if (t > 0) "CLASSER" else "CORBEILLE", style = MaterialTheme.typography.labelLarge, color = glow)
            }
        }
    }
}

/** Téléphone protégé par un bouclier, nuage barré, ondes qui s'éloignent. */
@Composable
private fun PrivacyIllustration() {
    val pulse by rememberInfiniteTransition(label = "privacy").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000), RepeatMode.Restart),
        label = "rings",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            repeat(3) { i ->
                val p = (pulse + i / 3f) % 1f
                drawCircle(
                    color = Neon.Cyan.copy(alpha = (1f - p) * 0.5f),
                    radius = size.minDimension / 2 * (0.35f + 0.65f * p),
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        Icon(Icons.Rounded.PhoneAndroid, null, tint = Neon.TextPrimary, modifier = Modifier.size(96.dp))
        Icon(Icons.Rounded.Shield, null, tint = Neon.Lime, modifier = Modifier.size(40.dp))
        IconBadge(Icons.Rounded.CloudOff, Neon.Magenta, Modifier.align(Alignment.TopEnd))
        IconBadge(Icons.Rounded.Check, Neon.Lime, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier) {
    Box(
        modifier.padding(16.dp).size(44.dp).background(Neon.Surface, CircleShape).border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
}
