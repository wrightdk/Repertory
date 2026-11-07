package io.github.wrightdk.repertory.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Retrofit service against saved TMDB responses: URLs, auth and parsing. */
class TmdbServiceTest {
    private val tmdb = FakeTmdbServer()
    private val service = tmdb.service()

    @After fun tearDown() = tmdb.close()

    @Test fun `search sends query, page, adult filter and api key`() = runTest {
        tmdb.routeFixture("/search/person", "search_person.json")

        val response = service.searchPeople("agnes varda")

        val url = tmdb.requests.single().url
        assertEquals("agnes varda", url.queryParameter("query"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals(FakeTmdbServer.TEST_KEY, url.queryParameter("api_key"))

        val first = response.results.single()
        assertEquals(6817, first.id)
        assertEquals("Agnès Varda", first.name)
        assertEquals("Directing", first.knownForDepartment)
        assertTrue(first.knownFor.any { it.title == "Cléo from 5 to 7" })
    }

    @Test fun `api key is read per request`() = runTest {
        tmdb.routeFixture("/configuration", "configuration.json")
        var key = "first"
        val service = TmdbClient.createService(keyProvider = { key }, baseUrl = tmdb.baseUrl)

        service.configuration()
        key = "second"
        service.configuration()

        assertEquals(listOf("first", "second"), tmdb.requests.map { it.url.queryParameter("api_key") })
    }

    @Test fun `parses person`() = runTest {
        tmdb.routeFixture("/person/6817", "person.json")
        val person = service.person(6817)
        assertEquals("Agnès Varda", person.name)
        assertEquals("1928-05-30", person.birthday)
        assertTrue(person.profilePath!!.startsWith("/"))
    }

    @Test fun `parses combined credits, movie and tv`() = runTest {
        tmdb.routeFixture("/person/6817/combined_credits", "combined_credits.json")
        val credits = service.combinedCredits(6817)

        assertEquals(4, credits.cast.size)
        assertEquals(10, credits.crew.size)
        val cleoDirector = credits.crew.first { it.id == 499 && it.job == "Director" }
        assertEquals("movie", cleoDirector.mediaType)
        assertEquals("Directing", cleoDirector.department)
        assertEquals("1962-04-11", cleoDirector.releaseDate)
        // TV entries use name/first_air_date, so title and release_date are absent.
        val tv = credits.cast.first { it.mediaType == "tv" }
        assertNull(tv.title)
        assertNull(tv.releaseDate)
    }

    @Test fun `parses movie details`() = runTest {
        tmdb.routeFixture("/movie/277677", "movie_short.json")
        val movie = service.movie(277677)
        assertEquals("Uncle Yanco", movie.title)
        assertEquals(18, movie.runtime)
        assertEquals(listOf(99), movie.genres.map { it.id })
    }

    @Test fun `parses configuration`() = runTest {
        tmdb.routeFixture("/configuration", "configuration.json")
        val images = service.configuration().images
        assertEquals("https://image.tmdb.org/t/p/", images.secureBaseUrl)
        assertTrue("w342" in images.posterSizes)
        assertTrue("w185" in images.profileSizes)
    }
}
