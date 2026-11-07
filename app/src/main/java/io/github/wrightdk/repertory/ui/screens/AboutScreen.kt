package io.github.wrightdk.repertory.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.BuildConfig
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.ui.components.placeholderStripes
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.spacing

private const val SOURCE_URL = "https://github.com/wrightdk/Repertory"

/**
 * From design/about-*.png: app name, version and blurb, the licences, the
 * source, and the TMDB attribution their terms require (logo less prominent
 * than ours). The app icon is still a placeholder.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicence: (LicenceKind) -> Unit) {
    val spacing = MaterialTheme.spacing
    val uriHandler = LocalUriHandler.current
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = { RepertoryTopBar(title = { Text(stringResource(R.string.about)) }, onBack = onBack) },
    ) { innerPadding ->
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize()) {
            // Fills the screen when short so the TMDB credit sits at the bottom; scrolls when not.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                Column(
                    modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.xl, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AppIconPlaceholder(Modifier.padding(bottom = spacing.md))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall.copy(lineHeight = 42.sp))
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.2.sp),
                        color = ink2,
                    )
                    Text(
                        stringResource(R.string.about_description),
                        modifier = Modifier.padding(top = spacing.sm),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.25.sp),
                        color = ink2,
                    )
                }
                HorizontalDivider()
                AboutRow(
                    icon = R.drawable.ic_balance,
                    title = stringResource(R.string.about_licence),
                    supporting = stringResource(R.string.about_licence_name),
                    onClick = { onOpenLicence(LicenceKind.APP) },
                )
                AboutRow(
                    icon = R.drawable.ic_text_fields,
                    title = stringResource(R.string.about_fonts),
                    supporting = stringResource(R.string.about_fonts_licence),
                    onClick = { onOpenLicence(LicenceKind.FONTS) },
                )
                AboutRow(
                    icon = R.drawable.ic_code,
                    title = stringResource(R.string.about_source_code),
                    supporting = SOURCE_URL.removePrefix("https://"),
                    onClick = { uriHandler.openUri(SOURCE_URL) },
                    trailing = {
                        Icon(
                            painterResource(R.drawable.ic_open_in_new),
                            contentDescription = stringResource(R.string.open_in_browser),
                            tint = ink2,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                FilmDataCredit(
                    Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.xl, bottom = spacing.gutter),
                )
            }
        }
    }
}

/** 64dp rounded square of placeholder stripes, where the app icon will go. */
@Composable
private fun AppIconPlaceholder(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.repertoryColors
    Box(
        modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .placeholderStripes(colors.placeholderStripe, colors.placeholderStripeAlt),
    )
}

/** Icon, label and supporting line: the mockup's 56dp row inside 12dp padding, so 80dp tall. */
@Composable
private fun AboutRow(
    @DrawableRes icon: Int,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.md)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.8.sp))
            Text(supporting, style = RowSupportingStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** "Film data", the TMDB logo and the attribution line, over a hairline. */
@Composable
private fun FilmDataCredit(modifier: Modifier = Modifier) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 6.dp))
        Text(
            stringResource(R.string.about_film_data),
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
            color = ink2,
        )
        // TMDB's own logo, unaltered, smaller than the app name as their terms ask.
        Image(
            painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.about_tmdb_logo),
        )
        Text(
            stringResource(R.string.about_tmdb_attribution),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.5.sp),
            color = ink2,
        )
    }
}

// Mockup: 13/1.3 for the licence line. (The source line is 14px there, which looks like a slip; both use 13.)
private val RowSupportingStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.9.sp)
