package com.sortquest.app.ui.sort

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import com.sortquest.app.core.image.prefetch
import com.sortquest.app.ui.components.ConfettiOverlay
import com.sortquest.app.ui.components.ConfettiStyle
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortquest.app.domain.model.FileItem
import com.sortquest.app.domain.model.SwipeDirection
import com.sortquest.app.ui.sort.components.Burst
import com.sortquest.app.ui.sort.components.BurstEffect
import com.sortquest.app.ui.dashboard.DashboardHeader
import com.sortquest.app.ui.sort.components.SortHaptics
import com.sortquest.app.ui.sort.components.SwipeCard
import com.sortquest.app.ui.sort.components.SwipeCardState
import com.sortquest.app.ui.sort.components.rememberSortHaptics
import com.sortquest.app.ui.theme.Neon
import kotlinx.coroutines.launch

@Composable
fun SwipeSortScreen(
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    viewModel: SwipeSortViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberSortHaptics()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val bursts = remember { mutableStateListOf<Burst>() }
    var expressConfetti by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    // Préchargement des miniatures des 3 cartes suivantes : prêtes avant d'arriver à l'écran
    val upcomingIds = state.deck.drop(1).take(PREFETCH_COUNT).map { it.id }
    LaunchedEffect(upcomingIds) {
        context.imageLoader.prefetch(context, state.deck.drop(1).take(PREFETCH_COUNT))
    }

    // Événements ponctuels : effets et snackbar "Annuler" (niveaux/badges : CelebrationHost)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SortUiEvent.ActionDone -> {
                    if (event.direction != SwipeDirection.DOWN) {
                        bursts += Burst(System.nanoTime(), Neon.forDirection(event.direction), event.xp)
                    }
                    if (event.combo >= 5 && event.combo % 5 == 0) haptics.celebrate() // palier de combo
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            message = actionMessage(event.direction, event.fileName),
                            actionLabel = "Annuler",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.historyId)
                    }
                }
                is SortUiEvent.ExpressFinished -> {
                    if (event.success) {
                        expressConfetti = System.nanoTime() // explosion de confettis
                        haptics.celebrate()
                    }
                    scope.launch {
                        snackbar.showSnackbar(
                            if (event.success) "Session réussie ! ${event.sorted} fichiers, bonus +${event.bonusXp} XP"
                            else "Temps écoulé : ${event.sorted} fichiers triés. Retente ta chance !"
                        )
                    }
                }
                is SortUiEvent.Error -> {
                    haptics.error()
                    scope.launch { snackbar.showSnackbar(event.message) }
                }
                is SortUiEvent.Undone -> {
                    haptics.undo()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${event.fileName} remis à sa place")
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // insets gérés par le Scaffold racine
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Tableau de bord, identique en portrait et en paysage
        val dashboard: @Composable (Modifier) -> Unit = { m ->
            DashboardHeader(
                progress = state.progress,
                storage = state.storage,
                pendingCount = state.pendingCount,
                combo = state.combo,
                express = state.express,
                onStartExpress = viewModel::startExpressSession,
                onOpenRules = onOpenRules,
                onOpenSettings = onOpenSettings,
                modifier = m,
            )
        }

        // Zone des cartes (pile, cibles du Fly-to-Folder, barre d'actions)
        val cardArea: @Composable (Modifier) -> Unit = { areaModifier ->
            BoxWithConstraints(areaModifier) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val top = state.deck.firstOrNull()

                // Cibles du Fly-to-Folder : centres des icônes, relatifs au centre de la carte
                val barPx = with(density) { ACTION_BAR_SPACE.toPx() }
                val targetInset = with(density) { (TARGET_PADDING + TARGET_SIZE / 2).toPx() }
                val cardCenter = Offset(widthPx / 2, (heightPx - barPx) / 2)
                val folderTarget = Offset(widthPx - targetInset, targetInset) - cardCenter
                val vaultTarget = Offset(targetInset, targetInset) - cardCenter

                // Nouvel état physique à chaque nouvelle carte du dessus
                val topState = remember(top?.id, widthPx, heightPx) {
                    SwipeCardState(widthPx, heightPx, folderTarget, vaultTarget)
                }
                var flight by remember { mutableStateOf<Flight?>(null) }
                var arrivals by remember { mutableIntStateOf(0) }

                fun release(item: FileItem, direction: SwipeDirection) {
                    haptics.forRelease(direction)
                    if (direction == SwipeDirection.RIGHT || direction == SwipeDirection.UP) {
                        flight = Flight(direction, item.suggestedLabel ?: item.suggestedTargetDir.substringAfterLast('/'))
                    }
                }
                fun landed(item: FileItem, direction: SwipeDirection) {
                    if (flight != null) arrivals++ // déclenche le rebond de l'icône
                    viewModel.onSwipe(item, direction)
                }

                if (top == null) {
                    EmptyDeck(state.isScanning, state.scannedFiles, onRescan = viewModel::rescan)
                } else {
                    CardStack(
                        deck = state.deck,
                        topState = topState,
                        haptics = haptics,
                        onRelease = ::release,
                        onSwiped = ::landed,
                        onOpen = { onOpenFile(it.path) },
                    )
                }

                // Icônes cibles : visibles pendant un glissement vers la droite / le haut, et pendant le vol
                val hint = topState.dominantDirection
                FolderTarget(
                    visible = hint == SwipeDirection.RIGHT || flight?.direction == SwipeDirection.RIGHT,
                    icon = Icons.Rounded.Folder,
                    label = flight?.takeIf { it.direction == SwipeDirection.RIGHT }?.label
                        ?: (top?.suggestedLabel ?: top?.suggestedTargetDir?.substringAfterLast('/')).orEmpty(),
                    color = Neon.Lime,
                    pulseKey = arrivals,
                    modifier = Modifier.align(Alignment.TopEnd).padding(TARGET_PADDING),
                )
                FolderTarget(
                    visible = hint == SwipeDirection.UP || flight?.direction == SwipeDirection.UP,
                    icon = Icons.Rounded.Lock,
                    label = "Coffre-fort",
                    color = Neon.Magenta,
                    pulseKey = arrivals,
                    modifier = Modifier.align(Alignment.TopStart).padding(TARGET_PADDING),
                )
                LaunchedEffect(arrivals) {
                    if (arrivals > 0) {
                        delay(450) // le temps du rebond, puis on range l'icône
                        flight = null
                    }
                }

                // Effets de récompense par-dessus la pile
                bursts.toList().forEach { burst ->
                    key(burst.id) { BurstEffect(burst, onFinished = { id -> bursts.removeAll { it.id == id } }) }
                }

                // Barre d'actions : alternative accessible aux gestes (et utile à une main)
                if (top != null) {
                    ActionBar(
                        canUndo = state.canUndo,
                        onUndo = { viewModel.undo() },
                        onAction = { direction ->
                            if (!topState.isAnimating) {
                                scope.launch {
                                    release(top, direction)
                                    topState.dismiss(direction)
                                    landed(top, direction)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        // Disposition adaptative : paysage = tableau de bord à gauche, cartes à droite
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        dashboard(Modifier)
                    }
                    cardArea(Modifier.weight(0.55f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dashboard(Modifier)
                    cardArea(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    // Session Express réussie : explosion de confettis par-dessus tout l'écran
    ConfettiOverlay(
        trigger = expressConfetti,
        style = ConfettiStyle.BURST,
        pieceCount = 120,
        modifier = Modifier.fillMaxSize(),
    )
}

private data class Flight(val direction: SwipeDirection, val label: String)

/** Icône de dossier cible : apparaît en douceur, rebondit (spring) quand une carte y "atterrit". */
@Composable
private fun FolderTarget(
    visible: Boolean,
    icon: ImageVector,
    label: String,
    color: Color,
    pulseKey: Int,
    modifier: Modifier = Modifier,
) {
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0 && visible) {
            bounce.snapTo(1.35f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(TARGET_SIZE)
                    .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                    .background(Neon.Background.copy(alpha = 0.85f), CircleShape)
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp)) }
            if (label.isNotEmpty()) {
                Text(
                    label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
                    modifier = Modifier.background(Neon.Background.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Pile de 3 cartes : la suivante grossit à mesure que celle du dessus s'éloigne. */
@Composable
private fun CardStack(
    deck: List<FileItem>,
    topState: SwipeCardState,
    haptics: SortHaptics,
    onRelease: (FileItem, SwipeDirection) -> Unit,
    onSwiped: (FileItem, SwipeDirection) -> Unit,
    onOpen: (FileItem) -> Unit,
) {
    val visible = deck.take(VISIBLE_CARDS)
    Box(Modifier.fillMaxSize().padding(bottom = ACTION_BAR_SPACE), contentAlignment = Alignment.TopCenter) {
        // Dessin de l'arrière vers l'avant
        visible.asReversed().forEachIndexed { reversedIndex, item ->
            val index = visible.lastIndex - reversedIndex // 0 = carte du dessus
            key(item.id) {
                if (index == 0) {
                    SwipeCard(
                        item = item,
                        state = topState,
                        onThresholdCrossed = haptics::tick,
                        onRelease = { direction -> onRelease(item, direction) },
                        onOpen = { onOpen(item) },
                        onSwiped = { direction -> onSwiped(item, direction) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SwipeCard(
                        item = item,
                        state = null,
                        onThresholdCrossed = {},
                        onRelease = {},
                        onSwiped = {},
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            // index 1 : 0.94 -> 1.0 ; index 2 : 0.88 -> 0.94
                            val t = topState.exitProgress
                            val fromScale = 1f - 0.06f * index
                            val toScale = 1f - 0.06f * (index - 1)
                            val s = lerp(fromScale, toScale, t)
                            scaleX = s; scaleY = s
                            translationY = lerp(18f * index, 18f * (index - 1), t) * density
                            alpha = if (index >= 2) lerp(0.5f, 1f, t) else 1f
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onAction: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.AutoMirrored.Rounded.Undo, contentDescription = "Annuler la dernière action",
                tint = if (canUndo) Neon.TextPrimary else Neon.Outline,
            )
        }
        RoundAction(Icons.Rounded.Close, "Corbeille", Neon.Red, 58.dp) { onAction(SwipeDirection.LEFT) }
        RoundAction(Icons.Rounded.Schedule, "Plus tard", Neon.Amber, 48.dp) { onAction(SwipeDirection.DOWN) }
        RoundAction(Icons.Rounded.Lock, "Coffre-fort", Neon.Magenta, 48.dp) { onAction(SwipeDirection.UP) }
        RoundAction(Icons.Rounded.Check, "Classer", Neon.Lime, 66.dp) { onAction(SwipeDirection.RIGHT) }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, size: Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .background(Neon.Surface, CircleShape)
            .border(2.dp, color, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun EmptyDeck(isScanning: Boolean, scanned: Int, onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isScanning) {
            CircularProgressIndicator(color = Neon.Cyan)
            Text("Analyse en cours...", style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary)
            Text("$scanned fichiers examinés", style = MaterialTheme.typography.labelSmall, color = Neon.TextMuted)
        } else {
            Text("Tout est rangé", style = MaterialTheme.typography.headlineSmall, color = Neon.Lime)
            Text(
                "Aucun fichier en attente. Relance une analyse après tes prochains téléchargements.",
                style = MaterialTheme.typography.bodyMedium, color = Neon.TextMuted, textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.Background),
            ) { Text("Relancer une analyse") }
        }
    }
}

/** Son + vibration propres à chaque action, joués à l'instant du lâcher. */
private fun SortHaptics.forRelease(direction: SwipeDirection) = when (direction) {
    SwipeDirection.RIGHT -> sorted()
    SwipeDirection.UP -> vaulted()
    SwipeDirection.LEFT -> trashed()
    SwipeDirection.DOWN -> skipped()
}

private fun actionMessage(direction: SwipeDirection, name: String): String {
    val short = if (name.length > 28) name.take(25) + "..." else name
    return when (direction) {
        SwipeDirection.RIGHT -> "$short classé"
        SwipeDirection.LEFT -> "$short mis à la corbeille (7 j)"
        SwipeDirection.UP -> "$short placé au coffre-fort"
        SwipeDirection.DOWN -> "$short reporté à demain"
    }
}

private const val VISIBLE_CARDS = 3
private const val PREFETCH_COUNT = 3
private val TARGET_SIZE = 56.dp
private val TARGET_PADDING = 12.dp
private val ACTION_BAR_SPACE = 84.dp
