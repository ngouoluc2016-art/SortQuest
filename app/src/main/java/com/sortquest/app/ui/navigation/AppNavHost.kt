package com.sortquest.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sortquest.app.core.permission.StoragePermissionHelper
import com.sortquest.app.domain.organization.ThematicCatalog
import com.sortquest.app.domain.usecase.InitializeOrganizationUseCase
import com.sortquest.app.ui.celebration.CelebrationHost
import com.sortquest.app.ui.clean.CleanScreen
import com.sortquest.app.ui.components.ActivityScaffold
import com.sortquest.app.ui.home.HomeActivity
import com.sortquest.app.ui.home.HomeScreen
import com.sortquest.app.ui.onboarding.OnboardingScreen
import com.sortquest.app.ui.organize.FolderContentScreen
import com.sortquest.app.ui.organize.OrganizeScreen
import com.sortquest.app.ui.organize.ThematicFoldersScreen
import com.sortquest.app.ui.rules.HistoryScreen
import com.sortquest.app.ui.rules.RulesScreen
import com.sortquest.app.ui.settings.SettingsScreen
import com.sortquest.app.ui.sort.SwipeSortScreen
import com.sortquest.app.ui.theme.Neon
import com.sortquest.app.ui.trash.TrashScreen
import com.sortquest.app.ui.viewer.FileViewerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ORGANIZE = "organize"
    const val SWIPE = "swipe"
    const val CLEAN = "clean"
    const val TRASH = "trash"
    const val AUTOPILOT = "autopilot"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val FOLDERS = "folders"
    const val VIEWER = "viewer?path={path}"
    const val FOLDER = "folder?dir={dir}"

    fun viewer(path: String) = "viewer?path=" + Uri.encode(path)
    fun folder(dir: String) = "folder?dir=" + Uri.encode(dir)
}

/** Initialise la gamme de dossiers au lancement (destinations des fichiers indexés alignées). */
@HiltViewModel
class MainViewModel @Inject constructor(
    initializeOrganization: InitializeOrganizationUseCase,
) : ViewModel() {
    init {
        viewModelScope.launch { runCatching { initializeOrganization() } }
    }
}

/**
 * Accueil épuré (tuiles) -> chaque activité s'ouvre en plein écran, sans barre d'onglets.
 * Le bouton Retour ramène toujours à l'accueil.
 */
@Composable
fun AppNavHost(@Suppress("UNUSED_PARAMETER") mainViewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = remember {
        if (StoragePermissionHelper.hasAllFilesAccess(context)) Routes.HOME else Routes.ONBOARDING
    }

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

    val back: () -> Unit = { nav.popBackStack() }
    val openFile: (String) -> Unit = { path -> nav.navigate(Routes.viewer(path)) }
    val openFolder: (String) -> Unit = { dir -> nav.navigate(Routes.folder(dir)) }
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    // Niveaux et badges fêtés par-dessus n'importe quel écran
    CelebrationHost {
        Scaffold(containerColor = Neon.Background) { padding ->
            NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onFinished = {
                        // Peut être appelé deux fois (retour des Paramètres + bouton) : on protège
                        if (nav.currentDestination?.route == Routes.ONBOARDING) {
                            nav.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpen = { activity ->
                            when (activity) {
                                HomeActivity.ORGANIZE -> go(Routes.ORGANIZE)
                                HomeActivity.SORTED -> openFolder(ThematicCatalog.BASE)
                                HomeActivity.SWIPE -> go(Routes.SWIPE)
                                HomeActivity.CLEAN -> go(Routes.CLEAN)
                                HomeActivity.TRASH -> go(Routes.TRASH)
                                HomeActivity.AUTOPILOT -> go(Routes.AUTOPILOT)
                                HomeActivity.HISTORY -> go(Routes.HISTORY)
                                HomeActivity.FOLDERS -> go(Routes.FOLDERS)
                            }
                        },
                        onOpenSettings = { go(Routes.SETTINGS) },
                    )
                }
                composable(Routes.ORGANIZE) {
                    ActivityScaffold("Organiser", back) {
                        OrganizeScreen(
                            onOpenFile = openFile,
                            onOpenFolders = { go(Routes.FOLDERS) },
                            onOpenFolder = openFolder,
                            onOpenOptimize = { go(Routes.CLEAN) },
                        )
                    }
                }
                composable(Routes.SWIPE) {
                    ActivityScaffold("Tri express", back) {
                        SwipeSortScreen(
                            onOpenRules = { go(Routes.AUTOPILOT) },
                            onOpenSettings = { go(Routes.SETTINGS) },
                            onOpenFile = openFile,
                        )
                    }
                }
                composable(Routes.CLEAN) {
                    ActivityScaffold("Libérer de l'espace", back) { CleanScreen(onOpenFile = openFile) }
                }
                composable(Routes.TRASH) {
                    ActivityScaffold("Corbeille", back) { TrashScreen(onOpenFile = openFile) }
                }
                composable(Routes.AUTOPILOT) {
                    val snackbar = remember { SnackbarHostState() }
                    ActivityScaffold("Auto-Pilot", back, snackbar) { RulesScreen(snackbar) }
                }
                composable(Routes.HISTORY) {
                    val snackbar = remember { SnackbarHostState() }
                    ActivityScaffold("Historique", back, snackbar) { HistoryScreen(snackbar, openFile) }
                }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
                composable(Routes.FOLDERS) { ThematicFoldersScreen(onBack = back) }
                composable(
                    Routes.VIEWER,
                    arguments = listOf(navArgument("path") { type = NavType.StringType; nullable = true }),
                ) { entry ->
                    FileViewerScreen(path = entry.arguments?.getString("path").orEmpty(), onBack = back)
                }
                composable(
                    Routes.FOLDER,
                    arguments = listOf(navArgument("dir") { type = NavType.StringType; nullable = true }),
                ) {
                    FolderContentScreen(onBack = back, onOpenFile = openFile, onOpenFolder = openFolder)
                }
            }
        }
    }
}
