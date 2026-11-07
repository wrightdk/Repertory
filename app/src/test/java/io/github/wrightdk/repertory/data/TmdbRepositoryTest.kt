package io.github.wrightdk.repertory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer
import io.github.wrightdk.repertory.data.remote.FakeTmdbServer.Companion.json
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.errorOrNull
import io.github.wrightdk.repertory.data.remote.fixture
import io.github.wrightdk.repertory.data.remote.getOrNull
import java.time.LocalDate
import kotlin.time.Duration
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
        detailsRequestInterval = Duration.ZERO,
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
        assertEquals(1, page.totalResults)
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
        assertEquals(LocalDate.of(1928, 5, 30), person.birthday)
        assertEquals(LocalDate.of(2019, 3, 29), person.deathday)
        assertEquals("Directing", person.knownForDepartment)
        assertEquals(now, person.cachedAt)

        val directing = repo.observeFilmography(VARDA, "Directing").first()
        assertEquals(
            "chronological, TV series included",
            listOf("Cléo from 5 to 7", "Uncle Yanco", "Nausicaa", "Vagabond", "Faces Places", "Varda by Agnès"),
            directing.map { it.film.title },
        )
        assertEquals(setOf("Director"), directing.map { it.credit.job }.toSet())
        assertEquals(MediaType.TV, directing.last().film.mediaType)
        assertEquals(listOf(10770, 18), directing.single { it.film.tmdbId == 324072 }.film.genreIds)

        val acting = repo.observeFilmography(VARDA, "Acting").first()
        assertEquals(4, acting.size)
        val charlie = acting.single { it.film.tmdbId == 13440 }.credit
        assertEquals("The Widow Hyppolite", charlie.character)
        assertEquals(11, charlie.order)
        assertEquals(
            "appearances as self",
            setOf(929052, 87772),
            acting.filter { it.credit.isSelf }.map { it.film.tmdbId }.toSet(),
        )
    }

    @Test fun `departments are counted by distinct film under the default filters`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)

        val departments = repo.departments(VARDA).associate { it.department to it.filmCount }

        // Directing has 6 titles, one a TV film and one a series. Acting has 4
        // credits: 2 as Self and 1 uncredited (a minor role).
        assertEquals(
            mapOf("Directing" to 4, "Acting" to 1, "Writing" to 2, "Editing" to 1, "Sound" to 1),
            departments,
        )

        val everything = repo.departments(VARDA, FilmographyFilters(true, true, true, true, true))
            .associate { it.department to it.filmCount }
        assertEquals(
            mapOf("Directing" to 6, "Acting" to 4, "Writing" to 2, "Editing" to 1, "Sound" to 1),
            everything,
        )
    }

    @Test fun `a department with only appearances as self is not offered`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        db.creditDao().replaceForPerson(
            VARDA,
            listOf(
                Credit(personId = VARDA, mediaType = MediaType.MOVIE, filmId = 929052, department = "Acting",
                    job = null, character = "Self", order = 0, isSelf = true),
                Credit(personId = VARDA, mediaType = MediaType.TV, filmId = 87772, department = "Acting",
                    job = null, character = "Herself (archive footage)", order = 1, isSelf = true),
                Credit(personId = VARDA, mediaType = MediaType.MOVIE, filmId = 499, department = "Directing",
                    job = "Director", character = null, order = null, isSelf = false),
            ),
        )

        assertEquals(listOf(RoleSummary("Directing", 1, "Director")), repo.roles(VARDA))
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

        assertEquals(6, repo.observeFilmography(VARDA, "Directing").first().size)
    }

    @Test fun `failed refresh keeps the cache and reports the error`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        tmdb.route("/person/$VARDA", json(429, fixture("error_429.json")))

        val result = repo.refreshPerson(VARDA, force = true)

        assertEquals(TmdbError.RateLimited, result.errorOrNull())
        assertEquals("Agnès Varda", repo.observePerson(VARDA).first()?.name)
        assertEquals(6, repo.observeFilmography(VARDA, "Directing").first().size)
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

    @Test fun `fillMissingFilmDetails fetches a list's runtimes and status, marks shorts and checks missing films`() = runTest {
        routeVarda()
        tmdb.routeFixture("/movie/499", "movie_feature.json")
        tmdb.routeFixture("/movie/277677", "movie_short.json")
        repo.refreshPerson(VARDA)
        assertEquals(5, repo.observeMissingDetailsCount(VARDA, "Directing").first())

        // 5 directed movies; only 2 have fixtures, the other 3 get 404.
        assertEquals(TmdbResult.Success(2), repo.fillMissingFilmDetails(VARDA, "Directing"))
        assertEquals("the TV series is never sent to /movie", 0, tmdb.requestCount("/movie/87772"))
        assertEquals("acting credits aren't in this list", 0, tmdb.requestCount("/movie/929052"))

        val byId = repo.observeFilmography(VARDA, "Directing").first().associate { it.film.tmdbId to it.film }
        assertTrue(byId.getValue(277677).isShort)
        assertEquals(18, byId.getValue(277677).runtime)
        assertEquals("Released", byId.getValue(277677).status)
        assertFalse(byId.getValue(499).isShort)
        assertEquals(90, byId.getValue(499).runtime)
        assertNull(byId.getValue(44018).runtime)
        assertEquals("a 404 doesn't hold the list up", 0, repo.observeMissingDetailsCount(VARDA, "Directing").first())

        val before = tmdb.requests.size
        assertEquals(TmdbResult.Success(0), repo.fillMissingFilmDetails(VARDA, "Directing"))
        assertEquals("nothing left to fetch", before, tmdb.requests.size)
    }

    @Test fun `fillMissingFilmDetails stops on a bad key`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        val before = tmdb.requests.size
        for (id in listOf(499, 44018, 277677, 324072, 451995)) {
            tmdb.route("/movie/$id", json(401, fixture("error_401.json")))
        }

        assertEquals(TmdbError.InvalidApiKey, repo.fillMissingFilmDetails(VARDA, "Directing").errorOrNull())
        assertEquals("only the first batch of 4 is tried", 4, tmdb.requests.size - before)
        assertEquals(5, repo.observeMissingDetailsCount(VARDA, "Directing").first())
    }

    @Test fun `fillMissingFilmDetails retries server errors and rate limits`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        for (id in listOf(44018, 277677, 324072, 451995)) tmdb.routeFixture("/movie/$id", "movie_short.json")
        tmdb.route(
            "/movie/499",
            json(503, "{}"),
            json(429, fixture("error_429.json")),
            json(200, fixture("movie_feature.json")),
        )

        assertTrue(repo.fillMissingFilmDetails(VARDA, "Directing") is TmdbResult.Success)
        assertEquals(3, tmdb.requestCount("/movie/499"))
        assertEquals(90, db.filmDao().get(MediaType.MOVIE, 499)!!.runtime)
    }

    @Test fun `fillMissingFilmDetails gives up on a list after repeated server errors`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        for (id in listOf(499, 44018, 277677, 324072, 451995)) tmdb.route("/movie/$id", json(503, "{}"))

        assertEquals(TmdbError.Http(503), repo.fillMissingFilmDetails(VARDA, "Directing").errorOrNull())
        assertEquals("first try plus 3 retries", 4, tmdb.requestCount("/movie/499"))
        assertEquals("not marked as checked", 5, repo.observeMissingDetailsCount(VARDA, "Directing").first())
    }

    @Test fun `fillMissingFilmDetails refetches stale details of unreleased films only`() = runTest {
        routeVarda()
        // Only 499 has details; the other four 404 and are marked as checked.
        tmdb.routeFixture("/movie/499", "movie_feature.json")
        repo.refreshPerson(VARDA)
        repo.fillMissingFilmDetails(VARDA, "Directing")
        val film = db.filmDao().get(MediaType.MOVIE, 499)!!
        db.filmDao().upsert(film.copy(status = "Post Production", releaseDate = null))

        now += 2.days.inWholeMilliseconds
        repo.fillMissingFilmDetails(VARDA, "Directing")
        assertEquals(2, tmdb.requestCount("/movie/499"))
        assertEquals("released films stay cached", 1, tmdb.requestCount("/movie/44018"))
    }

    @Test fun `refreshing a person keeps film details already fetched`() = runTest {
        routeVarda()
        tmdb.routeFixture("/movie/277677", "movie_short.json")
        repo.refreshPerson(VARDA)
        repo.refreshMovie(277677)

        repo.refreshPerson(VARDA, force = true)

        val yanco = repo.observeFilm(MediaType.MOVIE, 277677).first()!!
        assertEquals(18, yanco.runtime)
        assertEquals("Released", yanco.status)
        assertTrue(yanco.isShort)
        assertNotNull(yanco.detailsCachedAt)
    }

    @Test fun `film details are cached`() = runTest {
        tmdb.routeFixture("/movie/499", "movie_feature.json")

        repo.refreshMovie(499)
        repo.refreshMovie(499)
        assertEquals(1, tmdb.requestCount("/movie/499"))

        now += 31.days.inWholeMilliseconds
        repo.refreshMovie(499)
        assertEquals(2, tmdb.requestCount("/movie/499"))
    }

    @Test fun `details of unreleased films go stale after a day`() = runTest {
        tmdb.routeFixture("/movie/499", "movie_feature.json")
        repo.refreshMovie(499)
        val film = db.filmDao().get(MediaType.MOVIE, 499)!!
        db.filmDao().upsert(film.copy(status = "Post Production", releaseDate = null))

        now += 2.days.inWholeMilliseconds
        repo.refreshMovie(499)
        assertEquals(2, tmdb.requestCount("/movie/499"))
    }

    // ---- User data ----

    @Test fun `viewing is per film, with an optional date`() = runTest {
        val viewings = db.viewingDao()
        viewings.upsert(Viewing(MediaType.MOVIE, 499, seenOn = LocalDate.of(2023, 7, 19)))
        assertEquals(LocalDate.of(2023, 7, 19), viewings.observe(MediaType.MOVIE, 499).first()?.seenOn)

        viewings.upsert(Viewing(MediaType.MOVIE, 499, seenOn = null))
        val cleared = viewings.observe(MediaType.MOVIE, 499).first()!!
        assertNull("date cleared but still seen", cleared.seenOn)
        assertNull("same id as a movie, different title", viewings.observe(MediaType.TV, 499).first())

        viewings.delete(MediaType.MOVIE, 499)
        assertNull(viewings.observe(MediaType.MOVIE, 499).first())
    }

    @Test fun `viewings survive refreshing the cache and removing the list`() = runTest {
        routeVarda()
        repo.refreshPerson(VARDA)
        val lists = db.trackedPersonDao()
        lists.insert(TrackedPerson(personId = VARDA, department = "Directing", addedAt = now))
        db.viewingDao().upsert(Viewing(MediaType.MOVIE, 499, seenOn = null))

        repo.refreshPerson(VARDA, force = true)
        lists.delete(lists.observeAll().first().single())
        // Evict the whole TMDB cache (credits cascade).
        db.openHelper.writableDatabase.execSQL("DELETE FROM person")
        db.openHelper.writableDatabase.execSQL("DELETE FROM film")

        assertNull(repo.observeFilm(MediaType.MOVIE, 499).first())
        assertEquals(listOf(Viewing(MediaType.MOVIE, 499, null)), db.viewingDao().observeAll().first())
    }

    @Test fun `list filter override is stored, and null means follow the defaults`() = runTest {
        val lists = db.trackedPersonDao()
        lists.insert(TrackedPerson(personId = VARDA, department = "Directing", addedAt = now))
        val custom = FilmographyFilters.DEFAULTS.copy(showShorts = true, showSelf = true)
        lists.insert(TrackedPerson(personId = VARDA, department = "Acting", addedAt = now, filterOverride = custom))

        val byDept = lists.observeAll().first().associateBy { it.department }

        assertNull(byDept.getValue("Directing").filterOverride)
        assertEquals(custom, byDept.getValue("Acting").filterOverride)
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
