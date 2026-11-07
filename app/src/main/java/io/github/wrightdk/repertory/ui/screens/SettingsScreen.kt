package io.github.wrightdk.repertory.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.FilmCategory
import io.github.wrightdk.repertory.data.ThemeMode
import io.github.wrightdk.repertory.data.phoneWatchRegion
import io.github.wrightdk.repertory.data.regionDisplayName
import io.github.wrightdk.repertory.data.shows
import io.github.wrightdk.repertory.data.with
import io.github.wrightdk.repertory.ui.components.FilterSwitchRow
import io.github.wrightdk.repertory.ui.components.description
import io.github.wrightdk.repertory.ui.components.label
import io.github.wrightdk.repertory.ui.theme.spacing

/**
 * From design/settings-*.png: the filter defaults every list follows unless it
 * has its own, the theme, and About. Plus, not in the mockup and styled like
 * the About row: back up and restore ("Your data", see [BackupViewModel]) and
 * the own TMDB key; and the "Where to watch" country. [openApiKeyDialog] opens
 * straight onto the key dialog, for the way in from a key error;
 * [openWatchCountryDialog] onto the country picker, from a film's "Where to watch".
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAbout: () -> Unit,
    openApiKeyDialog: Boolean = false,
    openWatchCountryDialog: Boolean = false,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings = container.settings
    val defaults by settings.filterDefaults.collectAsStateWithLifecycle()
    val theme by settings.themeMode.collectAsStateWithLifecycle()
    val ownKey by settings.ownTmdbApiKey.collectAsStateWithLifecycle()
    var keyDialogOpen by rememberSaveable { mutableStateOf(openApiKeyDialog) }
    val watchRegion by settings.watchRegion.collectAsStateWithLifecycle()
    var countryDialogOpen by rememberSaveable { mutableStateOf(openWatchCountryDialog) }
    val locale = LocalConfiguration.current.locales[0]
    val phoneRegion = remember(locale) { phoneWatchRegion(locale) }
    val spacing = MaterialTheme.spacing

    val backup: BackupViewModel = viewModel(factory = BackupViewModel.factory(container, context.contentResolver))
    val backupState by backup.state.collectAsStateWithLifecycle()
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE), backup::save)
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), backup::open)
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(backup) {
        backup.messages.collect { m ->
            val text = when (m) {
                is BackupMessage.Saved -> resources.getString(
                    R.string.backup_saved,
                    resources.getQuantityString(R.plurals.backup_lists, m.lists, m.lists),
                    resources.getQuantityString(R.plurals.backup_films_seen, m.seen, m.seen),
                )
                BackupMessage.SaveFailed -> resources.getString(R.string.backup_save_failed)
                BackupMessage.Restored -> resources.getString(R.string.restored)
                BackupMessage.RestoreFailed -> resources.getString(R.string.restore_failed)
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = { RepertoryTopBar(title = { Text(stringResource(R.string.settings)) }, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
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
            HorizontalDivider(Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.xl))
            Text(
                stringResource(R.string.settings_where_to_watch),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.gutter, bottom = spacing.sm),
            )
            NavigationRow(
                icon = R.drawable.ic_public,
                label = stringResource(R.string.settings_watch_country),
                supporting = watchRegion?.let { regionDisplayName(it, locale) }
                    ?: phoneRegion?.let { stringResource(R.string.settings_watch_country_phone, regionDisplayName(it, locale)) }
                    ?: stringResource(R.string.settings_watch_country_none),
                onClick = { countryDialogOpen = true },
            )
            HorizontalDivider(Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.xl))
            Text(
                stringResource(R.string.settings_your_data),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.gutter, bottom = spacing.sm),
            )
            val idle = backupState == BackupUiState.Idle
            NavigationRow(
                icon = R.drawable.ic_download,
                label = stringResource(R.string.settings_back_up),
                supporting = stringResource(
                    if (backupState == BackupUiState.Saving) R.string.settings_backing_up else R.string.settings_back_up_body,
                ),
                onClick = { saveBackup.launch(resources.getString(R.string.backup_file_name, backup.fileDate())) },
                enabled = idle,
            )
            NavigationRow(
                icon = R.drawable.ic_upload,
                label = stringResource(R.string.settings_restore),
                supporting = stringResource(
                    when (backupState) {
                        BackupUiState.Reading -> R.string.settings_reading_backup
                        BackupUiState.Restoring -> R.string.settings_restoring
                        else -> R.string.settings_restore_body
                    },
                ),
                onClick = { openBackup.launch(BACKUP_OPEN_MIME_TYPES) },
                enabled = idle,
            )
            HorizontalDivider(Modifier.padding(top = spacing.xl))
            NavigationRow(
                icon = R.drawable.ic_key,
                label = stringResource(R.string.settings_own_key),
                supporting = ownKey?.let { stringResource(R.string.settings_own_key_on, it.takeLast(4)) }
                    ?: stringResource(R.string.settings_own_key_off),
                onClick = { keyDialogOpen = true },
            )
            NavigationRow(icon = R.drawable.ic_info, label = stringResource(R.string.about), onClick = onAbout)
        }
    }

    if (keyDialogOpen) {
        OwnApiKeyDialog(
            current = ownKey,
            check = container::checkTmdbApiKey,
            onSave = { key ->
                container.setOwnTmdbApiKey(key)
                keyDialogOpen = false
            },
            onRemove = {
                container.setOwnTmdbApiKey(null)
                keyDialogOpen = false
            },
            onDismiss = { keyDialogOpen = false },
        )
    }

    if (countryDialogOpen) {
        WatchCountryDialog(
            current = watchRegion,
            phoneRegion = phoneRegion,
            loadRegions = container.watchProvidersRepository::regions,
            onChoose = { code ->
                settings.setWatchRegion(code)
                countryDialogOpen = false
            },
            onDismiss = { countryDialogOpen = false },
        )
    }

    when (val state = backupState) {
        is BackupUiState.Confirm -> RestoreBackupDialog(state.backup, onRestore = backup::confirm, onDismiss = backup::dismiss)
        is BackupUiState.Invalid -> InvalidBackupDialog(state.error, onDismiss = backup::dismiss)
        else -> Unit
    }
}

private const val BACKUP_MIME_TYPE = "application/json"

/** Some file managers and cloud drives don't label .json files as JSON, so accept the usual fallbacks too. */
private val BACKUP_OPEN_MIME_TYPES = arrayOf(BACKUP_MIME_TYPE, "text/*", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(selected: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.SYSTEM to R.string.theme_system,
    )
    val c = MaterialTheme.colorScheme
    // SegmentedButton pads itself out to a 48dp touch target but only paints a
    // 40dp surface in the middle of it, which left a band above and below the
    // selected fill inside our outline. Without the padding the fill runs edge
    // to edge, as in the mockup. Each segment is still about a third of the
    // screen wide, so it's an easy target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        // One outline round the whole group, as in the mockup: no dividers between segments.
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                // At least 40dp; taller with large text so the labels aren't cut off.
                .heightIn(min = 40.dp)
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
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp),
                    )
                }
            }
        }
    }
}

/** The mockup's About row: icon, label (and optional supporting line), chevron. */
@Composable
private fun NavigationRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.md)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.8.sp))
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
