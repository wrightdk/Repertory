package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.WatchAvailability
import io.github.wrightdk.repertory.data.WatchProvider
import io.github.wrightdk.repertory.data.local.dao.CreditInfo
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import io.github.wrightdk.repertory.data.local.entity.WatchRegion
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmDetailModelTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun film(id: Int, title: String, date: LocalDate?, runtime: Int? = 100, status: String? = "Released", tvMovie: Boolean = false) =
        Film(
            mediaType = MediaType.MOVIE,
            tmdbId = id,
            title = title,
            releaseDate = date,
            posterPath = null,
            genreIds = emptyList(),
            runtime = runtime,
            isShort = runtime != null && runtime <= 40,
            isDocumentary = false,
            isTvMovie = tvMovie,
            status = status,
            cachedAt = 0,
            detailsCachedAt = 0,
        )

    private fun person(id: Int, name: String) = Person(tmdbId = id, name = name, profilePath = null, knownForDepartment = null, birthday = null, deathday = null, cachedAt = 0)

    private fun crew(f: Film, job: String = "Director") = FilmCredit(f, CreditInfo(job, null, null, false))
    private fun cast(f: Film, character: String?, order: Int = 0) = FilmCredit(f, CreditInfo(null, character, order, false))

    private val space = film(62, "2001: A Space Odyssey", LocalDate.of(1968, 4, 2), 149)
    private val lolita = film(802, "Lolita", LocalDate.of(1962, 6, 13), 153)
    private val shining = film(694, "The Shining", LocalDate.of(1980, 5, 23), 144)
    private val tvFilm = film(900, "A TV Film", LocalDate.of(1975, 1, 1), tvMovie = true)

    private val kubrick = person(240, "Stanley Kubrick")
    private val keir = person(9, "Keir Dullea")
    private val directing = TrackedPerson(id = 1, personId = 240, department = "Directing", addedAt = 1)
    private val writing = TrackedPerson(id = 2, personId = 240, department = "Writing", addedAt = 2)
    private val acting = TrackedPerson(id = 3, personId = 9, department = "Acting", addedAt = 3)

    private fun view(
        list: TrackedPerson,
        p: Person?,
        credits: List<FilmCredit>,
        filmId: Int = space.tmdbId,
        seen: List<Viewing> = emptyList(),
        missing: Int = 0,
        defaults: FilmographyFilters = FilmographyFilters.DEFAULTS,
    ) = listView(list, p, credits, missing, seen.associateBy { it.mediaType to it.filmId }, defaults, MediaType.MOVIE, filmId, today)

    @Test
    fun `counts each list the film is in with that list's own progress`() {
        val seen = listOf(Viewing(MediaType.MOVIE, space.tmdbId, null), Viewing(MediaType.MOVIE, lolita.tmdbId, null))
        val views = listOf(
            view(directing, kubrick, listOf(crew(lolita), crew(space), crew(shining)), seen = seen),
            view(writing, kubrick, listOf(crew(lolita, "Screenplay"), crew(space, "Screenplay")), seen = seen),
        )
        val entries = towardEntries(views, openedFrom = 1)
        assertEquals(
            listOf(
                TowardEntry(1, "Stanley Kubrick", "Director", seen = 2, total = 3),
                TowardEntry(2, "Stanley Kubrick", "Writer", seen = 2, total = 2),
            ),
            entries,
        )
    }

    @Test
    fun `the list it was opened from comes first`() {
        val views = listOf(
            view(directing, kubrick, listOf(crew(space))),
            view(acting, keir, listOf(cast(space, "Dr. Dave Bowman"))),
        )
        assertEquals(listOf(3L, 1L), towardEntries(views, openedFrom = 3).map { it.trackedPersonId })
    }

    @Test
    fun `a list that hides the film doesn't count it`() {
        val views = listOf(
            view(directing, kubrick, listOf(crew(tvFilm), crew(space)), filmId = tvFilm.tmdbId),
            view(
                writing, kubrick, listOf(crew(tvFilm, "Writer")), filmId = tvFilm.tmdbId,
                defaults = FilmographyFilters.DEFAULTS.copy(showTv = true),
            ),
        )
        // Defaults hide TV for the directing list; the second list was built with TV shown.
        assertEquals(listOf(2L), towardEntries(views, openedFrom = 1).map { it.trackedPersonId })
    }

    @Test
    fun `no count while a list's film details are still loading`() {
        val views = listOf(view(directing, kubrick, listOf(crew(space)), missing = 4))
        val entry = towardEntries(views, openedFrom = 1).single()
        assertEquals(null, entry.seen)
        assertEquals(null, entry.total)
    }

    @Test
    fun `credit line follows the role`() {
        assertEquals(CreditLine.Directed("Stanley Kubrick"), creditLine("Directing", "Stanley Kubrick", listOf(crew(space))))
        assertEquals(CreditLine.Written("Stanley Kubrick"), creditLine("Writing", "Stanley Kubrick", listOf(crew(space, "Screenplay"))))
        assertEquals(CreditLine.Music("Wendy Carlos"), creditLine("Sound", "Wendy Carlos", emptyList()))
        assertEquals(
            CreditLine.Acted("Michael Caine", "Harry Palmer"),
            creditLine("Acting", "Michael Caine", listOf(cast(space, "Harry Palmer (uncredited)"))),
        )
        assertEquals(CreditLine.Acted("Michael Caine", null), creditLine("Acting", "Michael Caine", listOf(cast(space, ""))))
        assertEquals(
            CreditLine.Other("Ken Adam", "Production Design"),
            creditLine("Art", "Ken Adam", listOf(crew(space, "Production Design"))),
        )
    }

    @Test
    fun `seen state comes from the viewing, and unreleased films can't be seen`() {
        val date = LocalDate.of(2018, 4, 2)
        fun seen(f: Film, v: Viewing?) = filmDetail(f, v, today, ImageConfig.DEFAULT, 305).seen
        assertEquals(SeenState.NotSeen, seen(space, null))
        assertEquals(SeenState.Seen(date), seen(space, Viewing(MediaType.MOVIE, space.tmdbId, date)))
        assertEquals(SeenState.Seen(null), seen(space, Viewing(MediaType.MOVIE, space.tmdbId, null)))
        val due = LocalDate.of(2026, 11, 26)
        val upcoming = film(1, "Narnia", due, null, "Post Production")
        assertEquals(SeenState.Unreleased(due), seen(upcoming, Viewing(MediaType.MOVIE, 1, null)))
    }

    @Test
    fun `year and runtime, either may be missing`() {
        val d = filmDetail(space, null, today, ImageConfig.DEFAULT, 305)
        assertEquals(1968 to 149, d.year to d.runtime)
        val noRuntime = filmDetail(film(5, "X", LocalDate.of(2000, 1, 1), runtime = 0), null, today, ImageConfig.DEFAULT, 305)
        assertEquals(2000 to null, noRuntime.year to noRuntime.runtime)
    }

    // ---- Where to watch ----

    private val tmdbLink = "https://www.themoviedb.org/movie/62/watch?locale=GB"

    @Test fun `where to watch builds w92 logo urls and keeps the order`() {
        val a = WatchAvailability(
            link = tmdbLink,
            stream = listOf(WatchProvider(11, "MUBI", "/mubi.jpg")),
            buy = listOf(WatchProvider(2, "Apple TV", "/apple.jpg"), WatchProvider(130, "Sky Store", null)),
        )

        val loaded = whereToWatchLoaded(a, ImageConfig.DEFAULT)

        assertEquals(listOf(WatchProviderChip("MUBI", "https://image.tmdb.org/t/p/w92/mubi.jpg")), loaded.stream)
        assertEquals(listOf("Apple TV", "Sky Store"), loaded.buy.map { it.name })
        assertNull(loaded.buy[1].logoUrl)
        assertTrue(loaded.rent.isEmpty())
        assertEquals(tmdbLink, loaded.link)
    }

    @Test fun `no TMDB link when there's nowhere to watch, or it isn't https`() {
        val empty = whereToWatchLoaded(WatchAvailability(link = tmdbLink), ImageConfig.DEFAULT)
        assertTrue(empty.isEmpty)
        assertNull(empty.link)

        val http = WatchAvailability(link = "http://example.com", rent = listOf(WatchProvider(2, "Apple TV")))
        assertNull(whereToWatchLoaded(http, ImageConfig.DEFAULT).link)
    }

    @Test fun `a country is covered if TMDB lists it, and unknown without a list`() {
        val list = TmdbResult.Success(listOf(WatchRegion("GB", "United Kingdom", 0), WatchRegion("FR", "France", 0)))
        assertEquals(true, regionCovered("GB", list))
        assertEquals(false, regionCovered("AQ", list))
        assertNull(regionCovered("GB", TmdbResult.Failure(TmdbError.NoNetwork)))
        assertNull(regionCovered("GB", TmdbResult.Success(emptyList())))
    }
}
