package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FilmographyScreen(trackedPersonId: Long, onBack: () -> Unit, onFilmSelected: () -> Unit) {
    PlaceholderScaffold(title = "Filmography", onBack = onBack) {
        Text("Tracked person ID: $trackedPersonId")
        Text("Film list, filters and progress go here.")
        Button(onClick = onFilmSelected) { Text("Open a sample film") }
    }
}
