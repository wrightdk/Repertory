package io.github.wrightdk.repertory.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palette from design/screens.html. The mockup defines tokens in OKLCH; these are
// the sRGB conversions. Names follow the mockup's CSS variables:
//   bg      page background (warm paper / warm charcoal)
//   sf/sf2  raised surfaces: sheets and dialogs / tracks, pressed states
//   ink     primary text and high-emphasis buttons
//   ink2    secondary text and icons
//   line    hairline rules; line2 outlines
//   ac      the only accent, used for "seen": ticks, progress, the tally
//   warn    ochre, marks unreleased films
//   ph1/ph2 diagonal stripes for missing posters and photos

// Light
internal val LightBg = Color(0xFFF8F5F0)
internal val LightSf = Color(0xFFF1EDE6)
internal val LightSf2 = Color(0xFFE7E2D9)
internal val LightInk = Color(0xFF241E19)
internal val LightInk2 = Color(0xFF5E5650)
internal val LightLine = Color(0xFFDCD7CF)
internal val LightLine2 = Color(0xFFA9A49C)
internal val LightAc = Color(0xFF27694A)
internal val LightAcInk = Color(0xFFF3FBF6)
internal val LightAcSoft = Color(0xFFCEE9D9)
internal val LightAcText = Color(0xFF195E3F)
internal val LightWarn = Color(0xFF825A27)
internal val LightPh1 = Color(0xFFE4DFD7)
internal val LightPh2 = Color(0xFFDCD7CF)
internal val LightDanger = Color(0xFFA04034)
internal val LightDangerInk = Color(0xFFFFF6F4)

// Dark
internal val DarkBg = Color(0xFF1A1713)
internal val DarkSf = Color(0xFF24201B)
internal val DarkSf2 = Color(0xFF2F2A25)
internal val DarkInk = Color(0xFFEBE7DF)
internal val DarkInk2 = Color(0xFFACA79F)
internal val DarkLine = Color(0xFF393530)
internal val DarkLine2 = Color(0xFF67625C)
internal val DarkAc = Color(0xFF80C29F)
internal val DarkAcInk = Color(0xFF091A11)
internal val DarkAcSoft = Color(0xFF253B2F)
internal val DarkAcText = Color(0xFF89CCA8)
internal val DarkWarn = Color(0xFFDBAE7B)
internal val DarkPh1 = Color(0xFF2B2722)
internal val DarkPh2 = Color(0xFF25211D)
internal val DarkDanger = Color(0xFFEBA295)
internal val DarkDangerInk = Color(0xFF2B130F)

// Not in the mockup; derived to sit with the palette (same hue as danger).
internal val LightDangerContainer = Color(0xFFF8DCD6)
internal val LightOnDangerContainer = Color(0xFF5C1E16)
internal val DarkDangerContainer = Color(0xFF4A251F)
internal val DarkOnDangerContainer = Color(0xFFF8DCD6)

// Mockup scrim is rgba(20,14,8,.4); Material applies its own alpha on top of this.
internal val Scrim = Color(0xFF140E08)

/**
 * Colours the design needs that have no Material 3 role.
 * The "seen" green itself is the tertiary role; these are its companions.
 */
@Immutable
data class RepertoryColors(
    /** Green for "Seen 11 Mar 2022" lines and "On list" badges; a touch darker than tertiary for text contrast. */
    val seenText: Color,
    /** Ochre for unreleased films: dashed outlines, "Coming …" badges. */
    val upcoming: Color,
    /** The two stripes of the placeholder shown where a poster or photo is missing. */
    val placeholderStripe: Color,
    val placeholderStripeAlt: Color,
)

internal val LightRepertoryColors = RepertoryColors(
    seenText = LightAcText,
    upcoming = LightWarn,
    placeholderStripe = LightPh1,
    placeholderStripeAlt = LightPh2,
)

internal val DarkRepertoryColors = RepertoryColors(
    seenText = DarkAcText,
    upcoming = DarkWarn,
    placeholderStripe = DarkPh1,
    placeholderStripeAlt = DarkPh2,
)

internal val LocalRepertoryColors = staticCompositionLocalOf { LightRepertoryColors }
