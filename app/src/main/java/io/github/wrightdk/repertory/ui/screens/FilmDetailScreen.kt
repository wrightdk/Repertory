package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FilmDetailScreen(filmId: Int, onBack: () -> Unit) {
    PlaceholderScaffold(title = "Film", onBack = onBack) {
        Text("Film ID: $filmId")
        Text("Mark as seen, date, rating and note go here.")
    }
}
