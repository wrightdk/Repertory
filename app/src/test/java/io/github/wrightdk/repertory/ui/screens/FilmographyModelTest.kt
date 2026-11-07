package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.local.dao.CreditInfo
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.local.entity.Viewing
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmographyModelTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun film(
        id: Int,
        title: String,
        date: LocalDate?,
        runtime: Int? = 100,
        status: String? = "Released",
        type: MediaType = MediaType.MOVIE,
        documentary: Boolean = false,
    ) = Film(
        mediaType = type,
        tmdbId = id,
        title = title,
        releaseDate = date,
        posterPath = null,
        genreIds = emptyList(),
        runtime = runtime,
        isShort = runtime != null && runtime <= 40,
        isDocumentary = documentary,
        isTvMovie = false,
        status = status,
        cachedAt = 0,
    )

    private fun crew(f: Film, job: String = "Director") = FilmCredit(f, CreditInfo(job, null, null, false))

    private fun cast(f: Film, character: String?, order: Int = 0, self: Boolean = false) =
        FilmCredit(f, CreditInfo(null, character, order, self))

    private fun content(
        credits: List<FilmCredit>,
        seen: List<Viewing> = emptyList(),
        filters: FilmographyFilters = FilmographyFilters.DEFAULTS,
        custom: Boolean = false,
    ) = filmographyContent(
        credits = credits,
        viewings = seen.associateBy { it.mediaType to it.filmId },
        filters = filters,
        custom = custom,
        today = today,
        images = ImageConfig.DEFAULT,
        posterMinWidthPx = 104,
    )

    private val ladyBird = film(1, "Lady Bird", LocalDate.of(2017, 9, 1), 94)
    private val littleWomen = film(2, "Little Women", LocalDate.of(2019, 12, 7), 135)
    private val narnia = film(3, "Narnia: The Magician's Nephew", LocalDate.of(2026, 11, 26), null, "Post Production")
    private val nights = film(4, "Nights and Weekends", LocalDate.of(2008, 3, 1), 80)

    private val FilmographyContent.rows get() = items.filterIsInstance<FilmographyItem.Film>().map { it.row }

    @Test fun `unreleased films are listed but don't count`() {
        val c = content(
            listOf(crew(nights), crew(ladyBird), crew(littleWomen), crew(narnia)),
            seen = listOf(Viewing(MediaType.MOVIE, 1, null), Viewing(MediaType.MOVIE, 2, LocalDate.of(2020, 1, 2))),
        )
        assertEquals(2, c.seen)
        assertEquals(3, c.total)
        assertEquals(67, c.percent)
        assertEquals(
            listOf(LedgerCell.UNSEEN, LedgerCell.SEEN, LedgerCell.SEEN, LedgerCell.UNRELEASED),
            c.ledger,
        )
        val n = c.rows.last()
        assertTrue(n.unreleased)
        assertEquals(LocalDate.of(2026, 11, 26), n.dueDate)
        assertFalse(n.seen)
    }

    @Test fun `a film becomes a normal row once its date passes`() {
        val due = film(5, "Due", today, null, "Post Production")
        val row = content(listOf(crew(due))).rows.single()
        assertFalse(row.unreleased)
        assertEquals(1, content(listOf(crew(due))).total)
    }

    @Test fun `a seen film that turns out unreleased is not shown as seen`() {
        val c = content(listOf(crew(narnia)), seen = listOf(Viewing(MediaType.MOVIE, 3, today)))
        assertFalse(c.rows.single().seen)
        assertEquals(0, c.seen)
        assertNull(c.percent)
    }

    @Test fun `decade headers group films, undated last`() {
        val undated = film(6, "Untitled", null, null, "Planned")
        val c = content(listOf(crew(nights), crew(ladyBird), crew(littleWomen), crew(undated)))
        val decades = c.items.filterIsInstance<FilmographyItem.Decade>().map { it.decade }
        assertEquals(listOf(2000, 2010, null), decades)
    }

    @Test fun `hidden categories leave the list and the count, and are summarised`() {
        val short = film(7, "Day of the Fight", LocalDate.of(1951, 4, 26), 16)
        val doc = film(8, "Doc", LocalDate.of(1990, 1, 1), 90, documentary = true)
        val c = content(listOf(crew(short), crew(doc), crew(nights)))
        assertEquals(listOf("Doc", "Nights and Weekends"), c.rows.map { it.title })
        assertEquals(2, c.total)
        assertEquals(FilterSummary(listOf(FilmCategory.SHORT), custom = false), c.filterSummary)
        assertEquals(listOf(FilmCategory.DOCUMENTARY), c.rows.first().categories)

        val all = FilmographyFilters(true, true, true, true, true)
        val everything = content(listOf(crew(short), crew(doc)), filters = all, custom = true)
        assertEquals(2, everything.total)
        assertEquals(FilterSummary(emptyList(), custom = true), everything.filterSummary)
    }

    @Test fun `canceled films are left out entirely`() {
        val canceled = film(9, "Napoleon", LocalDate.of(1970, 1, 1), null, "Canceled")
        val c = content(listOf(crew(canceled), crew(nights)))
        assertEquals(listOf("Nights and Weekends"), c.rows.map { it.title })
    }

    @Test fun `several credits on one film make one row`() {
        val zulu = film(10, "Zulu", LocalDate.of(1964, 1, 22))
        val c = content(listOf(crew(nights, "Director"), crew(nights, "Writer")))
        assertEquals(1, c.rows.size)
        val actor = content(listOf(cast(zulu, "Lt. Gonville Bromhead"), cast(zulu, "Narrator", order = 5)))
        assertEquals("Lt. Gonville Bromhead / Narrator", actor.rows.single().character)
    }

    @Test fun `appearances as self and uncredited parts are hidden by default`() {
        val talk = film(11, "Talk show", LocalDate.of(2000, 1, 1))
        val cameo = film(12, "Cameo", LocalDate.of(2001, 1, 1))
        val lead = film(13, "Lead", LocalDate.of(2002, 1, 1))
        val credits = listOf(
            cast(talk, "Himself", self = true),
            cast(cameo, "Man in Bar (uncredited)", order = 40),
            cast(lead, "Harry Palmer"),
        )
        val c = content(credits)
        assertEquals(listOf("Lead"), c.rows.map { it.title })
        assertEquals(listOf(FilmCategory.SELF, FilmCategory.MINOR_ROLE), c.filterSummary.hiding)

        val shown = content(credits, filters = FilmographyFilters.DEFAULTS.copy(showMinorRoles = true))
        val cameoRow = shown.rows.first { it.title == "Cameo" }
        assertEquals("Man in Bar", cameoRow.character)
        assertEquals(listOf(FilmCategory.MINOR_ROLE), cameoRow.categories)
    }

    @Test fun `header shows role and life dates`() {
        fun person(born: LocalDate?, died: LocalDate?) =
            Person(1, "Name", null, "Directing", born, died, 0)
        val images = ImageConfig.DEFAULT
        assertEquals("Director, 1928–1999", filmographyHeader(person(LocalDate.of(1928, 7, 26), LocalDate.of(1999, 3, 7)), "Directing", images, 185).subtitle)
        assertEquals("Actor, born 1933", filmographyHeader(person(LocalDate.of(1933, 3, 14), null), "Acting", images, 185).subtitle)
        assertEquals("Writer", filmographyHeader(person(null, null), "Writing", images, 185).subtitle)
    }
}
