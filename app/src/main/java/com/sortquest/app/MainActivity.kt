package com.sortquest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sortquest.app.ui.navigation.AppNavHost
import com.sortquest.app.ui.theme.SortQuestTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activité unique. Aucune référence à l'Activity n'est conservée ailleurs
 * (ViewModels Hilt sans Context, Context applicatif dans les singletons) : pas de fuite à la rotation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SortQuestTheme {
                AppNavHost()
            }
        }
    }
}
