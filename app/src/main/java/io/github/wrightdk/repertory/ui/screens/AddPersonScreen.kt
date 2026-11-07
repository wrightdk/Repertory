package io.github.wrightdk.repertory.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AddPersonScreen(personId: Int, onBack: () -> Unit, onAdded: () -> Unit) {
    PlaceholderScaffold(title = "Add person", onBack = onBack) {
        Text("Person ID: $personId")
        Text("Role picker (Directing, Acting, Writing…) goes here.")
        Button(onClick = onAdded) { Text("Add and open filmography") }
    }
}
