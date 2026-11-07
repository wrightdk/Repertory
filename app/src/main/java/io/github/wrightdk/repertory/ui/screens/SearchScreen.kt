package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SearchScreen(onBack: () -> Unit, onPersonSelected: () -> Unit) {
    PlaceholderScaffold(title = "Search people", onBack = onBack) {
        Text("Person search via TMDB goes here.")
        Button(onClick = onPersonSelected) { Text("Pick a sample person") }
    }
}
