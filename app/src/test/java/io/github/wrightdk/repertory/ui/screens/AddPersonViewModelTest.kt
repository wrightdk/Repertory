package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.RoleSummary
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddPersonViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val kubrick = Person(
        tmdbId = 240,
        name = "Stanley Kubrick",
        profilePath = null,
        knownForDepartment = "Directing",
        birthday = LocalDate.of(1928, 7, 26),
        deathday = LocalDate.of(1999, 3, 7),
        cachedAt = 0,
    )
    private val person = MutableStateFlow<Person?>(null)
    private val tracked = MutableStateFlow<List<String>>(emptyList())
    private var refreshResult: TmdbResult<Unit> = TmdbResult.Success(Unit)
    private var cachedRoles = listOf(
        RoleSummary("Directing", 13, "Director"),
        RoleSummary("Writing", 12, "Screenplay"),
        RoleSummary("Production", 11, "Producer"),
        RoleSummary("Art", 2, "Production Design"),
        RoleSummary("Crew", 3, "Thanks"),
    )
    private val trackCalls = mutableListOf<List<String>>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AddPersonViewModel(
        personId = kubrick.tmdbId,
        refreshPerson = {
            if (refreshResult is TmdbResult.Success) person.value = kubrick
            refreshResult
        },
        person = person,
        roles = { if (person.value == null) emptyList() else cachedRoles },
        trackedDepartments = tracked,
        imageConfig = { ImageConfig.DEFAULT },
        track = { _, departments ->
            trackCalls += departments
            departments.indices.map { 100L + it }
        },
    )

    private fun TestScope.started(): AddPersonViewModel = vm().also { vm ->
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private val AddPersonViewModel.loaded get() = uiState.value.content as AddPersonContent.Loaded

    @Test fun `pre-ticks the known-for department and labels jobs`() = runTest(dispatcher) {
        val vm = started()
        val c = vm.loaded
        assertEquals("1928–1999", c.header.lifeDates)
        // Only the seven roles the design names are offered: no Art, no Crew.
        assertEquals(listOf("Directing", "Writing", "Production"), c.roles.map { it.department })
        assertEquals(listOf(true, false, false), c.roles.map { it.checked })
        assertEquals(listOf("Director", "Writer", "Producer"), c.roles.map { it.jobLabel })
        assertEquals(1, c.selectedCount)
    }

    @Test fun `tracked roles are not pre-ticked and cannot be ticked`() = runTest(dispatcher) {
        tracked.value = listOf("Directing")
        val vm = started()
        assertEquals(0, vm.loaded.selectedCount)
        vm.toggle("Directing")
        advanceUntilIdle()
        val directing = vm.loaded.roles.first()
        assertTrue(directing.tracked)
        assertEquals(false, directing.checked)
        assertEquals(0, vm.loaded.selectedCount)
    }

    @Test fun `adding tracks ticked roles in display order and reports the first list`() = runTest(dispatcher) {
        val vm = started()
        vm.toggle("Production")
        vm.toggle("Writing")
        advanceUntilIdle()
        assertEquals(3, vm.loaded.selectedCount)
        vm.add()
        advanceUntilIdle()
        assertEquals(listOf(listOf("Directing", "Writing", "Production")), trackCalls)
        assertEquals(100L, vm.addedListId.value)
    }

    @Test fun `nothing ticked adds nothing`() = runTest(dispatcher) {
        val vm = started()
        vm.toggle("Directing")
        advanceUntilIdle()
        vm.add()
        advanceUntilIdle()
        assertTrue(trackCalls.isEmpty())
        assertNull(vm.addedListId.value)
    }

    @Test fun `refresh failure with nothing cached is an error, and retry recovers`() = runTest(dispatcher) {
        refreshResult = TmdbResult.Failure(TmdbError.NoNetwork)
        val vm = started()
        assertEquals(AddPersonContent.Error(TmdbError.NoNetwork), vm.uiState.value.content)
        refreshResult = TmdbResult.Success(Unit)
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, vm.loaded.selectedCount)
    }

    @Test fun `refresh failure falls back to the cache`() = runTest(dispatcher) {
        person.value = kubrick
        refreshResult = TmdbResult.Failure(TmdbError.NoNetwork)
        val vm = started()
        assertEquals(3, vm.loaded.roles.size)
    }

    @Test fun `life dates`() {
        assertEquals("1928–1999", lifeDates(LocalDate.of(1928, 1, 1), LocalDate.of(1999, 1, 1)))
        assertEquals("b. 1983", lifeDates(LocalDate.of(1983, 1, 1), null))
        assertEquals("d. 1999", lifeDates(null, LocalDate.of(1999, 1, 1)))
        assertNull(lifeDates(null, null))
    }
}
