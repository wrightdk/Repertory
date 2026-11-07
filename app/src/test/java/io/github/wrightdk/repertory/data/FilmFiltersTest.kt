package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.local.dao.CreditInfo
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agreed filter rules (CLAUDE.md, "Filter rules"). */
class FilmFiltersTest {
    private fun film(
        type: MediaType = MediaType.MOVIE,
        runtime: Int? = 100,
        documentary: Boolean = false,
        tvMovie: Boolean = false,
    ) = Film(
        mediaType = type,
        tmdbId = 1,
        title = "Film",
        releaseDate = LocalDate.of(2000, 1, 1),
        posterPath = null,
        genreIds = emptyList(),
        runtime = runtime,
        isShort = runtime != null && runtime <= 40,
        isDocumentary = documentary,
        isTvMovie = tvMovie,
        status = "Released",
        cachedAt = 0,
    )

    private fun cast(character: String?, order: Int? = 0, self: Boolean = false) = CreditInfo(null, character, order, self)
    private val crew = CreditInfo("Director", null, null, false)
    private val name = "Matt Damon"

    private fun minor(character: String?, order: Int? = 0, self: Boolean = false) =
        isMinorCredit(cast(character, order, self), name)

    @Test fun `shorts are 40 minutes or less, unknown runtime is not a short`() {
        assertTrue(FilmCategory.SHORT in filmCategories(film(runtime = 40), listOf(crew), name))
        assertFalse(FilmCategory.SHORT in filmCategories(film(runtime = 41), listOf(crew), name))
        assertFalse(FilmCategory.SHORT in filmCategories(film(runtime = null), listOf(crew), name))
    }

    @Test fun `TV is series or the TV Movie genre`() {
        assertTrue(FilmCategory.TV in filmCategories(film(type = MediaType.TV), listOf(crew), name))
        assertTrue(FilmCategory.TV in filmCategories(film(tvMovie = true), listOf(crew), name))
        assertFalse(FilmCategory.TV in filmCategories(film(), listOf(crew), name))
    }

    @Test fun `self is TMDB's self characters or their own name`() {
        assertTrue(isSelfCredit(cast("Self", self = true), name))
        assertTrue(isSelfCredit(cast("Matt Damon"), name))
        assertTrue(isSelfCredit(cast("matt damon (uncredited)"), name))
        assertFalse(isSelfCredit(cast("Matt Damon / Will Hunting"), name))
        assertFalse(isSelfCredit(crew, name))
    }

    @Test fun `minor roles are uncredited, archive footage, or billed 21st or lower`() {
        assertTrue(minor("Baseball Fan (uncredited)", order = 3))
        assertTrue(minor("Voice on Radio (Uncredited)", order = 0))
        assertTrue(minor("Alfred (archive footage)", order = 2))
        assertTrue(minor("(archive footage)", order = 50))
        assertTrue(minor("Redneck #2", order = 20))
        assertFalse(minor("Steven Sanderson", order = 19))
        assertFalse(minor("Bill (voice)", order = 10))
        assertFalse(minor("", order = null))
        assertFalse(minor(null, order = null))
        // Self is never minor.
        assertFalse(minor("Self (archive footage)", order = 49, self = true))
        assertFalse(isMinorCredit(crew, name))
    }

    @Test fun `a film is self or minor only when every credit on it is`() {
        val f = film()
        assertEquals(setOf(FilmCategory.MINOR_ROLE), filmCategories(f, listOf(cast("Extra (uncredited)"), cast("Cop", order = 30)), name))
        assertEquals(emptySet<FilmCategory>(), filmCategories(f, listOf(cast("Extra (uncredited)"), cast("Lead", order = 0)), name))
        assertEquals(emptySet<FilmCategory>(), filmCategories(f, listOf(cast("Self", self = true), cast("Lead")), name))
        assertEquals(emptySet<FilmCategory>(), filmCategories(f, listOf(crew), name))
    }

    @Test fun `a film is hidden if any of its categories is off`() {
        val d = FilmographyFilters.DEFAULTS
        assertTrue(d.showsAll(setOf(FilmCategory.DOCUMENTARY)))
        assertFalse(d.showsAll(setOf(FilmCategory.DOCUMENTARY, FilmCategory.TV)))
        assertTrue(d.showsAll(emptySet()))
    }

    @Test fun `an override matching the defaults is dropped`() {
        val d = FilmographyFilters.DEFAULTS
        val custom = d.with(FilmCategory.SHORT, true)
        assertEquals(custom, overrideFor(custom, d))
        assertNull(overrideFor(custom.with(FilmCategory.SHORT, false), d))
    }

    @Test fun `display character drops the uncredited marker`() {
        assertEquals("British Soldier (segment \"Fish\")", displayCharacter("British Soldier (uncredited) (segment \"Fish\")"))
        assertNull(displayCharacter(" (uncredited)"))
    }
}
