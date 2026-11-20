package com.sortquest.app.ui.optimize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sortquest.app.ui.clean.CleanScreen
import com.sortquest.app.ui.theme.Neon
import com.sortquest.app.ui.trash.TrashScreen

/** Optimisation de l'espace : doublons et fichiers lourds, puis corbeille (récupération ou vidage). */
@Composable
fun OptimizeScreen(onOpenFile: (String) -> Unit, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = Neon.Background, contentColor = Neon.Lime) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Doublons & fichiers lourds") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Corbeille") })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) CleanScreen(onOpenFile = onOpenFile) else TrashScreen(onOpenFile = onOpenFile)
        }
    }
}
