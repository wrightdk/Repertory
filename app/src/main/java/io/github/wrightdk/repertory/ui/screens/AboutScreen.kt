package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AboutScreen(onBack: () -> Unit) {
    PlaceholderScaffold(title = "About", onBack = onBack) {
        Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
        Text("Licence: GPL-3.0")
    }
}
