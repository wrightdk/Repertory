package io.github.wrightdk.repertory.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.DetailsSyncState
import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.Suggestion
import io.github.wrightdk.repertory.data.pickSuggestions
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeContent {
    /** The lists haven't been read yet. Shows nothing, so the empty state doesn't flash. */
    data object Loading : HomeContent

    /** Nobody on the list: the first-launch screen, with a random handful of [suggestions]. */
    data class Empty(val suggestions: List<Suggestion>) : HomeContent

    /**
     * [rows] sorted, and narrowed by the list search when it's open. [total] is before the search.
     * [query] is the search text these rows were narrowed by ("" when the search is closed), kept
     * with them so Home can tell a new order or search from a data update.
     */
    data class Lists(val rows: List<HomeRow>, val total: Int, val query: String = "") : HomeContent
}

data class HomeUiState(
    val sort: HomeSort = HomeSort.NAME,
    val content: HomeContent = HomeContent.Loading,
)

/** One list's raw inputs, before the viewings and filters are applied. */
private data class HomeListInputs(val list: TrackedPerson, val person: Person?, val credits: List<FilmCredit>)

/**
 * Every list with its progress, counted by the same rules as its filmography
 * ([filmographyContent]). A list whose film details are still loading has no
 * count yet rather than a provisional one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    lists: Flow<List<TrackedPerson>>,
    person: (personId: Int) -> Flow<Person?>,
    filmography: (personId: Int, department: String) -> Flow<List<FilmCredit>>,
    /** Ids of lists with films still missing their details. */
    listIdsMissingDetails: Flow<List<Long>>,
    /** Background details fetches running or failed, by list id. */
    detailsSync: Flow<Map<Long, DetailsSyncState>>,
    viewings: Flow<List<Viewing>>,
    defaultFilters: StateFlow<FilmographyFilters>,
    private val sort: StateFlow<HomeSort>,
    private val saveSort: (HomeSort) -> Unit,
    private val imageConfig: suspend () -> ImageConfig,
    private val suggestions: suspend () -> List<Suggestion>,
    private val random: Random = Random.Default,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val images = MutableStateFlow(ImageConfig.DEFAULT)

    /** Picked once, so the handful doesn't change on every recomposition or return to Home. */
    private val suggested = MutableStateFlow<List<Suggestion>>(emptyList())

    private val _listQuery = MutableStateFlow<String?>(null)

    /** The search-your-list field's text, or null when the search is closed. */
    val listQuery: StateFlow<String?> = _listQuery.asStateFlow()

    private val inputs: Flow<List<HomeListInputs>> = lists.flatMapLatest { all ->
        if (all.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                all.map { l ->
                    combine(person(l.personId), filmography(l.personId, l.department)) { p, credits ->
                        HomeListInputs(l, p, credits)
                    }
                },
            ) { it.toList() }
        }
    }

    private val rows: Flow<List<HomeRow>> = combine(
        inputs,
        viewings.map { list -> list.associateBy { it.mediaType to it.filmId } },
        defaultFilters,
        combine(listIdsMissingDetails.map { it.toSet() }, detailsSync, ::Pair),
        images,
    ) { all, seen, defaults, (missing, sync), config ->
        val day = today()
        all.map { i ->
            homeRow(
                list = i.list,
                person = i.person,
                credits = i.credits,
                detailsComplete = i.list.id !in missing,
                detailsFailed = sync[i.list.id] is DetailsSyncState.Failed,
                viewings = seen,
                defaults = defaults,
                today = day,
                images = config,
                photoMinWidthPx = PHOTO_MIN_WIDTH_PX,
            )
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(rows, sort, _listQuery, suggested) { all, s, query, picks ->
        val content = if (all.isEmpty()) {
            HomeContent.Empty(picks)
        } else {
            HomeContent.Lists(searchRows(sortRows(all, s), query.orEmpty()), total = all.size, query = query.orEmpty())
        }
        HomeUiState(s, content)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { images.value = imageConfig() }
        viewModelScope.launch { suggested.value = pickSuggestions(suggestions(), SUGGESTION_COUNT, random) }
    }

    fun setSort(sort: HomeSort) = saveSort(sort)

    fun openListSearch() {
        if (_listQuery.value == null) _listQuery.value = ""
    }

    fun setListQuery(query: String) {
        _listQuery.value = query
    }

    fun closeListSearch() {
        _listQuery.value = null
    }

    companion object {
        /** 52dp photo is ~135px on the Pixel's 2.6x screen; TMDB serves w185. */
        private const val PHOTO_MIN_WIDTH_PX = 135

        /** Chips on the first-launch screen, as in the mockup. */
        private const val SUGGESTION_COUNT = 4

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = container.tmdbRepository
                val db = container.database
                HomeViewModel(
                    lists = db.trackedPersonDao().observeAll(),
                    person = repo::observePerson,
                    filmography = repo::observeFilmography,
                    listIdsMissingDetails = repo.observeListIdsMissingDetails(),
                    detailsSync = container.filmDetailsSync.states,
                    viewings = db.viewingDao().observeAll(),
                    defaultFilters = container.settings.filterDefaults,
                    sort = container.settings.homeSort,
                    saveSort = container.settings::setHomeSort,
                    imageConfig = repo::imageConfig,
                    suggestions = container::suggestions,
                )
            }
        }
    }
}
