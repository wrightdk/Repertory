package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.PersonSearchPage
import io.github.wrightdk.repertory.data.PersonSearchResult
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.ui.roleLabel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row in the results. */
data class SearchPersonRow(
    val tmdbId: Int,
    val name: String,
    /** Known-for department as a role label ("Director"), or null if TMDB has none. */
    val role: String?,
    val photoUrl: String?,
    /** On the list in at least one role. */
    val onList: Boolean,
)

sealed interface SearchContent {
    /** Nothing typed yet. */
    data object Idle : SearchContent

    data class Results(
        val people: List<SearchPersonRow>,
        val totalResults: Int,
        val canLoadMore: Boolean,
        val loadingMore: Boolean,
        val loadMoreFailed: Boolean,
    ) : SearchContent

    data class NoResults(val query: String) : SearchContent

    data class Error(val error: TmdbError) : SearchContent
}

data class SearchUiState(
    /** What to show below the search field: the last completed search, or Idle. */
    val content: SearchContent = SearchContent.Idle,
    /** A search for the current text is running. [content] still holds the previous one meanwhile. */
    val loading: Boolean = false,
)

/**
 * Person search. Typing waits [debounce] before searching; each new search
 * cancels the one before, so a slow response for "kub" can't land after "kubrick".
 */
class SearchViewModel(
    private val searchPeople: suspend (query: String, page: Int) -> TmdbResult<PersonSearchPage>,
    trackedPersonIds: Flow<Collection<Int>>,
    private val imageConfig: suspend () -> ImageConfig,
    private val debounce: Duration = DEBOUNCE,
) : ViewModel() {

    val query = TextFieldState()

    /** Raw search state; joined with the tracked ids and image config into [uiState]. */
    private data class SearchState(
        val query: String = "",
        val results: List<PersonSearchResult> = emptyList(),
        val page: Int = 0,
        val totalPages: Int = 0,
        val totalResults: Int = 0,
        val error: TmdbError? = null,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    )

    private val state = MutableStateFlow(SearchState())
    private val images = MutableStateFlow(ImageConfig.DEFAULT)
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    val uiState: StateFlow<SearchUiState> =
        combine(state, trackedPersonIds.map { it.toSet() }, images) { s, tracked, config ->
            SearchUiState(content = s.toContent(tracked, config), loading = s.loading)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch { images.value = imageConfig() }
        viewModelScope.launch {
            snapshotFlow { query.text.toString().trim() }
                .distinctUntilChanged()
                .collect { search(it, immediate = false) }
        }
    }

    fun clear() {
        query.clearText()
        // Reset now rather than waiting for the text change to come round.
        search("", immediate = true)
    }

    /** Keyboard search action: skip the debounce. */
    fun searchNow() = search(query.text.toString().trim(), immediate = true)

    fun retry() = searchNow()

    fun loadMore() {
        val s = state.value
        if (s.loadingMore || loadMoreJob?.isActive == true || s.page >= s.totalPages || s.query.isEmpty()) return
        state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        loadMoreJob = viewModelScope.launch {
            when (val r = searchPeople(s.query, s.page + 1)) {
                is TmdbResult.Success -> state.update { cur ->
                    if (cur.query != s.query) return@update cur
                    cur.copy(
                        results = (cur.results + r.value.results).distinctBy { it.tmdbId },
                        page = r.value.page,
                        totalPages = r.value.totalPages,
                        loadingMore = false,
                    )
                }
                is TmdbResult.Failure -> state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private fun search(q: String, immediate: Boolean) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (q.isEmpty()) {
            state.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(debounce)
            state.update { it.copy(loading = true) }
            state.value = when (val r = searchPeople(q, 1)) {
                is TmdbResult.Success -> SearchState(
                    query = q,
                    results = r.value.results.distinctBy { it.tmdbId },
                    page = r.value.page,
                    totalPages = r.value.totalPages,
                    totalResults = r.value.totalResults,
                )
                is TmdbResult.Failure -> SearchState(query = q, error = r.error)
            }
        }
    }

    private fun SearchState.toContent(tracked: Set<Int>, config: ImageConfig): SearchContent = when {
        query.isEmpty() -> SearchContent.Idle
        error != null -> SearchContent.Error(error)
        results.isEmpty() -> SearchContent.NoResults(query)
        else -> SearchContent.Results(
            people = results.map { p ->
                SearchPersonRow(
                    tmdbId = p.tmdbId,
                    name = p.name,
                    role = p.knownForDepartment?.takeIf { it.isNotBlank() }?.let(::roleLabel),
                    photoUrl = config.profileUrl(p.profilePath, PHOTO_MIN_WIDTH_PX),
                    onList = p.tmdbId in tracked,
                )
            },
            // Adult results are dropped after TMDB counts them; never claim fewer than are shown.
            totalResults = maxOf(totalResults, results.size),
            canLoadMore = page < totalPages,
            loadingMore = loadingMore,
            loadMoreFailed = loadMoreFailed,
        )
    }

    companion object {
        val DEBOUNCE = 350.milliseconds

        /** The photo is 48dp wide; enough pixels for a 3x screen. */
        private const val PHOTO_MIN_WIDTH_PX = 144

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    searchPeople = container.tmdbRepository::searchPeople,
                    trackedPersonIds = container.database.trackedPersonDao().observeTrackedPersonIds(),
                    imageConfig = container.tmdbRepository::imageConfig,
                )
            }
        }
    }
}
