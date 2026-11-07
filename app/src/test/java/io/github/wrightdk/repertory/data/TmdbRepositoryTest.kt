package io.github.wrightdk.repertory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer.Companion.json
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.errorOrNull
import io.github.wrightdk.repertory.data.remote.fixture
import io.github.wrightdk.repertory.data.remote.getOrNull
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Repository against an in-memory Room database and saved TMDB responses. */
@RunWith(RobolectricTestRunner::class)
class TmdbRepositoryTest {
    private val tmdb = FakeTmdbServer()
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        RepertoryDatabase::class.java,
    ).allowMainThreadQueries().build()
    private var now = 1_700_000_000_000L
    private val repo = TmdbRepository(
        service = tmdb.service(),
        database = db,
        clock = { now },
        retryPolicy = RetryPolicy.NONE,
    )

    @After fun tearDown() {
        db.close()
        tmdb.close()
    }

    private fun routeVarda() {
        tmdb.routeFixture("/person/$VARDA", "person.json")
        tmdb.routeFixture("/person/$VARDA/combined_credits", "combined_credits.json")
    }

    // ---- Search ----

    @Test fun `search maps results`() = runTest {
        tmdb.routeFixture("/search/person", "search_person.json")

        val page = repo.searchPeople("  agnes varda ").getOrNull()!!

        assertEquals("agnes varda", tmdb.requests.single().url.queryParameter("query"))
        val varda = page.results.single()
        assertEquals(VARDA, varda.tmdbId)
        assertEquals("Directing", varda.knownForDepartment)
        assertTrue("Cléo from 5 to 7" in varda.knownForTitles)
    }

    @Test fun `blank search makes no request`() = runTest {
        assertEquals(PersonSearchPage.EMPTY, repo.searchPeople("   ").getOrNull())
        assertTrue(tmdb.requests.isEmpty())
    }

    @Test fun `search with a bad key is InvalidApiKey`() = runTest {
        tmdb.route("/search/person", json(401, fixture("error_401.json")))
        assertEquals(TmdbError.InvalidApiKey, repo.searchPeople("varda").errorOrNull())
    }

    // ---- People ----

    @Test fun `refreshPerson caches person, films and credits`() = runTest {
        routeVarda()

        assertEquals(TmdbResult.Success(Unit), repo.refreshPerson(VARDA))

        val person = repo.observePerson(VARDA).first()!!
        assertEquals("Agnès Varda", person.name)
        assertEquals(now, person.cachedAt)

        val directing = repo.observeFilmography(VARDA, "Directing").first()
        assertEquals(
            "chronological",
            listOf("Cléo from 5 to 7", "Uncle Yanco", "Nausicaa", "Vagabond", "Faces Places"),
            directing.map { it.film.title },
        )
        assertEquals(setOf("Director"), directing.map { it.credit.job }.toSet())

        val acting = repo.observeFilmography(VARDA, "Acting").first()
        assertEquals(3, acting.size)
        assertEquals("The Widow Hyppolite", acting.single { it.film.tmdbId == 13440 }.credit.character)
    }

    @Test fun `departments are counted by distinct film`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)

        val departments = repo.departments(VARDA).associate { it.department to it.filmCount }

        assertEquals(
            mapOf("Directing" to 5, "Acting" to 3, "Writing" to 2, "Editing" to 1, "Sound" to 1),
            departments,
        )
    }

    @Test fun `fresh person is served from cache without a request`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        val before = tmdb.requests.size

        now += 1.days.inWholeMilliseconds
        assertEquals(TmdbResult.Success(Unit), repo.refreshPerson(VARDA))

        assertEquals(before, tmdb.requests.size)
    }

    @Test fun `stale or forced person is refetched`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)

        repo.refreshPerson(VARDA, force = true)
        assertEquals(2, tmdb.requestCount("/person/$VARDA"))

        now += 8.days.inWholeMilliseconds
        repo.refreshPerson(VARDA)
        assertEquals(3, tmdb.requestCount("/person/$VARDA"))
    }

    @Test fun `refreshing credits does not duplicate them`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        repo.refreshPerson(VARDA, force = true)

        assertEquals(5, repo.observeFilmography(VARDA, "Directing").first().size)
    }

    @Test fun `failed refresh keeps the cache and reports the error`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        tmdb.route("/person/$VARDA", json(429, fixture("error_429.json")))

        val result = repo.refreshPerson(VARDA, force = true)

        assertEquals(TmdbError.RateLimited, result.errorOrNull())
        assertEquals("Agnès Varda", repo.observePerson(VARDA).first()?.name)
        assertEquals(5, repo.observeFilmography(VARDA, "Directing").first().size)
    }

    @Test fun `offline with nothing cached reports NoNetwork and stores nothing`() = runTest {
        val offlineRepo = offlineRepository()

        assertEquals(TmdbError.NoNetwork, offlineRepo.refreshPerson(VARDA).errorOrNull())
        assertNull(offlineRepo.observePerson(VARDA).first())
    }

    @Test fun `credits failing after person succeeds stores nothing`() = runTest {
        tmdb.routeFixture("/person/$VARDA", "person.json")
        tmdb.route("/person/$VARDA/combined_credits", json(500, "{}"))

        assertEquals(TmdbError.Http(500), repo.refreshPerson(VARDA).errorOrNull())
        // Otherwise a fresh-looking person with no credits would block the next refresh.
        assertNull(repo.observePerson(VARDA).first())
    }

    @Test fun `unknown person is NotFound`() = runTest {
        assertEquals(TmdbError.NotFound, repo.refreshPerson(1).errorOrNull())
    }

    // ---- Films ----

    @Test fun `fillMissingFilmDetails fetches runtimes, marks shorts and skips missing films`() = runTest {
        routeVarda()
        tmdb.routeFixture("/movie/499", "movie_feature.json")
        tmdb.routeFixture("/movie/277677", "movie_short.json")
        repo.refreshPerson(VARDA)

        // 8 films; only 2 have fixtures, the other 6 get 404 and are skipped.
        assertEquals(TmdbResult.Success(2), repo.fillMissingFilmDetails(VARDA))

        val byId = repo.observeFilmography(VARDA, "Directing").first().associate { it.film.tmdbId to it.film }
        assertTrue(byId.getValue(277677).isShort)
        assertEquals(18, byId.getValue(277677).runtime)
        assertFalse(byId.getValue(499).isShort)
        assertEquals(90, byId.getValue(499).runtime)
        assertNull(byId.getValue(44018).runtime)
    }

    @Test fun `fillMissingFilmDetails stops on a bad key`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        val before = tmdb.requests.size
        for (id in listOf(499, 44018, 277677, 324072, 451995, 929052, 13440, 2433)) {
            tmdb.route("/movie/$id", json(401, fixture("error_401.json")))
        }

        assertEquals(TmdbError.InvalidApiKey, repo.fillMissingFilmDetails(VARDA).errorOrNull())
        assertEquals("only the first batch of 4 is tried", 4, tmdb.requests.size - before)
    }

    @Test fun `refreshing a person keeps film details already fetched`() = runTest {
        routeVarda()
        tmdb.routeFixture("/movie/277677", "movie_short.json")
        repo.refreshPerson(VARDA)
        repo.refreshFilm(277677)

        repo.refreshPerson(VARDA, force = true)

        val yanco = repo.observeFilm(277677).first()!!
        assertEquals(18, yanco.runtime)
        assertTrue(yanco.isShort)
        assertNotNull(yanco.detailsCachedAt)
    }

    @Test fun `film details are cached`() = runTest {
        tmdb.routeFixture("/movie/499", "movie_feature.json")

        repo.refreshFilm(499)
        repo.refreshFilm(499)
        assertEquals(1, tmdb.requestCount("/movie/499"))

        now += 31.days.inWholeMilliseconds
        repo.refreshFilm(499)
        assertEquals(2, tmdb.requestCount("/movie/499"))
    }

    // ---- Images ----

    @Test fun `image config is fetched once and cached`() = runTest {
        tmdb.routeFixture("/configuration", "configuration.json")

        val first = repo.imageConfig()
        val second = repo.imageConfig()

        assertEquals("https://image.tmdb.org/t/p/", first.secureBaseUrl)
        assertTrue("w342" in first.posterSizes)
        assertEquals(first, second)
        assertEquals(1, tmdb.requestCount("/configuration"))
    }

    @Test fun `image config falls back to default when offline`() = runTest {
        assertEquals(ImageConfig.DEFAULT, offlineRepository().imageConfig())
    }

    @Test fun `stale image config is still used when refresh fails`() = runTest {
        tmdb.routeFixture("/configuration", "configuration.json")
        val cached = repo.imageConfig()
        tmdb.route("/configuration", json(503, "{}"))
        now += 4.days.inWholeMilliseconds

        assertEquals(cached, repo.imageConfig())
    }

    private fun offlineRepository(): TmdbRepository {
        val deadUrl = FakeTmdbServer().use { it.baseUrl } // Closed straight away.
        return TmdbRepository(
            service = TmdbClient.createService(keyProvider = { "k" }, baseUrl = deadUrl),
            database = db,
            clock = { now },
            retryPolicy = RetryPolicy.NONE,
        )
    }

    private companion object {
        const val VARDA = 6817
    }
}
