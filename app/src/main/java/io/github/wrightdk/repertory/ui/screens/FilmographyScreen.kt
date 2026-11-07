package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.FilmCategory
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.ui.components.FilmPoster
import io.github.wrightdk.repertory.ui.components.PersonPhoto
import io.github.wrightdk.repertory.ui.components.dashedOutline
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.Scrim
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.serif
import io.github.wrightdk.repertory.ui.theme.spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One list's filmography, from design/person-filmography-*, filmography-actor-* and filmography-unreleased-*. */
@Composable
fun FilmographyScreen(
    trackedPersonId: Long,
    onBack: () -> Unit,
    onFilmSelected: (mediaType: MediaType, filmId: Int) -> Unit,
    /** After removing the list; [message] is for Home's snackbar ("Removed Michael Caine as Actor"). */
    onRemoved: (message: String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: FilmographyViewModel = viewModel(
        key = "filmography-$trackedPersonId",
        factory = FilmographyViewModel.factory(context.appContainer, trackedPersonId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var removeDialogOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        // collectLatest: a new tick replaces the snackbar showing, rather than queueing behind it.
        viewModel.tickedOff.collectLatest { t ->
            val message = when {
                // Count not final yet: say what was ticked, not a number that may change.
                t.seen == null || t.total == null -> resources.getString(R.string.ticked_off_no_count, t.title)
                t.seen == t.total -> resources.getString(R.string.ticked_off_all, t.title, t.total)
                else -> resources.getString(R.string.ticked_off, t.title, t.seen, t.total)
            }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            RepertoryTopBar(
                title = {},
                onBack = onBack,
                actions = {
                    if (state.body != FilmographyBody.Missing) {
                        OnYourListButton(onClick = { if (state.header != null) removeDialogOpen = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        FilmographyList(
            state = state,
            contentPadding = innerPadding,
            onRetryDetails = viewModel::retryDetails,
            onOpenFilters = { filterSheetOpen = true },
            onToggleSeen = viewModel::toggleSeen,
            onOpen = { onFilmSelected(it.mediaType, it.tmdbId) },
            onRetry = viewModel::retry,
            modifier = Modifier.consumeWindowInsets(innerPadding),
        )
    }

    val loaded = state.body as? FilmographyBody.Loaded
    if (filterSheetOpen && loaded != null) {
        FilterSheet(
            content = loaded.content,
            onSetShown = viewModel::setShown,
            onUseDefaults = viewModel::useDefaults,
            onDismiss = { filterSheetOpen = false },
        )
    }

    val header = state.header
    if (removeDialogOpen && header != null) {
        RemoveListDialog(
            name = header.name,
            role = header.role,
            onConfirm = {
                removeDialogOpen = false
                viewModel.remove()
                onRemoved(resources.getString(R.string.removed_list, header.name, header.role))
            },
            onDismiss = { removeDialogOpen = false },
        )
    }
}

/**
 * "Remove Michael Caine as Actor from your list?", from design/remove-person-*.
 * The body says ticked films stay saved, unlike the mockup: removing a list
 * never deletes viewings.
 */
@Composable
private fun RemoveListDialog(name: String, role: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Draw the mockup's warm scrim (as the filter sheet does) in place of the platform's black dim.
        // The window is edge to edge, so it also sets the status bar icons: keep them as the screen has them.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            window?.setDimAmount(0f)
            window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = lightBars }
        }
        val title = stringResource(R.string.remove_list_title, name, role)
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Scrim.copy(alpha = DialogScrimAlpha))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                    .clearAndSetSemantics {},
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = MaterialTheme.spacing.xl, end = MaterialTheme.spacing.xl, top = MaterialTheme.spacing.xl, bottom = MaterialTheme.spacing.lg)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.remove_list_body),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm, Alignment.End),
                ) {
                    val buttonText = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp)
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.lg),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.height(40.dp),
                    ) { Text(stringResource(R.string.cancel), style = buttonText) }
                    Button(
                        onClick = onConfirm,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.height(40.dp),
                    ) { Text(stringResource(R.string.remove_list_confirm), style = buttonText) }
                }
            }
        }
    }
}

private const val DialogScrimAlpha = 0.4f

@Composable
private fun FilmographyList(
    state: FilmographyUiState,
    contentPadding: PaddingValues,
    onRetryDetails: () -> Unit,
    onOpenFilters: () -> Unit,
    onToggleSeen: (FilmRow) -> Unit,
    onOpen: (FilmRow) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.xl,
        ),
    ) {
        if (state.body != FilmographyBody.Missing) item(key = "header") { Header(state.header) }
        when (val body = state.body) {
            FilmographyBody.Loading -> item(key = "loading") { Loading() }
            is FilmographyBody.Error -> item(key = "error") {
                HorizontalDivider(Modifier.padding(horizontal = MaterialTheme.spacing.gutter), color = MaterialTheme.colorScheme.onSurface)
                LoadError(body.error, onRetry)
            }
            FilmographyBody.Missing -> item(key = "missing") {
                Message(stringResource(R.string.filmography_missing_title), stringResource(R.string.filmography_missing_body))
            }
            is FilmographyBody.Loaded -> {
                val content = body.content
                item(key = "progress") { Progress(content, body.details, onRetryDetails, onOpenFilters) }
                if (content.items.isEmpty()) {
                    item(key = "empty") {
                        Message(
                            stringResource(R.string.filmography_nothing_shown_title),
                            stringResource(R.string.filmography_nothing_shown_body),
                        )
                    }
                }
                items(
                    content.items,
                    key = { it.key },
                    contentType = { if (it is FilmographyItem.Decade) "decade" else "film" },
                ) { item ->
                    when (item) {
                        is FilmographyItem.Decade -> DecadeHeader(item.decade)
                        is FilmographyItem.Film -> {
                            FilmRowItem(item.row, onToggle = { onToggleSeen(item.row) }, onOpen = { onOpen(item.row) })
                        }
                    }
                }
            }
        }
    }
}

/** The outlined pill in the top bar. Opens the remove dialog. */
@Composable
private fun OnYourListButton(onClick: () -> Unit) {
    val description = stringResource(R.string.on_your_list_description)
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
        modifier = Modifier
            .padding(end = MaterialTheme.spacing.sm)
            .height(36.dp)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.on_your_list),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Photo, name, and role with life dates, bottom-aligned. Skeleton until the person is cached. */
@Composable
private fun Header(header: FilmographyHeader?) {
    Row(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.gutter,
            end = MaterialTheme.spacing.gutter,
            bottom = MaterialTheme.spacing.gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (header == null) {
            val block = MaterialTheme.colorScheme.surfaceVariant
            Box(Modifier.size(PhotoWidth, PhotoHeight).skeletonPulse().background(block, RoundedCornerShape(3.dp)))
            Column(Modifier.skeletonPulse(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                Box(Modifier.size(width = 200.dp, height = 26.dp).background(block, SkeletonBarShape))
                Box(Modifier.size(width = 120.dp, height = 12.dp).background(block, SkeletonBarShape))
            }
        } else {
            PersonPhoto(name = header.name, url = header.photoUrl, modifier = Modifier.size(PhotoWidth, PhotoHeight))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(header.name, style = NameStyle)
                Text(
                    header.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Ink rule, "9 of 13 … 69%", the tally of cells, the filter summary, hairline.
 * Until every film's details are in, the count and tally are skeletons and a
 * status line takes the filter summary's place: none of them would be final.
 */
@Composable
private fun Progress(
    content: FilmographyContent,
    details: DetailsStatus,
    onRetryDetails: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    Column(Modifier.padding(horizontal = MaterialTheme.spacing.gutter)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            if (details == DetailsStatus.Complete) {
                Count(content)
                Ledger(content.ledger)
                FilterSummaryLine(content.filterSummary, onOpenFilters)
            } else {
                CountSkeleton(pulsing = details == DetailsStatus.Loading)
                DetailsStatusLine(details, onRetryDetails)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Count(content: FilmographyContent) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val progressDescription = stringResource(R.string.progress_description, content.seen, content.total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = progressDescription },
    ) {
        Text(
            content.seen.toString(),
            style = MaterialTheme.typography.displayMedium.copy(lineHeight = 44.sp),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            stringResource(R.string.progress_of, content.total),
            style = OfStyle,
            color = ink2,
            modifier = Modifier
                .padding(start = MaterialTheme.spacing.sm)
                .alignByBaseline(),
        )
        Spacer(Modifier.weight(1f))
        Text(
            content.percent?.let { stringResource(R.string.progress_percent, it) }
                ?: stringResource(R.string.progress_percent_none),
            style = OfStyle,
            color = ink2,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** Blocks where the count, percentage and tally go, the same height as the real thing. */
@Composable
private fun CountSkeleton(pulsing: Boolean) {
    val block = MaterialTheme.colorScheme.surfaceVariant
    val loading = stringResource(R.string.progress_loading_description)
    Column(
        modifier = Modifier
            .then(if (pulsing) Modifier.skeletonPulse() else Modifier)
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(width = 28.dp, height = 36.dp).background(block, SkeletonBarShape))
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Box(Modifier.size(width = 64.dp, height = 18.dp).background(block, SkeletonBarShape))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 44.dp, height = 18.dp).background(block, SkeletonBarShape))
        }
        Box(Modifier.fillMaxWidth(0.6f).height(18.dp).background(block, CellShape))
    }
}

/** In the filter summary's place while details load: what's happening, or that it stopped and a retry. */
@Composable
private fun DetailsStatusLine(details: DetailsStatus, onRetry: () -> Unit) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    when (details) {
        DetailsStatus.Complete -> Unit
        DetailsStatus.Loading -> Text(
            stringResource(R.string.fetching_film_details),
            style = MetaStyle,
            color = ink2,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs),
        )
        is DetailsStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Text(
                stringResource(R.string.film_details_failed_title),
                style = MetaStyle.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(details.error.detailsFailedBody), style = MetaStyle, color = ink2)
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.try_again), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            }
        }
    }
}

private val TmdbError.detailsFailedBody: Int
    get() = when (this) {
        TmdbError.NoNetwork -> R.string.film_details_failed_offline
        TmdbError.InvalidApiKey -> R.string.search_error_key_body
        TmdbError.RateLimited -> R.string.search_error_busy_body
        else -> R.string.filmography_error_other_body
    }

/** One small cell per film shown, in list order, wrapping as needed. */
@Composable
private fun Ledger(cells: List<LedgerCell>) {
    if (cells.isEmpty()) return
    val seen = MaterialTheme.colorScheme.tertiary
    val unseen = MaterialTheme.colorScheme.outline
    val upcoming = MaterialTheme.repertoryColors.upcoming
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        for (cell in cells) {
            val m = Modifier.size(width = 9.dp, height = 18.dp)
            when (cell) {
                LedgerCell.SEEN -> Box(m.background(seen, CellShape))
                LedgerCell.UNSEEN -> Box(m.border(1.5.dp, unseen, CellShape))
                LedgerCell.UNRELEASED -> Box(m.dashedOutline(upcoming, 1.5.dp, 2.dp, dash = 3.dp, gap = 2.dp))
            }
        }
    }
}

/** "Hiding shorts", "Custom list: showing everything". Opens the "Show in this list" sheet. */
@Composable
private fun FilterSummaryLine(summary: FilterSummary, onClick: () -> Unit) {
    val hiding = summary.hiding.map { stringResource(it.hiddenLabel) }
    val list = joinWithAnd(hiding)
    val text = when {
        summary.custom && list != null -> stringResource(R.string.filter_custom_hiding, list)
        summary.custom -> stringResource(R.string.filter_custom_showing_everything)
        list != null -> stringResource(R.string.filter_hiding, list)
        else -> stringResource(R.string.filter_showing_everything)
    }
    val action = stringResource(R.string.filter_summary_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_filter_list),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "shorts", "TV, appearances as self and minor roles"; null when empty. */
@Composable
private fun joinWithAnd(items: List<String>): String? = when (items.size) {
    0 -> null
    1 -> items.single()
    else -> stringResource(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())
}

private val FilmCategory.hiddenLabel: Int
    get() = when (this) {
        FilmCategory.SHORT -> R.string.filter_hide_shorts
        FilmCategory.TV -> R.string.filter_hide_tv
        FilmCategory.DOCUMENTARY -> R.string.filter_hide_documentaries
        FilmCategory.SELF -> R.string.filter_hide_self
        FilmCategory.MINOR_ROLE -> R.string.filter_hide_minor_roles
    }

private val FilmCategory.metaLabel: Int
    get() = when (this) {
        FilmCategory.SHORT -> R.string.category_short
        FilmCategory.TV -> R.string.category_tv
        FilmCategory.DOCUMENTARY -> R.string.category_documentary
        FilmCategory.SELF -> R.string.category_self
        FilmCategory.MINOR_ROLE -> R.string.category_minor_role
    }

/** "1950s" and a hairline to the right edge. */
@Composable
private fun DecadeHeader(decade: Int?) {
    Row(
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.gutter,
            end = MaterialTheme.spacing.gutter,
            top = MaterialTheme.spacing.gutter,
            bottom = 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Text(
            if (decade != null) stringResource(R.string.decade, decade) else stringResource(R.string.decade_undated),
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Year, poster, title with its meta and seen/unreleased line, and the tick circle. */
@Composable
private fun FilmRowItem(row: FilmRow, onToggle: () -> Unit, onOpen: () -> Unit) {
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = MaterialTheme.spacing.gutter, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            row.year?.toString().orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 14.sp),
            color = ink2,
            modifier = Modifier.width(34.dp),
        )
        FilmPoster(row.posterUrl, Modifier.size(width = 40.dp, height = 60.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(row.title, style = TitleStyle, color = MaterialTheme.colorScheme.onSurface)
            val meta = metaLine(row)
            if (meta != null) {
                Text(meta, style = MetaStyle, color = ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                row.seen -> Text(
                    text = row.seenOn?.let { stringResource(R.string.seen_on, formatDate(it)) } ?: stringResource(R.string.seen),
                    style = MetaStyle.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.repertoryColors.seenText,
                )
                row.unreleased -> UnreleasedBadge(row.dueDate)
            }
        }
        TickCircle(row, onToggle)
    }
}

/** "Short, as Harry Palmer", "Documentary, 85 min", or null when there's nothing to say. */
@Composable
private fun metaLine(row: FilmRow): String? {
    val parts = buildList {
        row.categories.forEach { add(stringResource(it.metaLabel)) }
        when {
            row.character != null -> add(stringResource(R.string.as_character, row.character))
            row.runtime != null -> add(stringResource(R.string.runtime_minutes, row.runtime))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

@Composable
private fun UnreleasedBadge(due: LocalDate?) {
    val upcoming = MaterialTheme.repertoryColors.upcoming
    Text(
        text = due?.let { stringResource(R.string.unreleased_due, formatDate(it)) } ?: stringResource(R.string.unreleased),
        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
        color = upcoming,
        modifier = Modifier
            .border(1.dp, upcoming, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = MaterialTheme.spacing.xs),
    )
}

/** 48dp target around a 28dp circle: green with a tick when seen, ink2 outline when not, dashed ochre when unreleased. */
@Composable
private fun TickCircle(row: FilmRow, onToggle: () -> Unit) {
    val circle = Modifier.size(28.dp)
    if (row.unreleased) {
        val notOut = stringResource(R.string.not_out_yet)
        Box(
            Modifier.size(48.dp).semantics { contentDescription = notOut },
            contentAlignment = Alignment.Center,
        ) {
            Box(circle.dashedOutline(MaterialTheme.repertoryColors.upcoming, 2.dp, 14.dp, dash = 6.dp, gap = 4.dp))
        }
        return
    }
    val label = stringResource(if (row.seen) R.string.mark_as_not_seen else R.string.mark_as_seen)
    IconButton(onClick = onToggle, modifier = Modifier.semantics { contentDescription = label }) {
        if (row.seen) {
            Box(
                circle.background(MaterialTheme.colorScheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(circle.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

/** "Fetching filmography from TMDB…" over skeleton rows. */
@Composable
private fun Loading() {
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column {
        HorizontalDivider(Modifier.padding(horizontal = MaterialTheme.spacing.gutter), color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.fetching_filmography),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.gutter,
                end = MaterialTheme.spacing.gutter,
                top = MaterialTheme.spacing.lg,
                bottom = MaterialTheme.spacing.sm,
            ),
        )
        Column(Modifier.skeletonPulse()) {
            repeat(SKELETON_ROWS) {
                Row(
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.gutter, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(width = 34.dp, height = 12.dp).background(block, SkeletonBarShape))
                    Box(Modifier.size(width = 40.dp, height = 60.dp).background(block, RoundedCornerShape(3.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(block, SkeletonBarShape))
                        Box(Modifier.fillMaxWidth(0.3f).height(10.dp).background(block, SkeletonBarShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadError(error: TmdbError, onRetry: () -> Unit) {
    val (title, body) = when (error) {
        TmdbError.NoNetwork -> R.string.search_error_offline_title to R.string.search_error_offline_body
        TmdbError.InvalidApiKey -> R.string.search_error_key_title to R.string.search_error_key_body
        TmdbError.RateLimited -> R.string.search_error_busy_title to R.string.search_error_busy_body
        TmdbError.NotFound -> R.string.add_person_error_missing_title to R.string.add_person_error_missing_body
        else -> R.string.filmography_error_other_title to R.string.filmography_error_other_body
    }
    Message(title = stringResource(title), body = stringResource(body)) {
        if (error != TmdbError.NotFound) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
                Text(stringResource(R.string.try_again))
            }
        }
    }
}

/** "11 Mar 2022". English month abbreviations to match the rest of the (English-only) copy. */
private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun formatDate(date: LocalDate): String = date.format(DateFormat)

// Mockup values that fall between the theme's steps.
private val NameStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineMedium.copy(lineHeight = 31.5.sp)

private val OfStyle: TextStyle = serif(22f, 22f)

private val TitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleSmall.copy(lineHeight = 20.7.sp)

private val MetaStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.9.sp)

private val PhotoWidth = 72.dp
private val PhotoHeight = 92.dp
private val CellShape = RoundedCornerShape(2.dp)
private val SkeletonBarShape = RoundedCornerShape(2.dp)
private const val SKELETON_ROWS = 6
