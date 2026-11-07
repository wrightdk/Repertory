package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.ui.components.FilmPoster
import io.github.wrightdk.repertory.ui.components.dashedOutline
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.serif
import io.github.wrightdk.repertory.ui.theme.spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One film, from design/film-detail-* and justwatch-feature-*: poster and
 * credit, the Seen toggle and date, where to watch it, and the lists it counts toward.
 */
@Composable
fun FilmDetailScreen(
    trackedPersonId: Long,
    mediaType: MediaType,
    filmId: Int,
    onBack: () -> Unit,
    onChangeWatchCountry: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: FilmDetailViewModel = viewModel(
        key = "film-$trackedPersonId-${mediaType.name}-$filmId",
        factory = FilmDetailViewModel.factory(context.appContainer, trackedPersonId, mediaType, filmId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val whereToWatch by viewModel.whereToWatch.collectAsStateWithLifecycle()

    Scaffold(topBar = { RepertoryTopBar(title = {}, onBack = onBack) }) { innerPadding ->
        val modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (val s = state) {
            FilmDetailUiState.Loading -> Unit
            FilmDetailUiState.Missing -> Missing(modifier)
            is FilmDetailUiState.Loaded -> FilmDetailContent(
                state = s,
                whereToWatch = whereToWatch,
                onRetryWhereToWatch = viewModel::retryWhereToWatch,
                onChangeWatchCountry = onChangeWatchCountry,
                onToggleSeen = viewModel::toggleSeen,
                onSetSeenOn = viewModel::setSeenOn,
                onToday = viewModel::setSeenToday,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun FilmDetailContent(
    state: FilmDetailUiState.Loaded,
    whereToWatch: WhereToWatchState,
    onRetryWhereToWatch: () -> Unit,
    onChangeWatchCountry: () -> Unit,
    onToggleSeen: () -> Unit,
    onSetSeenOn: (LocalDate?) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = spacing.xxl),
    ) {
        Header(state.film, state.credit)
        Box(Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.xl, bottom = spacing.sm)) {
            when (val seen = state.film.seen) {
                SeenState.NotSeen -> MarkAsSeenButton(onToggleSeen)
                is SeenState.Seen -> SeenButton(onToggleSeen)
                is SeenState.Unreleased -> NotOutYet(seen.due)
            }
        }
        val seen = state.film.seen
        if (seen is SeenState.Seen) {
            DateSeenField(
                date = seen.seenOn,
                onChange = onSetSeenOn,
                onToday = onToday,
                // 16dp to the border in the mockup; the field already reserves 8dp above it for the label.
                modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.sm),
            )
        }
        WhereToWatchSection(
            state = whereToWatch,
            onRetry = onRetryWhereToWatch,
            onChangeCountry = onChangeWatchCountry,
            modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = SectionGap),
        )
        if (state.toward.isNotEmpty()) {
            // The JustWatch credit row already carries 8dp of its touch target below the text.
            val top = if (whereToWatch == WhereToWatchState.Hidden) SectionGap else SectionGap - spacing.sm
            CountsToward(state.toward, Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = top))
        }
    }
}

/** Poster on the left; year and runtime, title and credit line bottom-aligned beside it. */
@Composable
private fun Header(film: FilmDetail, credit: CreditLine?) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        FilmPoster(film.posterUrl, Modifier.size(width = 116.dp, height = 174.dp))
        Column(
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            val runtime = film.runtime?.let { stringResource(R.string.runtime_minutes, it) }
            val meta = when {
                film.year != null && runtime != null -> stringResource(R.string.film_year_runtime, film.year, runtime)
                else -> film.year?.toString() ?: runtime
            }
            if (meta != null) Text(meta, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.2.sp), color = ink2)
            Text(film.title, style = TitleStyle, color = MaterialTheme.colorScheme.onSurface)
            if (credit != null) Text(creditText(credit), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.9.sp), color = ink2)
        }
    }
}

@Composable
private fun creditText(credit: CreditLine): String = when (credit) {
    is CreditLine.Directed -> stringResource(R.string.credit_directed, credit.name)
    is CreditLine.Written -> stringResource(R.string.credit_written, credit.name)
    is CreditLine.Cinematography -> stringResource(R.string.credit_cinematography, credit.name)
    is CreditLine.Music -> stringResource(R.string.credit_music, credit.name)
    is CreditLine.Produced -> stringResource(R.string.credit_produced, credit.name)
    is CreditLine.Edited -> stringResource(R.string.credit_edited, credit.name)
    is CreditLine.Acted -> credit.character?.let { stringResource(R.string.credit_acted, credit.name, it) } ?: credit.name
    is CreditLine.Other -> stringResource(R.string.credit_other, credit.name, credit.job)
}

/** Filled green pill with a white tick: the film is seen. Tapping unticks it. */
@Composable
private fun SeenButton(onClick: () -> Unit) {
    val description = stringResource(R.string.seen_description)
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(ToggleHeight)
            .semantics { contentDescription = description },
    ) {
        ToggleLabel(R.drawable.ic_check_circle, stringResource(R.string.seen))
    }
}

/** Ink-outlined pill with an empty circle: not seen yet. */
@Composable
private fun MarkAsSeenButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .height(ToggleHeight),
    ) {
        ToggleLabel(R.drawable.ic_radio_unchecked, stringResource(R.string.mark_as_seen))
    }
}

@Composable
private fun ToggleLabel(icon: Int, text: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 16.sp))
}

/** Dashed ochre notice in place of the toggle: unreleased films can't be ticked. */
@Composable
private fun NotOutYet(due: LocalDate?) {
    val upcoming = MaterialTheme.repertoryColors.upcoming
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(upcoming, 1.dp, 12.dp)
            .padding(MaterialTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = upcoming, modifier = Modifier.size(22.dp))
        Text(
            text = due?.let { stringResource(R.string.not_out_yet_due, formatDate(it)) }
                ?: stringResource(R.string.not_out_yet_undated),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.3.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * "Date seen (optional)": read-only outlined field that opens a date picker
 * when tapped, with a calendar button and a "Today" shortcut. The picker can
 * also clear the date; the film stays seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSeenField(
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is PressInteraction.Release) pickerOpen = true }
    }
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "ddMMyyyy"), locale)
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedTextField(
        value = date?.format(format).orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.date_seen_optional)) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(
                        painterResource(R.drawable.ic_calendar),
                        contentDescription = stringResource(R.string.choose_date),
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(
                    onClick = onToday,
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md),
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.today),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp),
                        color = MaterialTheme.repertoryColors.seenText,
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ink2,
            unfocusedBorderColor = ink2,
            focusedLabelColor = ink2,
            unfocusedLabelColor = ink2,
            focusedTextColor = ink,
            unfocusedTextColor = ink,
        ),
        interactionSource = interactions,
        modifier = modifier.fillMaxWidth(),
    )

    if (pickerOpen) {
        val today = remember { LocalDate.now() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).toUtcMillis(),
            yearRange = 1888..today.year,
            selectableDates = object : SelectableDates {
                // Can't have seen it in the future.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toUtcMillis()
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(it.toLocalDate()) }
                    pickerOpen = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row {
                    if (date != null) {
                        TextButton(onClick = {
                            onChange(null)
                            pickerOpen = false
                        }) { Text(stringResource(R.string.clear)) }
                    }
                    TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Hairline, "Counts toward", then each list the film counts on with that list's "9 of 13". */
@Composable
private fun CountsToward(entries: List<TowardEntry>, modifier: Modifier = Modifier) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.counts_toward),
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
                color = ink2,
            )
            for (entry in entries) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                ) {
                    Text(
                        stringResource(R.string.list_name, entry.personName, entry.role),
                        style = ListNameStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline(),
                    )
                    if (entry.seen != null && entry.total != null) {
                        Text(
                            stringResource(R.string.list_progress, entry.seen, entry.total),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 13.sp),
                            color = ink2,
                            modifier = Modifier.alignByBaseline(),
                        )
                    } else {
                        // Not final until the list's film details are in; never a provisional number.
                        val loading = stringResource(R.string.progress_loading_description)
                        Box(
                            Modifier
                                .align(Alignment.CenterVertically)
                                .size(width = 44.dp, height = 10.dp)
                                .skeletonPulse()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                                .semantics { contentDescription = loading },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Missing(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.film_missing_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.film_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val ToggleHeight = 56.dp

/** Above each hairline-topped section ("Where to watch", "Counts toward"). */
private val SectionGap = 28.dp

// From the mockup: 14/1.3 Plex meta, 28/1.1 Newsreader title, 14/1.35 Plex credit, 16/1.3 Newsreader list names.
private val TitleStyle = serif(28f, 30.8f, tracking = -0.28f)
private val ListNameStyle = serif(16f, 20.8f)
