package io.github.wrightdk.repertory.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

sealed interface FilmographyBody {
    /** First fetch from TMDB, no credits cached yet. */
    data object Loading : FilmographyBody

    /** No credits cached and TMDB couldn't be reached. */
    data class Error(val error: TmdbError) : FilmographyBody

    data class Loaded(val content: FilmographyContent) : FilmographyBody

    /** The list was removed (or never existed). */
    data object Missing : FilmographyBody
}

data class FilmographyUiState(
    /** Null until the person is cached. */
    val header: FilmographyHeader? = null,
    val body: FilmographyBody = FilmographyBody.Loading,
)

/** "Ticked off Lolita, 10 of 13 seen", shown after ticking from the list. */
data class TickedOff(val title: String, val seen: Int, val total: Int)

/**
 * One list's filmography. Films come from the Room cache (filled by
 * [refreshPerson] on first open); each movie's details (runtime, status) are
 * fetched lazily as its row comes on screen, see [onRowShown].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilmographyViewModel(
    private val trackedPersonId: Long,
    trackedPerson: Flow<TrackedPerson?>,
    person: (personId: Int) -> Flow<Person?>,
    filmography: (personId: Int, department: String) -> Flow<List<FilmCredit>>,
    viewings: Flow<List<Viewing>>,
    private val refreshPerson: suspend (personId: Int) -> TmdbResult<Unit>,
    private val refreshMovie: suspend (movieId: Int) -> TmdbResult<Unit>,
    private val imageConfig: suspend () -> ImageConfig,
    private val setSeen: suspend (mediaType: MediaType, filmId: Int, seenOn: LocalDate?, seen: Boolean) -> Unit,
    private val touchList: suspend (trackedPersonId: Long) -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
    /** Global defaults from Settings, once that screen exists. */
    private val defaultFilters: FilmographyFilters = FilmographyFilters.DEFAULTS,
) : ViewModel() {

    private sealed interface Load {
        data object Loading : Load
        data class Failed(val error: TmdbError) : Load
        data object Done : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val images = MutableStateFlow(ImageConfig.DEFAULT)
    private val viewingsByFilm = viewings.map { list -> list.associateBy { it.mediaType to it.filmId } }

    private val tracked = trackedPerson.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tickedOff = Channel<TickedOff>(Channel.BUFFERED)

    /** One event per film ticked from the list, for the snackbar. */
    val tickedOff: Flow<TickedOff> = _tickedOff.receiveAsFlow()

    val uiState: StateFlow<FilmographyUiState> = trackedPerson.flatMapLatest { tp ->
        if (tp == null) {
            flowOf(FilmographyUiState(body = FilmographyBody.Missing))
        } else {
            combine(
                person(tp.personId),
                filmography(tp.personId, tp.department),
                viewingsByFilm,
                images,
                load,
            ) { p, credits, seen, config, l -> state(tp, p, credits, seen, config, l) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilmographyUiState())

    /** Movie ids whose details have been asked for this session; each is fetched at most once. */
    private val requested = mutableSetOf<Int>()
    private val detailsPermits = Semaphore(DETAILS_CONCURRENCY)

    init {
        viewModelScope.launch { images.value = imageConfig() }
        reload()
    }

    fun retry() = reload()

    /**
     * A row came on screen: make sure its movie details are cached, so runtime
     * and release status are known. Cheap to call repeatedly.
     */
    fun onRowShown(row: FilmRow) {
        if (row.mediaType != MediaType.MOVIE || !requested.add(row.tmdbId)) return
        viewModelScope.launch { detailsPermits.withPermit { refreshMovie(row.tmdbId) } }
    }

    /** Ticks (dated today) or unticks a released film. Unreleased films can't be ticked. */
    fun toggleSeen(row: FilmRow) {
        if (row.unreleased) return
        viewModelScope.launch {
            setSeen(row.mediaType, row.tmdbId, today(), !row.seen)
            touchList(trackedPersonId)
            if (!row.seen) {
                // Report the count once the tick has reached the list, not from the state at tap time.
                val content = withTimeoutOrNull(TICK_REPORT_TIMEOUT_MS) {
                    uiState.mapNotNull { (it.body as? FilmographyBody.Loaded)?.content }
                        .first { c -> c.items.any { it is FilmographyItem.Film && it.row.key == row.key && it.row.seen } }
                } ?: return@launch
                _tickedOff.trySend(TickedOff(row.title, content.seen, content.total))
            }
        }
    }

    private fun reload() {
        load.value = Load.Loading
        viewModelScope.launch {
            val tp = tracked.filterNotNull().first()
            load.value = when (val r = refreshPerson(tp.personId)) {
                is TmdbResult.Success -> Load.Done
                is TmdbResult.Failure -> Load.Failed(r.error)
            }
        }
    }

    private fun state(
        tp: TrackedPerson,
        p: Person?,
        credits: List<FilmCredit>,
        seen: Map<Pair<MediaType, Int>, Viewing>,
        config: ImageConfig,
        l: Load,
    ): FilmographyUiState {
        val header = p?.let { filmographyHeader(it, tp.department, config, PHOTO_MIN_WIDTH_PX) }
        val body = when {
            // Cached credits beat a failed refresh: the list works offline.
            credits.isNotEmpty() || l == Load.Done -> FilmographyBody.Loaded(
                filmographyContent(
                    credits = credits,
                    viewings = seen,
                    filters = tp.filters(defaultFilters),
                    custom = tp.filterOverride != null,
                    today = today(),
                    images = config,
                    posterMinWidthPx = POSTER_MIN_WIDTH_PX,
                ),
            )
            l is Load.Failed -> FilmographyBody.Error(l.error)
            else -> FilmographyBody.Loading
        }
        return FilmographyUiState(header, body)
    }

    companion object {
        /** 72dp photo; w185 is TMDB's step below "original" (see AddPersonViewModel). */
        private const val PHOTO_MIN_WIDTH_PX = 185

        /** 40dp poster is ~104px on the Pixel's 2.6x screen; TMDB serves w154. */
        private const val POSTER_MIN_WIDTH_PX = 104

        private const val DETAILS_CONCURRENCY = 4
        private const val TICK_REPORT_TIMEOUT_MS = 2_000L

        fun factory(container: AppContainer, trackedPersonId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = container.tmdbRepository
                val db = container.database
                val viewingDao = db.viewingDao()
                FilmographyViewModel(
                    trackedPersonId = trackedPersonId,
                    trackedPerson = db.trackedPersonDao().observe(trackedPersonId),
                    person = repo::observePerson,
                    filmography = repo::observeFilmography,
                    viewings = viewingDao.observeAll(),
                    refreshPerson = { repo.refreshPerson(it) },
                    refreshMovie = { repo.refreshMovie(it) },
                    imageConfig = repo::imageConfig,
                    setSeen = { type, id, date, seen ->
                        if (seen) viewingDao.upsert(Viewing(type, id, date)) else viewingDao.delete(type, id)
                    },
                    touchList = { db.trackedPersonDao().touch(it, System.currentTimeMillis()) },
                )
            }
        }
    }
}
