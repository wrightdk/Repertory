package io.github.wrightdk.repertory.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FilmDetailUiState {
    data object Loading : FilmDetailUiState

    /** The film isn't in the cache (it can't normally be opened then). */
    data object Missing : FilmDetailUiState

    data class Loaded(
        val film: FilmDetail,
        /** For the list it was opened from; null if that list has since been removed. */
        val credit: CreditLine?,
        val toward: List<TowardEntry>,
    ) : FilmDetailUiState
}

/** One list's raw inputs, before the viewings and filters are applied. */
private data class ListInputs(
    val list: TrackedPerson,
    val person: Person?,
    val credits: List<FilmCredit>,
    val missingDetails: Int,
)

/**
 * One film, opened from one list. The seen state is the film's own ([Viewing]
 * is keyed by film), so a change here shows on every list it's in; "Counts
 * toward" recounts each of those lists with the same rules as its filmography.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilmDetailViewModel(
    private val trackedPersonId: Long,
    private val mediaType: MediaType,
    private val filmId: Int,
    film: Flow<Film?>,
    viewing: Flow<Viewing?>,
    viewings: Flow<List<Viewing>>,
    /** Every list the film is in, shown or hidden there. */
    listsWithFilm: Flow<List<TrackedPerson>>,
    person: (personId: Int) -> Flow<Person?>,
    filmography: (personId: Int, department: String) -> Flow<List<FilmCredit>>,
    missingDetails: (personId: Int, department: String) -> Flow<Int>,
    defaultFilters: StateFlow<FilmographyFilters>,
    private val imageConfig: suspend () -> ImageConfig,
    private val saveViewing: suspend (Viewing) -> Unit,
    private val deleteViewing: suspend (mediaType: MediaType, filmId: Int) -> Unit,
    /** Marks lists as just updated, for the Recent sort on Home. */
    private val touchLists: suspend (ids: List<Long>) -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val images = MutableStateFlow(ImageConfig.DEFAULT)

    private val listInputs: Flow<List<ListInputs>> = listsWithFilm.flatMapLatest { lists ->
        if (lists.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                lists.map { l ->
                    combine(
                        person(l.personId),
                        filmography(l.personId, l.department),
                        missingDetails(l.personId, l.department),
                    ) { p, credits, missing -> ListInputs(l, p, credits, missing) }
                },
            ) { it.toList() }
        }
    }

    private val listViews: Flow<List<ListView>> = combine(
        listInputs,
        viewings.map { list -> list.associateBy { it.mediaType to it.filmId } },
        defaultFilters,
    ) { inputs, seen, defaults ->
        val day = today()
        inputs.map { i -> listView(i.list, i.person, i.credits, i.missingDetails, seen, defaults, mediaType, filmId, day) }
    }

    val uiState: StateFlow<FilmDetailUiState> = combine(film, viewing, listViews, images) { f, v, views, config ->
        if (f == null) return@combine FilmDetailUiState.Missing
        val source = views.firstOrNull { it.list.id == trackedPersonId }
        FilmDetailUiState.Loaded(
            film = filmDetail(f, v, today(), config, POSTER_MIN_WIDTH_PX),
            credit = source?.person?.let { creditLine(source.list.department, it.name, source.creditsOnFilm) },
            toward = towardEntries(views, openedFrom = trackedPersonId),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilmDetailUiState.Loading)

    init {
        viewModelScope.launch { images.value = imageConfig() }
    }

    /** Ticks the film (dated today) or unticks it, which drops the date. Unreleased films can't be ticked. */
    fun toggleSeen() {
        val loaded = uiState.value as? FilmDetailUiState.Loaded ?: return
        viewModelScope.launch {
            when (loaded.film.seen) {
                SeenState.NotSeen -> saveViewing(Viewing(mediaType, filmId, today()))
                is SeenState.Seen -> deleteViewing(mediaType, filmId)
                is SeenState.Unreleased -> return@launch
            }
            touch(loaded)
        }
    }

    /** Changes or clears (null) the date a seen film was seen. */
    fun setSeenOn(date: LocalDate?) {
        val loaded = uiState.value as? FilmDetailUiState.Loaded ?: return
        val seen = loaded.film.seen as? SeenState.Seen ?: return
        if (seen.seenOn == date) return
        viewModelScope.launch {
            saveViewing(Viewing(mediaType, filmId, date))
            touch(loaded)
        }
    }

    fun setSeenToday() = setSeenOn(today())

    private suspend fun touch(loaded: FilmDetailUiState.Loaded) {
        val ids = loaded.toward.map { it.trackedPersonId }
        if (ids.isNotEmpty()) touchLists(ids)
    }

    companion object {
        /** 116dp poster is ~305px on the Pixel's 2.6x screen; TMDB serves w342. */
        private const val POSTER_MIN_WIDTH_PX = 305

        fun factory(container: AppContainer, trackedPersonId: Long, mediaType: MediaType, filmId: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val repo = container.tmdbRepository
                    val db = container.database
                    val viewingDao = db.viewingDao()
                    val trackedDao = db.trackedPersonDao()
                    FilmDetailViewModel(
                        trackedPersonId = trackedPersonId,
                        mediaType = mediaType,
                        filmId = filmId,
                        film = repo.observeFilm(mediaType, filmId),
                        viewing = viewingDao.observe(mediaType, filmId),
                        viewings = viewingDao.observeAll(),
                        listsWithFilm = trackedDao.observeListsWithFilm(mediaType, filmId),
                        person = repo::observePerson,
                        filmography = repo::observeFilmography,
                        missingDetails = repo::observeMissingDetailsCount,
                        defaultFilters = container.settings.filterDefaults,
                        imageConfig = repo::imageConfig,
                        saveViewing = viewingDao::upsert,
                        deleteViewing = viewingDao::delete,
                        touchLists = { trackedDao.touchAll(it, System.currentTimeMillis()) },
                    )
                }
            }
    }
}
