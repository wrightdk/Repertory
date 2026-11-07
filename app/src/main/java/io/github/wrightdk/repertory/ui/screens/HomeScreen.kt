package io.github.wrightdk.repertory.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.Suggestion
import io.github.wrightdk.repertory.ui.components.PersonPhoto
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.serif
import io.github.wrightdk.repertory.ui.theme.spacing
import java.time.LocalDate
import java.time.ZoneId

/** "Your list", from design/home-*.png, and the first-launch screen from design/home-firstlaunch-*.png. */
@Composable
fun HomeScreen(
    onAddPerson: () -> Unit,
    onOpenList: (trackedPersonId: Long) -> Unit,
    onSuggestion: (personId: Int) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context.appContainer))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listQuery by viewModel.listQuery.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        listQuery = listQuery,
        onListQuery = viewModel::setListQuery,
        onOpenListSearch = viewModel::openListSearch,
        onCloseListSearch = viewModel::closeListSearch,
        onSort = viewModel::setSort,
        onAddPerson = onAddPerson,
        onOpenList = onOpenList,
        onSuggestion = onSuggestion,
        onSettings = onSettings,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    listQuery: String?,
    onListQuery: (String) -> Unit,
    onOpenListSearch: () -> Unit,
    onCloseListSearch: () -> Unit,
    onSort: (HomeSort) -> Unit,
    onAddPerson: () -> Unit,
    onOpenList: (Long) -> Unit,
    onSuggestion: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val content = state.content
    val hasLists = content is HomeContent.Lists
    val searching = listQuery != null && hasLists
    BackHandler(enabled = searching, onBack = onCloseListSearch)

    Scaffold(
        topBar = {
            if (searching) {
                RepertoryTopBar(
                    title = { ListSearchField(listQuery.orEmpty(), onListQuery) },
                    onBack = onCloseListSearch,
                    backDescription = stringResource(R.string.close_search),
                    actions = { SettingsButton(onSettings) },
                )
            } else {
                RepertoryTopBar(
                    // The wordmark is set in Newsreader, unlike other screen titles.
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall) },
                    onBack = null,
                    actions = {
                        if (hasLists) {
                            IconButton(onClick = onOpenListSearch) {
                                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search_your_list))
                            }
                        }
                        SettingsButton(onSettings)
                    },
                )
            }
        },
        floatingActionButton = {
            if (hasLists) {
                ExtendedFloatingActionButton(
                    onClick = onAddPerson,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.add_person), style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            when (content) {
                HomeContent.Loading -> Unit
                is HomeContent.Empty -> EmptyHome(
                    suggestions = content.suggestions,
                    onFindSomeone = onAddPerson,
                    onSuggestion = onSuggestion,
                    bottomPadding = innerPadding.calculateBottomPadding(),
                )
                is HomeContent.Lists -> YourList(
                    content = content,
                    sort = state.sort,
                    query = listQuery,
                    onSort = onSort,
                    onOpenList = onOpenList,
                    bottomPadding = innerPadding.calculateBottomPadding(),
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(onSettings: () -> Unit) {
    IconButton(onClick = onSettings) {
        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
    }
}

/** "Search your list", in place of the wordmark. Opens with the keyboard up. */
@Composable
private fun ListSearchField(query: String, onQuery: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        // The list filters as you type; the search key just gets the keyboard out of the way.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_your_list),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun YourList(
    content: HomeContent.Lists,
    sort: HomeSort,
    query: String?,
    onSort: (HomeSort) -> Unit,
    onOpenList: (Long) -> Unit,
    bottomPadding: Dp,
) {
    val spacing = MaterialTheme.spacing
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = spacing.gutter, end = spacing.gutter, top = spacing.lg)) {
            Text(stringResource(R.string.your_list), style = MaterialTheme.typography.displaySmall)
            // The 32dp chips sit in 48dp touch targets: take the 8dp overhang off the
            // mockup's 20dp above and 16dp below.
            SortChips(sort, onSort, Modifier.padding(top = spacing.gutter - ChipTouchOverhang))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room under the last row for the "Add person" button.
            contentPadding = PaddingValues(top = spacing.lg - ChipTouchOverhang, bottom = bottomPadding + 100.dp),
        ) {
            items(content.rows, key = { it.trackedPersonId }) { row ->
                ListRow(
                    row = row,
                    recency = if (sort == HomeSort.RECENT) recency(row.updatedAt, today, zone) else null,
                    onClick = { onOpenList(row.trackedPersonId) },
                )
            }
            if (content.rows.isEmpty() && !query.isNullOrBlank()) {
                item(key = "no-match") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.home_no_match, query.trim()),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.xl),
                    )
                }
            }
            item(key = "end") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
    }
}

/** The sort icon, then Name / Progress / Recent; one is always on. */
@Composable
private fun SortChips(sort: HomeSort, onSort: (HomeSort) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_sort),
            contentDescription = stringResource(R.string.sort_by),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = MaterialTheme.spacing.xxs).size(22.dp),
        )
        for (option in HomeSort.entries) {
            SortChip(
                label = stringResource(
                    when (option) {
                        HomeSort.NAME -> R.string.sort_name
                        HomeSort.PROGRESS -> R.string.sort_progress
                        HomeSort.RECENT -> R.string.sort_recent
                    },
                ),
                selected = option == sort,
                onClick = { onSort(option) },
            )
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(start = if (selected) 8.dp else 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Photo, name, role, "9 of 40 seen" and a thin progress bar, over a hairline. */
@Composable
private fun ListRow(row: HomeRow, recency: Recency?, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val metaStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.gutter, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonPhoto(name = row.name.orEmpty(), url = row.photoUrl, modifier = Modifier.size(52.dp, 66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.name != null) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Box(
                        Modifier
                            .height(23.dp)
                            .padding(vertical = 4.dp)
                            .width(160.dp)
                            .skeletonPulse()
                            .background(MaterialTheme.colorScheme.surfaceVariant, SkeletonShape),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (recency == null) row.role else stringResource(R.string.role_updated, row.role, recencyText(recency)),
                        style = metaStyle,
                        color = ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SeenCount(row, metaStyle)
                }
                ProgressBar(row)
            }
        }
    }
}

@Composable
private fun SeenCount(row: HomeRow, style: TextStyle) {
    val progress = row.progress
    when {
        progress == null -> {
            val loading = stringResource(R.string.progress_loading_description)
            Box(
                Modifier
                    .then(if (row.detailsFailed) Modifier else Modifier.skeletonPulse())
                    .clearAndSetSemantics { contentDescription = loading }
                    .padding(vertical = 2.dp)
                    .size(width = 72.dp, height = 13.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, SkeletonShape),
            )
        }
        progress.done -> Text(
            stringResource(R.string.home_all_seen, progress.total),
            style = style.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.repertoryColors.seenText,
            maxLines = 1,
        )
        else -> Text(
            stringResource(R.string.home_seen_count, progress.seen, progress.total),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 4dp track in sf2, filled in the "seen" green. While loading, an empty track that pulses. */
@Composable
private fun ProgressBar(row: HomeRow) {
    val progress = row.progress
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .then(if (progress == null && !row.detailsFailed) Modifier.skeletonPulse() else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant, BarShape),
    ) {
        if (progress != null && progress.fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.fraction)
                    .background(MaterialTheme.colorScheme.tertiary, BarShape),
            )
        }
    }
}

@Composable
private fun recencyText(recency: Recency): String = when (recency) {
    Recency.Today -> stringResource(R.string.updated_today)
    Recency.Yesterday -> stringResource(R.string.updated_yesterday)
    is Recency.Days -> pluralStringResource(R.plurals.updated_days_ago, recency.n, recency.n)
    is Recency.Weeks -> pluralStringResource(R.plurals.updated_weeks_ago, recency.n, recency.n)
    is Recency.Months -> pluralStringResource(R.plurals.updated_months_ago, recency.n, recency.n)
    is Recency.Years -> pluralStringResource(R.plurals.updated_years_ago, recency.n, recency.n)
}

/** First launch: what the app is for, "Find someone", and a few people to start with. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHome(
    suggestions: List<Suggestion>,
    onFindSomeone: () -> Unit,
    onSuggestion: (Int) -> Unit,
    bottomPadding: Dp,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, top = 72.dp, bottom = spacing.xl + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onFindSomeone,
            modifier = Modifier
                .padding(top = spacing.sm)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(start = 18.dp, end = spacing.xl),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.size(spacing.sm))
            Text(stringResource(R.string.find_someone), style = MaterialTheme.typography.labelLarge)
        }
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(bottom = spacing.gutter - spacing.md),
                )
                Text(
                    stringResource(R.string.suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    // Rows of 48dp touch targets, overlapping so the 32dp chips are 8dp apart as in the mockup.
                    verticalArrangement = Arrangement.spacedBy(spacing.sm - ChipTouchOverhang * 2),
                    modifier = Modifier.offset(y = -ChipTouchOverhang),
                ) {
                    for (s in suggestions) SuggestionChip(s.name, onClick = { onSuggestion(s.id) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.height(32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(name, style = SuggestionStyle, maxLines = 1)
        }
    }
}

private val SuggestionStyle = serif(14f, 18f)

/** How far a 32dp chip's 48dp touch target reaches above and below it. */
private val ChipTouchOverhang = 8.dp
private val BarShape = RoundedCornerShape(2.dp)
private val SkeletonShape = RoundedCornerShape(3.dp)
