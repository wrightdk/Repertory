package io.github.wrightdk.repertory.ui.screens

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import io.github.wrightdk.repertory.data.PersonSearchPage
import io.github.wrightdk.repertory.data.PersonSearchResult
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tracked = MutableStateFlow<Collection<Int>>(emptySet())
    private val calls = mutableListOf<Pair<String, Int>>()

    /** Canned responses by query; pages beyond 1 get "<query>#<page>". */
    private val responses = mutableMapOf<String, TmdbResult<PersonSearchPage>>()
    private var latency = 0L

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SearchViewModel(
        searchPeople = { q, page ->
            calls += q to page
            delay(latency)
            responses[if (page == 1) q else "$q#$page"] ?: TmdbResult.Success(PersonSearchPage.EMPTY)
        },
        trackedPersonIds = tracked,
        imageConfig = { ImageConfig.DEFAULT },
    )

    private fun TestScope.subscribe(vm: SearchViewModel) {
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        runCurrent()
    }

    private fun SearchViewModel.type(text: String) {
        query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun person(id: Int, name: String, dept: String? = "Directing", photo: String? = "/p$id.jpg") =
        PersonSearchResult(id, name, photo, dept, emptyList())

    private fun page(vararg people: PersonSearchResult, page: Int = 1, totalPages: Int = 1, total: Int = people.size) =
        TmdbResult.Success(PersonSearchPage(people.toList(), page, totalPages, total))

    @Test fun `typing waits for the debounce, then searches once for the last text`() = runTest(dispatcher) {
        responses["kubrick"] = page(person(240, "Stanley Kubrick"))
        val vm = vm()
        subscribe(vm)

        vm.type("kub")
        advanceTimeBy(SearchViewModel.DEBOUNCE.inWholeMilliseconds / 2)
        vm.type("kubrick")
        runCurrent()
        assertTrue(calls.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf("kubrick" to 1), calls)
        val results = vm.uiState.value.content as SearchContent.Results
        assertEquals("Stanley Kubrick", results.people.single().name)
        assertEquals("Director", results.people.single().role)
        assertEquals("https://image.tmdb.org/t/p/w185/p240.jpg", results.people.single().photoUrl)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun `a newer query cancels a slow older one`() = runTest(dispatcher) {
        latency = 5_000
        responses["kub"] = page(person(1, "Old result"))
        responses["kubrick"] = page(person(240, "Stanley Kubrick"))
        val vm = vm()
        subscribe(vm)

        vm.type("kub")
        advanceTimeBy(SearchViewModel.DEBOUNCE.inWholeMilliseconds + 100)
        runCurrent()
        assertTrue(vm.uiState.value.loading)
        vm.type("kubrick")
        advanceUntilIdle()

        val results = vm.uiState.value.content as SearchContent.Results
        assertEquals(listOf(240), results.people.map { it.tmdbId })
    }

    @Test fun `people tracked in any role are marked on list, live`() = runTest(dispatcher) {
        responses["kubrick"] = page(person(240, "Stanley Kubrick"), person(241, "Christiane Kubrick", "Acting"))
        tracked.value = listOf(240)
        val vm = vm()
        subscribe(vm)

        vm.type("kubrick")
        advanceUntilIdle()
        fun onList() = (vm.uiState.value.content as SearchContent.Results).people.associate { it.tmdbId to it.onList }
        assertEquals(mapOf(240 to true, 241 to false), onList())

        tracked.value = listOf(240, 241)
        runCurrent()
        assertEquals(mapOf(240 to true, 241 to true), onList())
    }

    @Test fun `no results and errors`() = runTest(dispatcher) {
        responses["zzzz"] = page()
        responses["offline"] = TmdbResult.Failure(TmdbError.NoNetwork)
        val vm = vm()
        subscribe(vm)

        vm.type(" zzzz ")
        advanceUntilIdle()
        assertEquals(SearchContent.NoResults("zzzz"), vm.uiState.value.content)

        vm.type("offline")
        advanceUntilIdle()
        assertEquals(SearchContent.Error(TmdbError.NoNetwork), vm.uiState.value.content)

        responses["offline"] = page(person(5, "Back online"))
        vm.retry()
        runCurrent()
        assertEquals(2, calls.count { it.first == "offline" }) // no debounce on retry
        assertTrue(vm.uiState.value.content is SearchContent.Results)
    }

    @Test fun `clearing goes straight back to idle without searching`() = runTest(dispatcher) {
        responses["kubrick"] = page(person(240, "Stanley Kubrick"))
        val vm = vm()
        subscribe(vm)
        vm.type("kubrick")
        advanceUntilIdle()

        vm.clear()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(SearchContent.Idle, vm.uiState.value.content)
        assertEquals("", vm.query.text.toString())
        advanceUntilIdle()
        assertEquals(1, calls.size)
    }

    @Test fun `load more appends the next page and stops at the last`() = runTest(dispatcher) {
        responses["smith"] = page(person(1, "A Smith"), person(2, "B Smith"), totalPages = 2, total = 3)
        responses["smith#2"] = page(person(2, "B Smith"), person(3, "C Smith"), page = 2, totalPages = 2, total = 3)
        val vm = vm()
        subscribe(vm)
        vm.type("smith")
        advanceUntilIdle()
        val first = vm.uiState.value.content as SearchContent.Results
        assertTrue(first.canLoadMore)
        assertEquals(3, first.totalResults)

        vm.loadMore()
        advanceUntilIdle()
        val second = vm.uiState.value.content as SearchContent.Results
        assertEquals(listOf(1, 2, 3), second.people.map { it.tmdbId }) // duplicate across pages dropped
        assertFalse(second.canLoadMore)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, calls.size)
    }

    @Test fun `a failed page keeps what was loaded and can be retried`() = runTest(dispatcher) {
        responses["smith"] = page(person(1, "A Smith"), totalPages = 2, total = 2)
        responses["smith#2"] = TmdbResult.Failure(TmdbError.NoNetwork)
        val vm = vm()
        subscribe(vm)
        vm.type("smith")
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()
        val failed = vm.uiState.value.content as SearchContent.Results
        assertTrue(failed.loadMoreFailed)
        assertEquals(1, failed.people.size)

        responses["smith#2"] = page(person(2, "B Smith"), page = 2, totalPages = 2, total = 2)
        vm.loadMore()
        advanceUntilIdle()
        val ok = vm.uiState.value.content as SearchContent.Results
        assertEquals(listOf(1, 2), ok.people.map { it.tmdbId })
        assertFalse(ok.loadMoreFailed)
    }

    @Test fun `missing photo and department`() = runTest(dispatcher) {
        responses["x"] = page(person(9, "Nobody Known", dept = null, photo = null))
        val vm = vm()
        subscribe(vm)
        vm.type("x")
        advanceUntilIdle()
        val row = (vm.uiState.value.content as SearchContent.Results).people.single()
        assertNull(row.photoUrl)
        assertNull(row.role)
    }
}
