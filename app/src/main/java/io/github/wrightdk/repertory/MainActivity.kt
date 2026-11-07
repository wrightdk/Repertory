package io.github.wrightdk.repertory

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.navigation.RepertoryNavHost
import io.github.wrightdk.repertory.ui.theme.RepertoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = appContainer.settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status and navigation bar icons follow the app's theme, not the system's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RepertoryTheme(darkTheme = dark) {
                RepertoryNavHost()
            }
        }
    }
}
