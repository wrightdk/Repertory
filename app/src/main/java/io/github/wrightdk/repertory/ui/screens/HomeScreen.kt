package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wrightdk.repertory.appContainer

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onOpenFilmography: () -> Unit,
    onAbout: () -> Unit,
) {
    // Skeleton check that the Room database opens through the manual wiring.
    val context = LocalContext.current
    val countFlow = remember { context.appContainer.database.trackedPersonDao().observeCount() }
    val trackedCount by countFlow.collectAsStateWithLifecycle(initialValue = null)

    PlaceholderScaffold(title = "Repertory", onBack = null) {
        Text("Tracked people in database: ${trackedCount ?: "…"}")
        Button(onClick = onSearch) { Text("Search people") }
        OutlinedButton(onClick = onOpenFilmography) { Text("Open a filmography") }
        OutlinedButton(onClick = onAbout) { Text("About") }
    }
}
