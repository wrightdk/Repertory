package io.github.wrightdk.repertory.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii from the mockups. Buttons are full pills (Material's default for Button).
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // posters, text fields, badges
    small = RoundedCornerShape(8.dp), // chips
    medium = RoundedCornerShape(12.dp), // the unreleased-film notice
    large = RoundedCornerShape(16.dp), // extended FAB, app icon on About
    extraLarge = RoundedCornerShape(28.dp), // dialogs, bottom sheets
)
