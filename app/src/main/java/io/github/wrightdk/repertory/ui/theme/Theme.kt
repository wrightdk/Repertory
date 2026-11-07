package io.github.wrightdk.repertory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Role mapping. The design is ink on paper with one accent:
// - primary is ink, so filled buttons, the extended FAB and text buttons are ink.
// - tertiary is the "seen" green, for ticks, progress, checkboxes and switches
//   (pass tertiary colours to those components; Material defaults them to primary).
// - secondaryContainer is the soft green behind selected chips and segments.
// - surface is the page itself; sheets and dialogs sit on sf.
private val LightColors = lightColorScheme(
    primary = LightInk,
    onPrimary = LightBg,
    primaryContainer = LightInk,
    onPrimaryContainer = LightBg,
    inversePrimary = DarkAc,
    secondary = LightInk2,
    onSecondary = LightBg,
    secondaryContainer = LightAcSoft,
    onSecondaryContainer = LightInk,
    tertiary = LightAc,
    onTertiary = LightAcInk,
    tertiaryContainer = LightAcSoft,
    onTertiaryContainer = LightInk,
    background = LightBg,
    onBackground = LightInk,
    surface = LightBg,
    onSurface = LightInk,
    surfaceVariant = LightSf2,
    onSurfaceVariant = LightInk2,
    surfaceTint = Color.Transparent,
    inverseSurface = LightInk,
    inverseOnSurface = LightBg,
    error = LightDanger,
    onError = LightDangerInk,
    errorContainer = LightDangerContainer,
    onErrorContainer = LightOnDangerContainer,
    outline = LightLine2,
    outlineVariant = LightLine,
    scrim = Scrim,
    surfaceBright = LightBg,
    surfaceDim = LightSf2,
    surfaceContainerLowest = LightBg,
    surfaceContainerLow = LightSf,
    surfaceContainer = LightSf,
    surfaceContainerHigh = LightSf,
    surfaceContainerHighest = LightSf2,
)

private val DarkColors = darkColorScheme(
    primary = DarkInk,
    onPrimary = DarkBg,
    primaryContainer = DarkInk,
    onPrimaryContainer = DarkBg,
    inversePrimary = LightAc,
    secondary = DarkInk2,
    onSecondary = DarkBg,
    secondaryContainer = DarkAcSoft,
    onSecondaryContainer = DarkInk,
    tertiary = DarkAc,
    onTertiary = DarkAcInk,
    tertiaryContainer = DarkAcSoft,
    onTertiaryContainer = DarkInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkBg,
    onSurface = DarkInk,
    surfaceVariant = DarkSf2,
    onSurfaceVariant = DarkInk2,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkInk,
    inverseOnSurface = DarkBg,
    error = DarkDanger,
    onError = DarkDangerInk,
    errorContainer = DarkDangerContainer,
    onErrorContainer = DarkOnDangerContainer,
    outline = DarkLine2,
    outlineVariant = DarkLine,
    scrim = Scrim,
    surfaceBright = DarkSf2,
    surfaceDim = DarkBg,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSf,
    surfaceContainer = DarkSf,
    surfaceContainerHigh = DarkSf,
    surfaceContainerHighest = DarkSf2,
)

/**
 * The app theme. No dynamic colour: the warm paper palette is the identity.
 * Light / Dark / System is chosen in Settings; MainActivity passes [darkTheme].
 */
@Composable
fun RepertoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRepertoryColors provides if (darkTheme) DarkRepertoryColors else LightRepertoryColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** Colours beyond Material's roles; see [RepertoryColors]. */
val MaterialTheme.repertoryColors: RepertoryColors
    @Composable @ReadOnlyComposable get() = LocalRepertoryColors.current

val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current
