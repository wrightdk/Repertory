package io.github.wrightdk.repertory.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.isRegionCode
import io.github.wrightdk.repertory.data.regionDisplayName
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.ui.components.dashedOutline
import io.github.wrightdk.repertory.ui.components.placeholderStripes
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.spacing

/**
 * "Where to watch", from design/justwatch-feature-*: a hairline, the header
 * with the country (tap it to change country in Settings), then Stream / Rent /
 * Buy rows of provider chips and the TMDB link, or the dashed "nowhere" card.
 * JustWatch's logo and credit close every state, as their data requires.
 */
@Composable
internal fun WhereToWatchSection(
    state: WhereToWatchState,
    onRetry: () -> Unit,
    onChangeCountry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == WhereToWatchState.Hidden) return
    val spacing = MaterialTheme.spacing
    val region = (state as? WhereToWatchState.Shown)?.region?.takeIf(::isRegionCode)
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Header(region, onChangeCountry)
        var afterLink = false
        when (state) {
            WhereToWatchState.Hidden -> Unit
            WhereToWatchState.NoCountry -> Notice(stringResource(R.string.watch_no_country), stringResource(R.string.watch_choose_country), onChangeCountry)
            is WhereToWatchState.Shown -> when (val content = state.content) {
                WhereToWatchContent.Loading -> LoadingRow()
                is WhereToWatchContent.Failed -> LoadFailed(content.error, onRetry)
                WhereToWatchContent.NotCovered -> Notice(
                    stringResource(R.string.watch_not_covered, region?.let { regionName(it) }.orEmpty()),
                    stringResource(R.string.watch_choose_another_country),
                    onChangeCountry,
                )
                is WhereToWatchContent.Loaded -> if (content.isEmpty) {
                    NowhereCard()
                } else {
                    ProviderRows(content)
                    content.link?.let {
                        SeeAllLink(it, Modifier.padding(top = 10.dp))
                        afterLink = true
                    }
                }
            }
        }
        JustWatchCredit(Modifier.padding(top = if (afterLink) spacing.md else spacing.lg))
    }
}

/**
 * "Where to watch" on the left; on the right the country, which opens the
 * country setting, or nothing until one is known. 48dp tall for the tap target,
 * which keeps the mockup's 16dp above and below the text.
 */
@Composable
private fun Header(region: String?, onChangeCountry: () -> Unit) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Text(
            stringResource(R.string.where_to_watch),
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
            color = ink2,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (region != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.watch_change_country), onClick = onChangeCountry),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(regionName(region), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.6.sp), color = ink2)
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = ink2,
                    // Says the country can be changed; the mockup has none, but a bare label wouldn't look tappable.
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun regionName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(code, locale) { regionDisplayName(code, locale) }
}

/** A line of explanation and the way to the country setting: no country yet, or one JustWatch doesn't cover. */
@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.repertoryColors.seenText,
            )
        }
    }
}

/**
 * One row per category with providers. The label sits in a column to the left
 * of the chips, as in the mockup; at large font sizes it goes above them so the
 * chips keep the width.
 */
@Composable
private fun ProviderRows(state: WhereToWatchContent.Loaded) {
    val rows = listOf(
        R.string.watch_stream to state.stream,
        R.string.watch_rent to state.rent,
        R.string.watch_buy to state.buy,
    ).filter { it.second.isNotEmpty() }
    val fontScale = LocalDensity.current.fontScale
    val stacked = fontScale >= STACK_LABELS_AT_FONT_SCALE
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((label, chips) in rows) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    CategoryLabel(stringResource(label))
                    ProviderChips(chips)
                }
            } else {
                Row {
                    Box(
                        Modifier
                            .width(LabelColumnWidth * fontScale)
                            .heightIn(min = ChipHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CategoryLabel(stringResource(label))
                    }
                    ProviderChips(chips, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(text: String) {
    Text(text, style = ChipTextStyle, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ProviderChips(chips: List<WatchProviderChip>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        chips.forEach { ProviderChip(it) }
    }
}

/**
 * Hairline-outlined chip: logo, then the provider's name. Not tappable. The
 * name is printed beside the logo, so the logo itself is silent and a screen
 * reader says the name once.
 */
@Composable
private fun ProviderChip(chip: WatchProviderChip) {
    val colors = MaterialTheme.repertoryColors
    Row(
        modifier = Modifier
            .heightIn(min = ChipHeight)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ChipShape)
            .padding(start = MaterialTheme.spacing.xs, end = MaterialTheme.spacing.md, top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LogoSize)
                .clip(LogoShape)
                .placeholderStripes(colors.placeholderStripe, colors.placeholderStripeAlt),
        ) {
            if (chip.logoUrl != null) {
                AsyncImage(
                    model = chip.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Text(chip.name, style = ChipTextStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** "See all options on TMDB" with the open-in-new icon: TMDB's watch page in the browser. */
@Composable
private fun SeeAllLink(link: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val green = MaterialTheme.repertoryColors.seenText
    Row(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_in_browser)) {
                uriHandler.openSafely(link)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.watch_see_all_on_tmdb), style = MaterialTheme.typography.labelLarge, color = green)
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, tint = green, modifier = Modifier.size(18.dp))
    }
}

/** Dashed card in the providers' place: nowhere to stream, rent or buy it in the region. */
@Composable
private fun NowhereCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(MaterialTheme.colorScheme.outlineVariant, 1.dp, 12.dp)
            .padding(MaterialTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Icon(
            painterResource(R.drawable.ic_tv_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            stringResource(R.string.watch_none),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.3.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One pulsing row the height of a provider row, so little moves when the data arrives. */
@Composable
private fun LoadingRow() {
    val description = stringResource(R.string.watch_loading_description)
    val block = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .skeletonPulse()
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(LabelColumnWidth)) {
            Box(Modifier.size(width = 44.dp, height = 12.dp).background(block, RoundedCornerShape(2.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Box(Modifier.size(width = 96.dp, height = ChipHeight).background(block, ChipShape))
            Box(Modifier.size(width = 120.dp, height = ChipHeight).background(block, ChipShape))
        }
    }
}

/** Nothing cached and the fetch failed: one line and "Try again". */
@Composable
private fun LoadFailed(error: TmdbError, onRetry: () -> Unit) {
    Column {
        Text(
            stringResource(if (error == TmdbError.NoNetwork) R.string.watch_error_offline else R.string.watch_error_other),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(
                stringResource(R.string.try_again),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.repertoryColors.seenText,
            )
        }
    }
}

/**
 * JustWatch's logo and "Availability data from JustWatch, via TMDB.", the
 * whole row one link to JustWatch so it's an easy target. Required wherever
 * their data is shown.
 */
@Composable
private fun JustWatchCredit(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val brand = stringResource(R.string.justwatch)
    val full = stringResource(R.string.watch_attribution, brand)
    val linkStyle = SpanStyle(color = MaterialTheme.repertoryColors.seenText, textDecoration = TextDecoration.Underline)
    val text = remember(full, brand, linkStyle) {
        buildAnnotatedString {
            append(full)
            val start = full.indexOf(brand)
            if (start >= 0) addStyle(linkStyle, start, start + brand.length)
        }
    }
    Row(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_in_browser)) {
                uriHandler.openSafely(JUSTWATCH_URL)
            }
            .padding(vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.justwatch_logo),
            contentDescription = stringResource(R.string.justwatch_logo),
            modifier = Modifier
                .height(JustWatchLogoHeight)
                .aspectRatio(JUSTWATCH_LOGO_ASPECT),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens [uri] in the browser; does nothing if the phone has none. */
private fun UriHandler.openSafely(uri: String) {
    try {
        openUri(uri)
    } catch (_: ActivityNotFoundException) {
    } catch (_: IllegalArgumentException) {
        // Newer Compose wraps "no activity" in this.
    }
}

private const val JUSTWATCH_URL = "https://www.justwatch.com"

/** The logo's viewBox, 415.77 × 67.38. */
private const val JUSTWATCH_LOGO_ASPECT = 415.77f / 67.38f

/** From this font scale on, category labels go above their chips. */
private const val STACK_LABELS_AT_FONT_SCALE = 1.3f

// From the mockup (2px to the dp): 40dp chips with a 32dp logo and 8dp gaps,
// labels in a 64dp column, a 14dp-tall JustWatch logo.
private val ChipHeight = 40.dp
private val LogoSize = 32.dp
private val LabelColumnWidth = 64.dp
private val JustWatchLogoHeight = 14.dp
private val MinTouchTarget = 48.dp
private val ChipShape = RoundedCornerShape(8.dp)
private val LogoShape = RoundedCornerShape(6.dp)

private val ChipTextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp)
