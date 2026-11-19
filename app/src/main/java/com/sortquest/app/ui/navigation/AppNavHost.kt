package com.sortquest.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.repository.FileRepository
import com.sortquest.app.domain.usecase.ObserveTrashUseCase
import com.sortquest.app.ui.celebration.CelebrationHost
import com.sortquest.app.ui.clean.CleanScreen
import com.sortquest.app.ui.onboarding.OnboardingScreen
import com.sortquest.app.ui.rules.AutoPilotScreen
import com.sortquest.app.ui.settings.SettingsScreen
import com.sortquest.app.ui.sort.SwipeSortScreen
import com.sortquest.app.ui.theme.Neon
import com.sortquest.app.ui.trash.TrashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
}

/** Les 4 écrans principaux, dans l'ordre de la barre de navigation. */
enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    SORT("sort", "Trier", Icons.Rounded.Style),
    CLEAN("clean", "Nettoyer", Icons.Rounded.CleaningServices),
    AUTOPILOT("autopilot", "Auto-Pilot", Icons.Rounded.AutoMode),
    TRASH("trash", "Corbeille", Icons.Rounded.DeleteOutline),
}

data class NavBadges(val pending: Int = 0, val trash: Int = 0)

/** Pastilles de la barre de navigation (fichiers à trier, éléments en corbeille). */
@HiltViewModel
class MainViewModel @Inject constructor(
    fileRepository: FileRepository,
    observeTrash: ObserveTrashUseCase,
) : ViewModel() {
    val badges: StateFlow<NavBadges> = combine(
        fileRepository.observePendingCount(),
        observeTrash(),
    ) { pending, trash -> NavBadges(pending, trash.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavBadges())
}

@Composable
fun AppNavHost(mainViewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = remember {
        if (StoragePermissionHelper.hasAllFilesAccess(context)) TopLevelDestination.SORT.route else Routes.ONBOARDING
    }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val badges by mainViewModel.badges.collectAsStateWithLifecycle()
    val showBottomBar = TopLevelDestination.entries.any { top -> destination?.hierarchy?.any { it.route == top.route } == true }

    // Accès retiré dans les Paramètres pendant que l'appli était en arrière-plan : retour à l'onboarding
    LifecycleResumeEffect(nav) {
        val current = nav.currentDestination?.route
        if (current != null && current != Routes.ONBOARDING && !StoragePermissionHelper.hasAllFilesAccess(context)) {
            nav.navigate(Routes.ONBOARDING) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        onPauseOrDispose { }
    }

    // Niveaux et badges fêtés par-dessus n'importe quel écran
    CelebrationHost {
        Scaffold(
            containerColor = Neon.Background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = Neon.Surface) {
                        TopLevelDestination.entries.forEach { top ->
                            val selected = destination?.hierarchy?.any { it.route == top.route } == true
                            val count = when (top) {
                                TopLevelDestination.SORT -> badges.pending
                                TopLevelDestination.TRASH -> badges.trash
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.navigateTopLevel(top) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (count > 0) Badge(containerColor = Neon.Magenta) {
                                            Text(if (count > 99) "99+" else "$count")
                                        }
                                    }) { Icon(top.icon, contentDescription = top.label) }
                                },
                                label = { Text(top.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Neon.Background,
                                    selectedTextColor = Neon.Cyan,
                                    indicatorColor = Neon.Cyan,
                                    unselectedIconColor = Neon.TextMuted,
                                    unselectedTextColor = Neon.TextMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onFinished = {
                        // Peut être appelé deux fois (retour des Paramètres + bouton) : on protège
                        if (nav.currentDestination?.route == Routes.ONBOARDING) {
                            nav.navigate(TopLevelDestination.SORT.route) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    })
                }
                composable(TopLevelDestination.SORT.route) {
                    SwipeSortScreen(
                        onOpenRules = { nav.navigateTopLevel(TopLevelDestination.AUTOPILOT) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(TopLevelDestination.CLEAN.route) { CleanScreen() }
                composable(TopLevelDestination.AUTOPILOT.route) { AutoPilotScreen() }
                composable(TopLevelDestination.TRASH.route) { TrashScreen() }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

/**
 * Navigation entre onglets selon les recommandations Material :
 * une seule instance par onglet, état sauvegardé et restauré, pile qui ne grossit pas.
 */
private fun NavHostController.navigateTopLevel(top: TopLevelDestination) {
    navigate(top.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
