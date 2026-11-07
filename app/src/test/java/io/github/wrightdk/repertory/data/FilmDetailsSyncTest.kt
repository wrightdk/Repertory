package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilmDetailsSyncTest {
    private val lists = mutableMapOf<Long, TrackedPerson>()
    private val calls = mutableListOf<Pair<Int, String>>()
    private val missing = MutableStateFlow<List<Long>>(emptyList())
    private var result: TmdbResult<Int> = TmdbResult.Success(3)
    private var gate: CompletableDeferred<Unit>? = null
    private val missingPerson = MutableStateFlow<List<Long>>(emptyList())
    private val personFetches = mutableListOf<Int>()
    private var personResult: TmdbResult<Unit> = TmdbResult.Success(Unit)

    private fun TestScope.sync() = FilmDetailsSync(
        scope = backgroundScope,
        trackedList = { lists[it] },
        listIdsMissingDetails = missing,
        fillMissingFilmDetails = { person, department ->
            calls += person to department
            gate?.await()
            result
        },
        listIdsMissingPerson = missingPerson,
        fetchPersonIfMissing = { personId ->
            personFetches += personId
            personResult
        },
    )

    private fun track(personId: Int, department: String): Long {
        val id = lists.size + 1L
        lists[id] = TrackedPerson(id = id, personId = personId, department = department, addedAt = 0)
        return id
    }

    @Test fun `fetches the list's department and goes idle when done`() = runTest {
        val id = track(3636, "Acting")
        val sync = sync()
        gate = CompletableDeferred()

        sync.enqueue(id)
        runCurrent()
        assertEquals(DetailsSyncState.Running, sync.states.value[id])

        sync.enqueue(id)
        gate!!.complete(Unit)
        runCurrent()
        assertEquals("a second enqueue while running is ignored", listOf(3636 to "Acting"), calls)
        assertEquals(emptyMap<Long, DetailsSyncState>(), sync.states.value)
    }

    @Test fun `a failure is kept until the next try`() = runTest {
        val id = track(3636, "Acting")
        val sync = sync()
        result = TmdbResult.Failure(TmdbError.NoNetwork)

        sync.enqueue(id)
        runCurrent()
        assertEquals(DetailsSyncState.Failed(TmdbError.NoNetwork), sync.states.value[id])

        result = TmdbResult.Success(1)
        sync.enqueue(id)
        runCurrent()
        assertEquals(2, calls.size)
        assertEquals(null, sync.states.value[id])
    }

    @Test fun `retryFailed tries again only the lists that stopped`() = runTest {
        val failed = track(3895, "Acting")
        val done = track(240, "Directing")
        val sync = sync()
        result = TmdbResult.Failure(TmdbError.InvalidApiKey)
        sync.enqueue(failed)
        runCurrent()
        result = TmdbResult.Success(1)
        sync.enqueue(done)
        runCurrent()
        calls.clear()

        sync.retryFailed()
        runCurrent()
        assertEquals(listOf(3895 to "Acting"), calls)
        assertEquals(emptyMap<Long, DetailsSyncState>(), sync.states.value)
    }

    @Test fun `retryOffline tries again only the lists that stopped offline`() = runTest {
        val offline = track(3895, "Acting")
        val rejected = track(240, "Directing")
        val sync = sync()
        result = TmdbResult.Failure(TmdbError.NoNetwork)
        sync.enqueue(offline)
        runCurrent()
        result = TmdbResult.Failure(TmdbError.InvalidApiKey)
        sync.enqueue(rejected)
        runCurrent()
        calls.clear()

        result = TmdbResult.Success(1)
        sync.retryOffline()
        runCurrent()
        assertEquals(listOf(3895 to "Acting"), calls)
        assertEquals(mapOf<Long, DetailsSyncState>(rejected to DetailsSyncState.Failed(TmdbError.InvalidApiKey)), sync.states.value)
    }

    @Test fun `backfill starts every list still missing details`() = runTest {
        val caine = track(3895, "Acting")
        track(240, "Directing")
        val gerwig = track(45400, "Directing")
        missing.value = listOf(caine, gerwig)

        sync().backfill()
        runCurrent()
        assertEquals(setOf(3895 to "Acting", 45400 to "Directing"), calls.toSet())
    }

    @Test fun `backfill also picks up lists restored without their person`() = runTest {
        val restored = track(240, "Directing")
        val loading = track(3636, "Acting")
        missingPerson.value = listOf(restored)
        missing.value = listOf(loading, restored)
        val sync = sync()

        sync.backfill()
        runCurrent()
        assertEquals(listOf(240, 3636), personFetches)
        assertEquals("each list once, person first", setOf(240 to "Directing", 3636 to "Acting"), calls.toSet())
        assertEquals(2, calls.size)
    }

    @Test fun `a failed person fetch stops the list before its details`() = runTest {
        val id = track(240, "Directing")
        val sync = sync()
        personResult = TmdbResult.Failure(TmdbError.NoNetwork)

        sync.enqueue(id)
        runCurrent()
        assertEquals(DetailsSyncState.Failed(TmdbError.NoNetwork), sync.states.value[id])
        assertEquals(emptyList<Pair<Int, String>>(), calls)
    }
}
