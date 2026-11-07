package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.FilmCategory
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.shows
import io.github.wrightdk.repertory.data.with
import io.github.wrightdk.repertory.ui.components.FilterSwitchRow
import io.github.wrightdk.repertory.ui.components.description
import io.github.wrightdk.repertory.ui.components.label
import io.github.wrightdk.repertory.ui.theme.spacing

/**
 * From design/settings-*.png: the filter defaults every list follows unless it
 * has its own, the theme, and About. The own-TMDB-key row comes later.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val settings = LocalContext.current.appContainer.settings
    val defaults by settings.filterDefaults.collectAsStateWithLifecycle()
    val theme by settings.themeMode.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing

    Scaffold(
        topBar = { RepertoryTopBar(title = { Text(stringResource(R.string.settings)) }, onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = spacing.xl),
        ) {
            Column(
                modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.gutter, bottom = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.settings_show_in_lists), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.settings_show_in_lists_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (category in FilmCategory.entries) {
                FilterSwitchRow(
                    label = stringResource(category.label),
                    supporting = stringResource(category.description),
                    checked = defaults.shows(category),
                    onCheckedChange = { settings.setFilterDefaults(defaults.with(category, it)) },
                    horizontalPadding = spacing.gutter,
                )
            }
            HorizontalDivider(Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.gutter))
            Column(
                modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.headlineSmall)
                ThemePicker(theme, onPick = settings::setThemeMode)
            }
            HorizontalDivider(Modifier.padding(top = spacing.xl))
            AboutRow(onAbout)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(selected: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.SYSTEM to R.string.theme_system,
    )
    val c = MaterialTheme.colorScheme
    // One outline round the whole group, as in the mockup: no dividers between segments.
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, c.outline, CircleShape),
    ) {
        for ((mode, label) in options) {
            SegmentedButton(
                selected = mode == selected,
                onClick = { onPick(mode) },
                shape = RectangleShape,
                border = BorderStroke(0.dp, Color.Transparent),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.secondaryContainer,
                    activeContentColor = c.onSurface,
                    inactiveContainerColor = c.surface,
                    inactiveContentColor = c.onSurface,
                    activeBorderColor = c.outline,
                    inactiveBorderColor = c.outline,
                ),
                icon = { SegmentedButtonDefaults.Icon(active = mode == selected) },
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun AboutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.md)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
