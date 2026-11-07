package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a list's background details fetch stands. */
sealed interface DetailsSyncState {
    data object Running : DetailsSyncState

    /** Stopped before every film's details were in; [FilmDetailsSync.enqueue] tries again. */
    data class Failed(val error: TmdbError) : DetailsSyncState
}

/**
 * Fetches every film's details for each list in the background, so a list's
 * "X of Y" is final when it's first shown. Runs in [scope] (the app's), so it
 * carries on when the user leaves the screen that started it. If the process
 * dies part way, [backfill] at the next start picks up where it left off,
 * since only films still missing details are fetched.
 *
 * A list restored from a backup arrives without its person or credits, so
 * those are fetched first ([fetchPersonIfMissing]), one person at a time so a
 * person on the list in two roles is only fetched once.
 */
class FilmDetailsSync(
    private val scope: CoroutineScope,
    private val trackedList: suspend (trackedPersonId: Long) -> TrackedPerson?,
    private val listIdsMissingDetails: Flow<List<Long>>,
    private val fillMissingFilmDetails: suspend (personId: Int, department: String) -> TmdbResult<Int>,
    /** Ids of lists whose person has never been fetched (restored from a backup). */
    private val listIdsMissingPerson: Flow<List<Long>> = flowOf(emptyList()),
    /** Fetches the person and their credits unless they're already cached. */
    private val fetchPersonIfMissing: suspend (personId: Int) -> TmdbResult<Unit> = { TmdbResult.Success(Unit) },
    private val log: (String) -> Unit = {},
) {
    private val personMutex = Mutex()
    private val jobs = mutableMapOf<Long, Job>()
    private val _states = MutableStateFlow<Map<Long, DetailsSyncState>>(emptyMap())

    /** Lists with a fetch running or failed, by list id. A list missing here is idle. */
    val states: StateFlow<Map<Long, DetailsSyncState>> = _states.asStateFlow()

    fun state(trackedPersonId: Long): Flow<DetailsSyncState?> = states.map { it[trackedPersonId] }

    /** Fetches whatever this list is missing. Does nothing if a fetch for it is already running. */
    fun enqueue(trackedPersonId: Long) {
        synchronized(jobs) {
            if (jobs[trackedPersonId]?.isActive == true) return
            _states.update { it + (trackedPersonId to DetailsSyncState.Running) }
            jobs[trackedPersonId] = scope.launch { run(trackedPersonId) }
        }
    }

    /**
     * Starts a fetch for every list that has films without details (e.g. lists
     * added before this existed) or no person yet (just restored from a backup).
     */
    fun backfill() {
        scope.launch { (listIdsMissingPerson.first() + listIdsMissingDetails.first()).distinct().forEach(::enqueue) }
    }

    /**
     * Tries again every list whose fetch stopped with an error matching [which]
     * (any error by default), e.g. once the user has set their own TMDB key.
     */
    fun retryFailed(which: (TmdbError) -> Boolean = { true }) {
        states.value.filterValues { it is DetailsSyncState.Failed && which(it.error) }.keys.forEach(::enqueue)
    }

    /** Tries again the lists that stopped for want of a connection. Called when the device gets one. */
    fun retryOffline() = retryFailed { it == TmdbError.NoNetwork }

    private suspend fun run(trackedPersonId: Long) {
        val list = trackedList(trackedPersonId)
        val result = if (list == null) {
            TmdbResult.Success(0)
        } else {
            when (val person = personMutex.withLock { fetchPersonIfMissing(list.personId) }) {
                is TmdbResult.Success -> fillMissingFilmDetails(list.personId, list.department)
                is TmdbResult.Failure -> person
            }
        }
        log("List $trackedPersonId details: $result")
        _states.update { states ->
            when (result) {
                is TmdbResult.Success -> states - trackedPersonId
                is TmdbResult.Failure -> states + (trackedPersonId to DetailsSyncState.Failed(result.error))
            }
        }
    }
}
