package io.github.wrightdk.repertory.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wrightdk.repertory.AppContainer
import io.github.wrightdk.repertory.data.DetailsSyncState
import io.github.wrightdk.repertory.data.FilmCategory
import io.github.wrightdk.repertory.data.overrideFor
import io.github.wrightdk.repertory.data.with
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface FilmographyBody {
    /** First fetch from TMDB, no credits cached yet. */
    data object Loading : FilmographyBody

    /** No credits cached and TMDB couldn't be reached. */
    data class Error(val error: TmdbError) : FilmographyBody

    /**
     * [content] lists the films straight away; its count, ledger and filter
     * summary are final only once [details] is [DetailsStatus.Complete].
     */
    data class Loaded(val content: FilmographyContent, val details: DetailsStatus) : FilmographyBody

    /** The list was removed (or never existed). */
    data object Missing : FilmographyBody
}

/** Whether every film in the list has its details (runtime, status), so the count is final. */
sealed interface DetailsStatus {
    data object Complete : DetailsStatus

    /** Still fetching in the background. */
    data object Loading : DetailsStatus

    /** The background fetch stopped; [FilmographyViewModel.retryDetails] starts it again. */
    data class Failed(val error: TmdbError) : DetailsStatus
}

data class FilmographyUiState(
    /** Null until the person is cached. */
    val header: FilmographyHeader? = null,
    val body: FilmographyBody = FilmographyBody.Loading,
)

/**
 * "Ticked off Lolita, 10 of 13 seen", shown after ticking from the list. The
 * count is null while film details are still loading, as it isn't final yet.
 */
data class TickedOff(val title: String, val seen: Int?, val total: Int?)

/**
 * One list's filmography. Films come from the Room cache (filled by
 * [refreshPerson] on first open). Every movie's details (runtime, status) are
 * fetched in the background by [io.github.wrightdk.repertory.data.FilmDetailsSync]
 * ([requestDetails]); until that's done the count shows as loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilmographyViewModel(
    private val trackedPersonId: Long,
    trackedPerson: Flow<TrackedPerson?>,
    person: (personId: Int) -> Flow<Person?>,
    filmography: (personId: Int, department: String) -> Flow<List<FilmCredit>>,
    viewings: Flow<List<Viewing>>,
    private val refreshPerson: suspend (personId: Int) -> TmdbResult<Unit>,
    /** Movies in the list still without details. */
    missingDetails: (personId: Int, department: String) -> Flow<Int>,
    /** The list's background details fetch, null when idle. */
    detailsSync: Flow<DetailsSyncState?>,
    /** Starts the list's background details fetch unless it's running. */
    private val requestDetails: (trackedPersonId: Long) -> Unit,
    private val imageConfig: suspend () -> ImageConfig,
    private val setSeen: suspend (mediaType: MediaType, filmId: Int, seenOn: LocalDate?, seen: Boolean) -> Unit,
    private val touchList: suspend (trackedPersonId: Long) -> Unit,
    /** Saves the list's own filters, or null to follow the defaults. */
    private val setFilterOverride: suspend (trackedPersonId: Long, filters: FilmographyFilters?) -> Unit,
    /** Global defaults from Settings. */
    private val defaultFilters: StateFlow<FilmographyFilters>,
    /** Deletes the list only; viewings belong to films and stay. */
    private val deleteList: suspend (trackedPersonId: Long) -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
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

    /** Set once the list is being removed, so the screen keeps its last state while it leaves. */
    @Volatile private var removing = false

    val uiState: StateFlow<FilmographyUiState> = trackedPerson.flatMapLatest { tp ->
        if (tp == null) {
            flowOf(FilmographyUiState(body = FilmographyBody.Missing))
        } else {
            val details = combine(missingDetails(tp.personId, tp.department), detailsSync) { missing, sync ->
                when {
                    missing == 0 -> DetailsStatus.Complete
                    sync is DetailsSyncState.Failed -> DetailsStatus.Failed(sync.error)
                    else -> DetailsStatus.Loading
                }
            }
            combine(
                combine(person(tp.personId), filmography(tp.personId, tp.department), ::Pair),
                combine(viewingsByFilm, defaultFilters, ::Pair),
                images,
                load,
                details,
            ) { (p, credits), (seen, defaults), config, l, d -> state(tp, p, credits, seen, defaults, config, l, d) }
        }
    }
        .filter { !(removing && it.body == FilmographyBody.Missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilmographyUiState())

    init {
        viewModelScope.launch { images.value = imageConfig() }
        reload()
    }

    fun retry() = reload()

    /** Starts the background details fetch again after it failed. */
    fun retryDetails() = requestDetails(trackedPersonId)

    /** Ticks (dated today) or unticks a released film. Unreleased films can't be ticked. */
    fun toggleSeen(row: FilmRow) {
        if (row.unreleased) return
        viewModelScope.launch {
            setSeen(row.mediaType, row.tmdbId, today(), !row.seen)
            touchList(trackedPersonId)
            if (!row.seen) {
                // Report the count once the tick has reached the list, not from the state at tap time.
                val loaded = withTimeoutOrNull(TICK_REPORT_TIMEOUT_MS) {
                    uiState.mapNotNull { it.body as? FilmographyBody.Loaded }
                        .first { b -> b.content.items.any { it is FilmographyItem.Film && it.row.key == row.key && it.row.seen } }
                } ?: return@launch
                val final = loaded.details == DetailsStatus.Complete
                _tickedOff.trySend(
                    TickedOff(
                        title = row.title,
                        seen = loaded.content.seen.takeIf { final },
                        total = loaded.content.total.takeIf { final },
                    ),
                )
            }
        }
    }

    /** Shows or hides [category] in this list only, from the "Show in this list" sheet. */
    fun setShown(category: FilmCategory, show: Boolean) {
        val tp = tracked.value ?: return
        val defaults = defaultFilters.value
        val next = tp.filters(defaults).with(category, show)
        viewModelScope.launch { setFilterOverride(trackedPersonId, overrideFor(next, defaults)) }
    }

    /** Drops this list's own filters so it follows the defaults from Settings again. */
    fun useDefaults() {
        viewModelScope.launch { setFilterOverride(trackedPersonId, null) }
    }

    /**
     * Removes the list (not its ticked films) from "On your list". The delete
     * finishes even if the screen leaves straight away, as it does.
     */
    fun remove() {
        removing = true
        viewModelScope.launch { withContext(NonCancellable) { deleteList(trackedPersonId) } }
    }

    private fun reload() {
        load.value = Load.Loading
        viewModelScope.launch {
            val tp = tracked.filterNotNull().first()
            load.value = when (val r = refreshPerson(tp.personId)) {
                is TmdbResult.Success -> Load.Done
                is TmdbResult.Failure -> Load.Failed(r.error)
            }
            // Fill in details for any films the refresh added (or that never finished
            // loading). A no-op when nothing is missing.
            requestDetails(trackedPersonId)
        }
    }

    private fun state(
        tp: TrackedPerson,
        p: Person?,
        credits: List<FilmCredit>,
        seen: Map<Pair<MediaType, Int>, Viewing>,
        defaults: FilmographyFilters,
        config: ImageConfig,
        l: Load,
        details: DetailsStatus,
    ): FilmographyUiState {
        val header = p?.let { filmographyHeader(it, tp.department, config, PHOTO_MIN_WIDTH_PX) }
        val body = when {
            // Cached credits beat a failed refresh: the list works offline.
            credits.isNotEmpty() || l == Load.Done -> FilmographyBody.Loaded(
                filmographyContent(
                    credits = credits,
                    personName = p?.name,
                    viewings = seen,
                    filters = tp.filters(defaults),
                    custom = tp.filterOverride != null,
                    today = today(),
                    images = config,
                    posterMinWidthPx = POSTER_MIN_WIDTH_PX,
                ),
                details = details,
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
                    missingDetails = repo::observeMissingDetailsCount,
                    detailsSync = container.filmDetailsSync.state(trackedPersonId),
                    requestDetails = container.filmDetailsSync::enqueue,
                    imageConfig = repo::imageConfig,
                    setSeen = { type, id, date, seen ->
                        if (seen) viewingDao.upsert(Viewing(type, id, date)) else viewingDao.delete(type, id)
                    },
                    touchList = { db.trackedPersonDao().touch(it, System.currentTimeMillis()) },
                    setFilterOverride = db.trackedPersonDao()::setFilterOverride,
                    defaultFilters = container.settings.filterDefaults,
                    deleteList = db.trackedPersonDao()::deleteById,
                )
            }
        }
    }
}
