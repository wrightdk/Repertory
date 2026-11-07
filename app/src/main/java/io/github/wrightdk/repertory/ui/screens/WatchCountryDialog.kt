package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.data.local.entity.WatchRegion
import io.github.wrightdk.repertory.data.regionDisplayName
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.ui.components.DialogBody
import io.github.wrightdk.repertory.ui.components.DialogTextButton
import io.github.wrightdk.repertory.ui.components.RepertoryDialog
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.spacing

/**
 * Picks the "Where to watch" country from the ones JustWatch covers (TMDB's
 * list, cached for months). [current] is the chosen code, or null when
 * following [phoneRegion]; choosing "Your phone's region" sends null. A tap
 * chooses and closes.
 */
@Composable
internal fun WatchCountryDialog(
    current: String?,
    phoneRegion: String?,
    loadRegions: suspend () -> TmdbResult<List<WatchRegion>>,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<TmdbResult<List<WatchRegion>>?>(null, attempt) {
        value = null
        value = loadRegions()
    }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]

    RepertoryDialog(
        title = stringResource(R.string.watch_country_title),
        onDismiss = onDismiss,
        buttons = { DialogTextButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        DialogBody(stringResource(R.string.watch_country_body))
        when (val r = result) {
            null -> Loading()
            is TmdbResult.Failure -> LoadFailed(r.error, onRetry = { attempt++ })
            is TmdbResult.Success -> {
                val regions = r.value
                val options = remember(regions, query, locale) { countryOptions(regions, query, locale) }
                // Offered only when JustWatch covers it, like any other choice.
                val phoneName = phoneRegion
                    ?.takeIf { code -> regions.any { it.code == code } }
                    ?.let { regionDisplayName(it, locale) }
                SearchField(query, onChange = { query = it })
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = ListMaxHeight)
                        .selectableGroup(),
                ) {
                    if (phoneName != null && query.isBlank()) {
                        item(key = "phone") {
                            CountryRow(
                                name = stringResource(R.string.watch_country_phone),
                                supporting = phoneName,
                                selected = current == null,
                                onClick = { onChoose(null) },
                            )
                        }
                    }
                    items(options, key = { it.code }) { option ->
                        CountryRow(option.name, supporting = null, selected = option.code == current, onClick = { onChoose(option.code) })
                    }
                    if (options.isEmpty()) {
                        item(key = "none") {
                            Text(
                                stringResource(R.string.watch_country_no_match, query.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.watch_country_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = ink2) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear), tint = ink2)
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ink2, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Radio button and name; the whole row is the target. */
@Composable
private fun CountryRow(name: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.tertiary),
        )
        Column(Modifier.padding(vertical = MaterialTheme.spacing.sm)) {
            // The dialog sets no content colour of its own, so say it.
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp), color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.watch_country_loading)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun LoadFailed(error: TmdbError, onRetry: () -> Unit) {
    Column {
        Text(
            stringResource(if (error == TmdbError.NoNetwork) R.string.watch_country_error_offline else R.string.watch_country_error_other),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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

private val ListMaxHeight = 320.dp
