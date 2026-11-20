package com.sortquest.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.sortquest.app.ui.theme.Neon

/**
 * Cadre commun d'une activité ouverte depuis l'accueil : plein écran, titre et retour,
 * sans barre d'onglets pour laisser toute la place à l'activité en cours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Neon.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // insets déjà gérés par le Scaffold racine
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour à l'accueil") }
                },
                actions = actions,
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Neon.Background,
                    titleContentColor = Neon.TextPrimary,
                    navigationIconContentColor = Neon.TextPrimary,
                    actionIconContentColor = Neon.TextMuted,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
