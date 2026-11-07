package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.HomeSort
import io.github.wrightdk.repertory.data.local.dao.CreditInfo
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun film(id: Int, date: LocalDate?, runtime: Int? = 100, status: String? = "Released") = Film(
        mediaType = MediaType.MOVIE,
        tmdbId = id,
        title = "Film $id",
        releaseDate = date,
        posterPath = null,
        genreIds = emptyList(),
        runtime = runtime,
        isShort = runtime != null && runtime <= 40,
        isDocumentary = false,
        isTvMovie = false,
        status = status,
        cachedAt = 0,
        detailsCachedAt = 0,
    )

    private fun crew(f: Film) = FilmCredit(f, CreditInfo("Director", null, null, false))

    private val kubrick = Person(240, "Stanley Kubrick", null, "Directing", null, null, 0)
    private val directing = TrackedPerson(id = 1, personId = 240, department = "Directing", addedAt = 1)

    private fun row(
        id: Long,
        name: String?,
        role: String = "Director",
        progress: HomeProgress? = HomeProgress(0, 10),
        updatedAt: Long = 0,
    ) = HomeRow(id, name, null, role, progress, detailsFailed = false, updatedAt = updatedAt)

    private fun homeRowFor(
        credits: List<FilmCredit>,
        seen: List<Int> = emptyList(),
        complete: Boolean = true,
        filters: FilmographyFilters = FilmographyFilters.DEFAULTS,
    ) = homeRow(
        list = directing,
        person = kubrick,
        credits = credits,
        detailsComplete = complete,
        detailsFailed = false,
        viewings = seen.associate { (MediaType.MOVIE to it) to Viewing(MediaType.MOVIE, it, null) },
        defaults = filters,
        today = today,
        images = ImageConfig.DEFAULT,
        photoMinWidthPx = 0,
    )

    @Test
    fun `counts released films the filters show`() {
        val short = film(3, LocalDate.of(1951, 1, 1), runtime = 16)
        val upcoming = film(4, LocalDate.of(2027, 1, 1), status = "Post Production")
        val r = homeRowFor(listOf(crew(film(1, LocalDate.of(1960, 1, 1))), crew(film(2, LocalDate.of(1970, 1, 1))), crew(short), crew(upcoming)), seen = listOf(1, 3))
        assertEquals(HomeProgress(seen = 1, total = 2), r.progress)
        assertEquals("Director", r.role)
        assertEquals("Stanley Kubrick", r.name)
    }

    @Test
    fun `a list whose details are still loading has no count`() {
        val r = homeRowFor(listOf(crew(film(1, LocalDate.of(1960, 1, 1)))), complete = false)
        assertNull(r.progress)
    }

    @Test
    fun `a list restored without its person has no count yet`() {
        val r = homeRow(
            list = directing,
            person = null,
            credits = emptyList(),
            detailsComplete = true,
            detailsFailed = true,
            viewings = emptyMap(),
            defaults = FilmographyFilters.DEFAULTS,
            today = today,
            images = ImageConfig.DEFAULT,
            photoMinWidthPx = 0,
        )
        assertNull("not \"All 0 seen\"", r.progress)
        assertNull(r.name)
        assertTrue("a failed person fetch shows as failed", r.detailsFailed)
    }

    @Test
    fun `all seen only when there is something to count`() {
        assertTrue(HomeProgress(3, 3).done)
        assertFalse(HomeProgress(0, 0).done)
        assertEquals(0f, HomeProgress(0, 0).fraction)
    }

    @Test
    fun `name sort is by surname, accents ignored, then role`() {
        val rows = listOf(
            row(1, "Agnès Varda"),
            row(2, "Greta Gerwig", role = "Director"),
            row(3, "Michael Caine", role = "Actor"),
            row(4, "Greta Gerwig", role = "Actor"),
            row(5, "Stanley Kubrick"),
            row(6, "Lars von Trier"),
            row(7, "Ana Valdés"),
        )
        assertEquals(listOf(3L, 4, 2, 5, 6, 7, 1), sortRows(rows, HomeSort.NAME, Locale.UK).map { it.trackedPersonId })
    }

    @Test
    fun `progress sort puts the furthest along first and loading lists last`() {
        val rows = listOf(
            row(1, "A A", progress = HomeProgress(1, 10)),
            row(2, "B B", progress = null),
            row(3, "C C", progress = HomeProgress(9, 10)),
            row(4, "D D", progress = HomeProgress(0, 0)),
            row(5, "E E", progress = HomeProgress(5, 5)),
        )
        assertEquals(listOf(5L, 3, 1, 4, 2), sortRows(rows, HomeSort.PROGRESS, Locale.UK).map { it.trackedPersonId })
    }

    @Test
    fun `recent sort puts the last updated first`() {
        val rows = listOf(row(1, "A A", updatedAt = 10), row(2, "B B", updatedAt = 30), row(3, "C C", updatedAt = 20))
        assertEquals(listOf(2L, 3, 1), sortRows(rows, HomeSort.RECENT, Locale.UK).map { it.trackedPersonId })
    }

    @Test
    fun `search matches name or role, ignoring case and accents`() {
        val rows = listOf(row(1, "Agnès Varda"), row(2, "Michael Caine", role = "Actor"), row(3, "Roger Deakins", role = "Cinematographer"))
        assertEquals(listOf(1L), searchRows(rows, "agnes").map { it.trackedPersonId })
        assertEquals(listOf(2L), searchRows(rows, " ACT ").map { it.trackedPersonId })
        assertEquals(rows, searchRows(rows, "  "))
        assertEquals(emptyList<HomeRow>(), searchRows(rows, "kubrick"))
    }

    @Test
    fun `recency reads like the mockup`() {
        fun daysAgo(n: Long) = recency(today.minusDays(n).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), today, ZoneOffset.UTC)
        assertEquals(Recency.Today, daysAgo(0))
        assertEquals(Recency.Yesterday, daysAgo(1))
        assertEquals(Recency.Days(2), daysAgo(2))
        assertEquals(Recency.Weeks(1), daysAgo(7))
        assertEquals(Recency.Weeks(3), daysAgo(20))
        assertEquals(Recency.Months(1), daysAgo(30))
        assertEquals(Recency.Months(12), daysAgo(364))
        assertEquals(Recency.Years(2), daysAgo(800))
        // A clock change can put the update a moment in the future.
        assertEquals(Recency.Today, daysAgo(-1))
    }
}
