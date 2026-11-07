package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wrightdk.repertory.R
import io.github.wrightdk.repertory.appContainer
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.ui.components.PersonPhoto
import io.github.wrightdk.repertory.ui.components.skeletonPulse
import io.github.wrightdk.repertory.ui.theme.repertoryColors
import io.github.wrightdk.repertory.ui.theme.serif
import io.github.wrightdk.repertory.ui.theme.spacing
import java.text.NumberFormat

/** Search people on TMDB, from design/search-people-*.png. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPersonSelected: (personId: Int) -> Unit,
    onOpenApiKeySetting: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(context.appContainer))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    SearchScreen(
        query = viewModel.query,
        state = state,
        onBack = onBack,
        onClear = viewModel::clear,
        onSearchAction = {
            keyboard?.hide()
            viewModel.searchNow()
        },
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onPersonSelected = { id ->
            keyboard?.hide()
            onPersonSelected(id)
        },
        onOpenApiKeySetting = onOpenApiKeySetting,
    )
}

@Composable
private fun SearchScreen(
    query: TextFieldState,
    state: SearchUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearchAction: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPersonSelected: (Int) -> Unit,
    onOpenApiKeySetting: () -> Unit,
) {
    Scaffold(
        topBar = {
            SearchBar(
                query = query,
                onBack = onBack,
                onClear = onClear,
                onSearchAction = onSearchAction,
                loading = state.loading && state.content is SearchContent.Results,
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            val bottom = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            when (val content = state.content) {
                is SearchContent.Results -> Results(content, bottom, onLoadMore, onPersonSelected)
                is SearchContent.NoResults ->
                    if (state.loading) Searching() else NoResults(content.query)
                is SearchContent.Error ->
                    if (state.loading) Searching() else SearchError(content.error, onRetry, onOpenApiKeySetting)
                SearchContent.Idle -> if (state.loading) Searching()
            }
        }
    }
}

/** Back, the search field and Clear, over a hairline. */
@Composable
private fun SearchBar(
    query: TextFieldState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearchAction: () -> Unit,
    loading: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Open with the keyboard up, but not again when coming back from Add to your list.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoFocused) {
            focusRequester.requestFocus()
            autoFocused = true
        }
    }

    Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
            }
            BasicTextField(
                state = query,
                modifier = Modifier
                    .weight(1f)
                    // Lines the text up with the mockup's input, which has the browser's inner padding.
                    .padding(start = 1.dp)
                    .focusRequester(focusRequester),
                textStyle = SearchFieldStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                onKeyboardAction = { onSearchAction() },
                decorator = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.text.isEmpty()) {
                            Text(
                                stringResource(R.string.search_people_hint),
                                style = SearchFieldStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                },
            )
            if (query.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear_search))
                }
            }
        }
        // The hairline doubles as the progress track while a new search replaces the results.
        Box(Modifier.fillMaxWidth().height(1.dp)) {
            if (loading) {
                LinearProgressIndicator(
                    // A touch heavier than the rule so it reads as activity; overhangs it evenly.
                    modifier = Modifier.fillMaxWidth().requiredHeight(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    gapSize = 0.dp,
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun Results(
    content: SearchContent.Results,
    contentPadding: PaddingValues,
    onLoadMore: () -> Unit,
    onPersonSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    // New results start at the top.
    LaunchedEffect(content.people.firstOrNull()?.tmdbId) { listState.scrollToItem(0) }
    // Scrolling the results means reading them: get the keyboard out of the way.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, content.canLoadMore, content.loadMoreFailed) {
        if (nearEnd && content.canLoadMore && !content.loadMoreFailed) onLoadMore()
    }

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "count") { CountLabel(matchesLabel(content.totalResults)) }
        items(content.people, key = { it.tmdbId }) { person ->
            PersonRow(person, onClick = { onPersonSelected(person.tmdbId) })
        }
        if (content.canLoadMore) {
            item(key = "more") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (content.loadMoreFailed) {
                        TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_more_failed)) }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

/** "3 matches", or "Searching…" over the skeleton. */
@Composable
private fun CountLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = MaterialTheme.spacing.gutter,
            end = MaterialTheme.spacing.gutter,
            top = MaterialTheme.spacing.gutter,
            bottom = MaterialTheme.spacing.sm,
        ),
    )
}

@Composable
private fun PersonRow(person: SearchPersonRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.gutter, vertical = MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
        PersonPhoto(name = person.name, url = person.photoUrl, modifier = Modifier.size(PhotoWidth, PhotoHeight))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(person.name, style = NameStyle)
            if (person.role != null) {
                Text(
                    person.role,
                    style = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (person.onList) OnListBadge()
    }
}

@Composable
private fun OnListBadge() {
    val green = MaterialTheme.repertoryColors.seenText
    Text(
        text = stringResource(R.string.on_list),
        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
        color = green,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, green, MaterialTheme.shapes.extraSmall)
            // The mockup's 4dp × 6dp padding plus its 1dp border, which CSS adds outside the padding.
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** First search for a query: skeleton rows in the shape of the results, like the filmography's first fetch. */
@Composable
private fun Searching() {
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxSize()) {
        CountLabel(stringResource(R.string.searching))
        Column(Modifier.skeletonPulse()) {
            repeat(SKELETON_ROWS) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.gutter,
                        vertical = MaterialTheme.spacing.md,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
                ) {
                    Box(Modifier.size(PhotoWidth, PhotoHeight).background(block, RoundedCornerShape(3.dp)))
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Box(Modifier.size(width = 160.dp, height = 14.dp).background(block, SkeletonBarShape))
                        Box(Modifier.size(width = 72.dp, height = 10.dp).background(block, SkeletonBarShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Message(
        title = stringResource(R.string.search_no_results_title, query),
        body = stringResource(R.string.search_no_results_body),
    )
}

@Composable
private fun SearchError(error: TmdbError, onRetry: () -> Unit, onOpenApiKeySetting: () -> Unit) {
    val (title, body) = commonTmdbErrorCopy(error)
        ?: (R.string.search_error_other_title to R.string.search_error_other_body)
    Message(title = stringResource(title), body = stringResource(body)) {
        TmdbErrorActions(error, onRetry, onOpenApiKeySetting)
    }
}

/** Newsreader headline and a line of help, as in the mockup's "No one called …" state. */
@Composable
internal fun Message(title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Text(title, style = MessageTitleStyle)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}

@Composable
private fun matchesLabel(count: Int): String {
    val formatted = remember(count) { NumberFormat.getIntegerInstance().format(count) }
    return if (count == 1) stringResource(R.string.search_one_match) else stringResource(R.string.search_matches, formatted)
}

// Mockup values that fall between the theme's type steps.
private val SearchFieldStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)

private val MessageTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 27.5.sp)

private val NameStyle = serif(18f, 21.6f, FontWeight.Medium)

private val PhotoWidth = 48.dp
private val PhotoHeight = 60.dp
private val SkeletonBarShape = RoundedCornerShape(2.dp)
private const val SKELETON_ROWS = 4
private const val LOAD_MORE_THRESHOLD = 5
