package io.github.wrightdk.repertory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.R

// Two families, both SIL OFL, bundled as variable fonts in res/font (licences in
// third_party/fonts). Newsreader for names and titles, IBM Plex Sans for everything
// else, all in sentence case.

@OptIn(ExperimentalTextApi::class)
private fun plex(weight: FontWeight) = Font(
    resId = R.font.ibm_plex_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val IbmPlexSans = FontFamily(
    plex(FontWeight.Normal),
    plex(FontWeight.Medium),
    plex(FontWeight.SemiBold),
)

/**
 * Newsreader has an optical-size axis. Browsers set it from the font size
 * automatically; Android doesn't, so each style gets a family pinned to its size.
 */
@OptIn(ExperimentalTextApi::class)
private fun newsreader(opticalSize: Float): FontFamily {
    fun font(weight: FontWeight) = Font(
        resId = R.font.newsreader,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.Setting("opsz", opticalSize),
        ),
    )
    return FontFamily(font(FontWeight.Normal), font(FontWeight.Medium))
}

private fun serif(size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(
        fontFamily = newsreader(size),
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

private fun sans(size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp,
    )

// Where each style is used in the mockups (sizes are 1:1, the mockup frame is 412dp wide).
val Typography = Typography(
    // Newsreader
    displayLarge = serif(57f, 60f, tracking = -1.1f), // not in the mockups; kept in scale
    displayMedium = serif(44f, 44f, tracking = -0.9f), // progress count "9" on a filmography
    displaySmall = serif(40f, 42f, tracking = -0.6f), // "Your list", app name on About
    headlineLarge = serif(36f, 40f, tracking = -0.36f), // empty home "Nobody on your list yet."
    headlineMedium = serif(30f, 32f, tracking = -0.3f), // person name on filmography / add person
    headlineSmall = serif(24f, 30f), // sheet, dialog and settings section titles
    titleMedium = serif(20f, 23f, FontWeight.Medium), // person name in home rows
    titleSmall = serif(17f, 21f, FontWeight.Medium), // film titles in lists, home wordmark

    // IBM Plex Sans
    titleLarge = sans(17f, 22f, FontWeight.Medium), // top app bar titles ("About", "Settings")
    bodyLarge = sans(16f, 24f), // list item labels, prose
    bodyMedium = sans(14f, 20f), // supporting lines, years, blurbs
    bodySmall = sans(13f, 19f), // row metadata, attribution
    labelLarge = sans(15f, 20f, FontWeight.Medium), // buttons, extended FAB
    labelMedium = sans(13f, 16f, FontWeight.Medium), // section labels ("Suggestions"), chips
    labelSmall = sans(12f, 16f, FontWeight.Medium), // badges ("On list", "Coming 26 Nov")
)
