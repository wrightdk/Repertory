package io.github.wrightdk.repertory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.entity.WatchProvidersCache
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer.Companion.json
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.TmdbService
import io.github.wrightdk.repertory.data.remote.errorOrNull
import io.github.wrightdk.repertory.data.remote.getOrNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Where-to-watch cache against an in-memory Room database and saved TMDB responses. */
@RunWith(RobolectricTestRunner::class)
class WatchProvidersRepositoryTest {
    private val tmdb = FakeTmdbServer()
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        RepertoryDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao = db.watchProvidersDao()
    private var now = 1_700_000_000_000L

    private fun repoWith(service: TmdbService = tmdb.service()) =
        WatchProvidersRepository(service, dao, db.watchRegionDao(), clock = { now }, retryPolicy = RetryPolicy.NONE)

    private val repo = repoWith()

    @After fun tearDown() {
        db.close()
        tmdb.close()
    }

    private fun advance(by: kotlin.time.Duration) {
        now += by.inWholeMilliseconds
    }

    private fun route(fixtureName: String) = tmdb.routeFixture(PATH, fixtureName)

    private fun requests() = tmdb.requestCount(PATH)

    /** A server that's gone: every request fails to connect, as when offline. */
    private fun offlineService(): TmdbService = FakeTmdbServer().let { gone -> gone.service().also { gone.close() } }

    @Test fun `first load fetches, parses GB and caches it`() = runTest {
        route("watch_providers_full.json")

        val a = repo.load(MOVIE, GB).getOrNull()!!

        assertEquals(listOf("MUBI"), a.stream.map { it.name })
        assertEquals(1, requests())
        val row = dao.get(MOVIE, GB)!!
        assertEquals(a, row.availability)
        assertEquals(now, row.fetchedAt)
    }

    @Test fun `cache hit within 24 hours makes no request`() = runTest {
        route("watch_providers_full.json")
        val first = repo.load(MOVIE, GB).getOrNull()

        advance(23.hours + 59.minutes)
        val second = repo.load(MOVIE, GB).getOrNull()

        assertEquals(first, second)
        assertEquals(1, requests())
    }

    @Test fun `after 24 hours it fetches again and replaces the cache`() = runTest {
        route("watch_providers_full.json")
        repo.load(MOVIE, GB)

        advance(24.hours)
        route("watch_providers_rent_only.json")
        val a = repo.load(MOVIE, GB).getOrNull()!!

        assertEquals(2, requests())
        assertTrue(a.stream.isEmpty())
        assertEquals(listOf("Apple TV"), a.rent.map { it.name })
        assertEquals(now, dao.get(MOVIE, GB)!!.fetchedAt)
    }

    @Test fun `nowhere to watch is cached too`() = runTest {
        route("watch_providers_no_gb.json")

        assertEquals(WatchAvailability.NONE, repo.load(MOVIE, GB).getOrNull())
        advance(12.hours)
        assertEquals(WatchAvailability.NONE, repo.load(MOVIE, GB).getOrNull())

        assertEquals(1, requests())
        assertNotNull(dao.get(MOVIE, GB))
    }

    @Test fun `nowhere to watch expires like anything else`() = runTest {
        route("watch_providers_no_gb.json")
        repo.load(MOVIE, GB)

        advance(25.hours)
        route("watch_providers_full.json")

        assertEquals(listOf("MUBI"), repo.load(MOVIE, GB).getOrNull()!!.stream.map { it.name })
        assertEquals(2, requests())
    }

    @Test fun `offline with an expired entry serves the expired entry`() = runTest {
        route("watch_providers_full.json")
        val cached = repo.load(MOVIE, GB).getOrNull()

        advance(30.hours)
        val result = repoWith(offlineService()).load(MOVIE, GB)

        assertEquals(cached, result.getOrNull())
        // The old copy is kept, not re-stamped, so it still goes at 48 hours.
        assertEquals(now - 30.hours.inWholeMilliseconds, dao.get(MOVIE, GB)!!.fetchedAt)
    }

    @Test fun `an API failure with an expired entry serves the expired entry`() = runTest {
        route("watch_providers_full.json")
        val cached = repo.load(MOVIE, GB).getOrNull()

        advance(30.hours)
        tmdb.route(PATH, json(503, """{"status_code": 9, "status_message": "Service offline."}"""))

        assertEquals(cached, repo.load(MOVIE, GB).getOrNull())
    }

    @Test fun `offline with nothing cached is an error`() = runTest {
        assertEquals(TmdbError.NoNetwork, repoWith(offlineService()).load(MOVIE, GB).errorOrNull())
    }

    @Test fun `an entry older than 48 hours is never served, and is deleted`() = runTest {
        route("watch_providers_full.json")
        repo.load(MOVIE, GB)

        advance(48.hours)
        val result = repoWith(offlineService()).load(MOVIE, GB)

        assertEquals(TmdbError.NoNetwork, result.errorOrNull())
        assertNull(dao.get(MOVIE, GB))
    }

    @Test fun `an entry from the future (clock moved back) is refetched`() = runTest {
        route("watch_providers_full.json")
        dao.upsert(WatchProvidersCache(MOVIE, GB, WatchAvailability.NONE, fetchedAt = now + 1.hours.inWholeMilliseconds))

        assertEquals(listOf("MUBI"), repo.load(MOVIE, GB).getOrNull()!!.stream.map { it.name })
        assertEquals(1, requests())
    }

    @Test fun `purge deletes entries older than 48 hours and keeps the rest`() = runTest {
        fun entry(id: Int, ageHours: Int) =
            WatchProvidersCache(id, GB, WatchAvailability.NONE, fetchedAt = now - ageHours.hours.inWholeMilliseconds)
        dao.upsert(entry(1, 1))
        dao.upsert(entry(2, 30))
        dao.upsert(entry(3, 49))
        dao.upsert(entry(4, 24 * 30))

        assertEquals(2, repo.purgeExpired())

        assertNotNull(dao.get(1, GB))
        assertNotNull(dao.get(2, GB))
        assertNull(dao.get(3, GB))
        assertNull(dao.get(4, GB))
    }

    @Test fun `another region's cached entry isn't used`() = runTest {
        dao.upsert(WatchProvidersCache(MOVIE, "FR", WatchAvailability.NONE, fetchedAt = now))
        route("watch_providers_full.json")

        val result = repo.load(MOVIE, GB)

        assertTrue(result is TmdbResult.Success)
        assertEquals(1, requests())
    }

    @Test fun `each country is picked out and cached on its own, and the country is never sent`() = runTest {
        route("watch_providers_full.json")

        val gb = repo.load(MOVIE, GB).getOrNull()!!
        val us = repo.load(MOVIE, "US").getOrNull()!!

        assertEquals(listOf("MUBI"), gb.stream.map { it.name })
        assertEquals(listOf("Max"), us.stream.map { it.name })
        assertEquals(2, requests())
        assertTrue(tmdb.requests.none { r -> r.url.queryParameterNames.any { it != "api_key" } })
        // Both stay cached: going back to GB makes no request.
        repo.load(MOVIE, GB)
        assertEquals(2, requests())
    }

    // ---- Countries ----

    private val regionDao = db.watchRegionDao()

    private fun regionRequests() = tmdb.requestCount(REGIONS_PATH)

    private fun routeRegions() = tmdb.routeFixture(REGIONS_PATH, "watch_regions.json")

    @Test fun `the country list is fetched, cleaned up and cached`() = runTest {
        routeRegions()

        val regions = repo.regions().getOrNull()!!

        // Blank and missing codes dropped; a repeated code kept once.
        assertEquals(listOf("GB", "FR", "CI", "US"), regions.map { it.code })
        assertEquals("United Kingdom", regions.first().englishName)
        assertEquals(4, regionDao.getAll().size)
    }

    @Test fun `the country list is served from the cache for 90 days`() = runTest {
        routeRegions()
        repo.regions()

        advance(89.days)
        repo.regions()
        assertEquals(1, regionRequests())

        advance(1.days)
        repo.regions()
        assertEquals(2, regionRequests())
    }

    @Test fun `offline, a country list up to 180 days old is still served`() = runTest {
        routeRegions()
        repo.regions()

        advance(179.days)
        val result = repoWith(offlineService()).regions()

        assertEquals(4, result.getOrNull()!!.size)
    }

    @Test fun `a country list 180 days old is deleted, never served`() = runTest {
        routeRegions()
        repo.regions()

        advance(180.days)
        val result = repoWith(offlineService()).regions()

        assertEquals(TmdbError.NoNetwork, result.errorOrNull())
        assertTrue(regionDao.getAll().isEmpty())
    }

    @Test fun `an empty country list from TMDB doesn't replace the one we have`() = runTest {
        routeRegions()
        repo.regions()

        advance(91.days)
        tmdb.route(REGIONS_PATH, json(200, """{"results": []}"""))

        assertEquals(4, repo.regions().getOrNull()!!.size)
        assertEquals(4, regionDao.getAll().size)
    }

    @Test fun `purge deletes a country list older than 180 days`() = runTest {
        routeRegions()
        repo.regions()

        advance(100.days)
        assertEquals(0, repo.purgeExpired())
        advance(81.days)
        assertEquals(4, repo.purgeExpired())
        assertTrue(regionDao.getAll().isEmpty())
    }

    private companion object {
        const val MOVIE = 62
        const val GB = "GB"
        const val PATH = "/movie/$MOVIE/watch/providers"
        const val REGIONS_PATH = "/watch/providers/regions"
    }
}
