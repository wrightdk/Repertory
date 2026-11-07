package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.TrackedPerson
import io.github.wrightdk.repertory.data.remote.dto.CombinedCreditsDto
import io.github.wrightdk.repertory.data.remote.dto.MovieDetailsDto
import io.github.wrightdk.repertory.data.remote.dto.PersonDetailsDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMappersTest {
    private val credits = TmdbClient.json.decodeFromString<CombinedCreditsDto>(fixture("combined_credits.json"))
    private val mapped = credits.toEntities(personId = 6817, now = 1_000L)

    private fun movie(id: Int) = mapped.films.single { it.mediaType == MediaType.MOVIE && it.tmdbId == id }

    @Test fun `person keeps life dates and known-for department`() {
        val person = TmdbClient.json.decodeFromString<PersonDetailsDto>(fixture("person.json")).toEntity(7L)
        assertEquals("Agnès Varda", person.name)
        assertEquals(LocalDate.of(1928, 5, 30), person.birthday)
        assertEquals(LocalDate.of(2019, 3, 29), person.deathday)
        assertEquals("Directing", person.knownForDepartment)
        assertEquals(7L, person.cachedAt)
    }

    @Test fun `living person has no deathday, and blank dates are null`() {
        val person = PersonDetailsDto(id = 1, name = "Greta Gerwig", birthday = "1983-08-04", deathday = "").toEntity(0)
        assertEquals(LocalDate.of(1983, 8, 4), person.birthday)
        assertNull(person.deathday)
    }

    @Test fun `tv series are kept as TV with name and first air date`() {
        // Fixture has "Varda by Agnès" (tv 87772) in both cast and crew.
        val series = mapped.films.single { it.tmdbId == 87772 }
        assertEquals(MediaType.TV, series.mediaType)
        assertEquals("Varda by Agnès", series.title)
        assertEquals(LocalDate.of(2019, 3, 18), series.releaseDate)
        assertTrue(series.isTv)
        assertFalse(series.isTvMovie)
        val credits = mapped.credits.filter { it.filmId == 87772 }
        assertEquals(setOf(MediaType.TV), credits.map { it.mediaType }.toSet())
        assertEquals(setOf("Acting", "Directing"), credits.map { it.department }.toSet())
    }

    @Test fun `movie and tv with the same id stay separate`() {
        val clash = TmdbClient.json.decodeFromString<CombinedCreditsDto>(
            """{"cast":[
                {"id":5,"media_type":"movie","title":"A film","character":"A"},
                {"id":5,"media_type":"tv","name":"A series","character":"B"},
                {"id":6,"character":"No media type"}
            ]}""",
        ).toEntities(personId = 1, now = 0)
        assertEquals(
            setOf(MediaType.MOVIE to "A film", MediaType.TV to "A series"),
            clash.films.map { it.mediaType to it.title }.toSet(),
        )
        assertEquals(2, clash.credits.size)
    }

    @Test fun `credits keep media type`() {
        assertTrue(mapped.credits.filter { it.filmId == 499 }.all { it.mediaType == MediaType.MOVIE })
    }

    @Test fun `one film row per film, one credit per job`() {
        assertEquals(mapped.films.size, mapped.films.map { it.mediaType to it.tmdbId }.toSet().size)
        val cleo = mapped.credits.filter { it.filmId == 499 }
        assertEquals(setOf("Director", "Writer", "Songs"), cleo.map { it.job }.toSet())
        assertEquals(setOf("Directing", "Writing", "Sound"), cleo.map { it.department }.toSet())
    }

    @Test fun `cast credits are Acting with character and order`() {
        val charlie = mapped.credits.single { it.filmId == 13440 }
        assertEquals(ACTING_DEPARTMENT, charlie.department)
        assertEquals("The Widow Hyppolite", charlie.character)
        assertEquals(11, charlie.order)
        assertNull(charlie.job)
        assertFalse(charlie.isSelf)
    }

    @Test fun `crew credits keep department and job, no character or order`() {
        val vagabondEditor = mapped.credits.single { it.filmId == 44018 && it.department == "Editing" }
        assertEquals("Editor", vagabondEditor.job)
        assertNull(vagabondEditor.character)
        assertNull(vagabondEditor.order)
        assertFalse(vagabondEditor.isSelf)
    }

    @Test fun `appearances as self are flagged`() {
        assertTrue("Cinéma! Cinéma! is Self", mapped.credits.single { it.filmId == 929052 }.isSelf)
        assertFalse(mapped.credits.single { it.filmId == 2433 }.isSelf)
        listOf("Self", "self", "Himself", "Herself (archive footage)", "Self - Host", "Themselves").forEach {
            assertTrue(it, isSelfCharacter(it))
        }
        listOf(null, "", "Selfish Man", "The Widow Hyppolite", "Harry Palmer (as himself)").forEach {
            assertFalse(it.toString(), isSelfCharacter(it))
        }
    }

    @Test fun `uncredited parts keep the raw character and order for the minor-role rule`() {
        val nun = mapped.credits.single { it.filmId == 2433 }
        assertEquals("Music-Loving Nun (uncredited)", nun.character)
        assertEquals(68, nun.order)
    }

    @Test fun `film flags come from genre ids, which are kept`() {
        assertTrue("Faces Places is a documentary", movie(451995).isDocumentary)
        assertTrue("Nausicaa is a TV movie", movie(324072).isTvMovie)
        assertTrue(movie(324072).isTv)
        assertEquals(listOf(10770, 18), movie(324072).genreIds)
        assertEquals(listOf(99, 10770), movie(929052).genreIds)
        assertFalse(movie(499).isDocumentary)
        assertFalse(movie(499).isTv)
        assertEquals(LocalDate.of(1962, 4, 11), movie(499).releaseDate)
    }

    @Test fun `films from credits have no details yet`() {
        mapped.films.forEach {
            assertNull(it.runtime)
            assertNull(it.status)
            assertNull(it.detailsCachedAt)
            assertFalse(it.isShort)
        }
    }

    @Test fun `blank or junk release dates become null`() {
        assertNull(parseTmdbDate(""))
        assertNull(parseTmdbDate(null))
        assertNull(parseTmdbDate("2026"))
        assertEquals(LocalDate.of(2026, 1, 2), parseTmdbDate("2026-01-02"))
    }

    @Test fun `movie details set runtime and short flag`() {
        val short = TmdbClient.json.decodeFromString<MovieDetailsDto>(fixture("movie_short.json")).toEntity(5L)
        assertEquals(MediaType.MOVIE, short.mediaType)
        assertEquals(18, short.runtime)
        assertEquals("Released", short.status)
        assertEquals(LocalDate.of(1967, 10, 1), short.releaseDate)
        assertEquals(listOf(99), short.genreIds)
        assertTrue(short.isShort)
        assertTrue(short.isDocumentary)
        assertEquals(5L, short.detailsCachedAt)

        val feature = TmdbClient.json.decodeFromString<MovieDetailsDto>(fixture("movie_feature.json")).toEntity(5L)
        assertEquals(90, feature.runtime)
        assertFalse(feature.isShort)
    }

    @Test fun `short threshold is 40 minutes inclusive, and 0 means unknown`() {
        fun film(runtime: Int) = MovieDetailsDto(id = 1, runtime = runtime).toEntity(0)
        assertTrue(film(40).isShort)
        assertFalse(film(41).isShort)
        assertNull(film(0).runtime)
        assertFalse(film(0).isShort)
    }

    @Test fun `merging keeps cached details`() {
        val fromCredits = movie(277677)
        val cached = TmdbClient.json.decodeFromString<MovieDetailsDto>(fixture("movie_short.json")).toEntity(5L)

        val merged = fromCredits.mergedWith(cached)

        assertEquals(18, merged.runtime)
        assertEquals("Released", merged.status)
        assertTrue(merged.isShort)
        assertEquals(5L, merged.detailsCachedAt)
        assertEquals(fromCredits.cachedAt, merged.cachedAt)
    }

    @Test fun `released depends on status once known, else on date`() {
        val today = LocalDate.of(2026, 11, 7)
        fun film(date: LocalDate?, status: String?) = MovieDetailsDto(id = 1).toEntity(0).copy(releaseDate = date, status = status)
        val past = LocalDate.of(2023, 7, 21)
        val due = LocalDate.of(2026, 11, 26)

        assertTrue(film(past, "Released").isReleased(today))
        assertFalse(film(due, "Post Production").isReleased(today))
        assertFalse("canceled never counts", film(past, "Canceled").isReleased(today))
        assertTrue("no details yet, dated in the past", film(past, null).isReleased(today))
        assertTrue("released today", film(today, null).isReleased(today))
        assertFalse("no details yet, due later", film(due, null).isReleased(today))
        assertFalse("undated", film(null, null).isReleased(today))
    }

    @Test fun `lists follow the defaults until overridden`() {
        val defaults = FilmographyFilters.DEFAULTS
        assertEquals(
            "everything hidden except documentaries",
            FilmographyFilters(showShorts = false, showTv = false, showDocumentaries = true, showSelf = false, showMinorRoles = false),
            defaults,
        )
        val list = TrackedPerson(personId = 1, department = "Directing", addedAt = 0)
        assertNull(list.filterOverride)
        assertEquals(defaults, list.filters(defaults))
        val custom = defaults.copy(showShorts = true)
        assertEquals(custom, list.copy(filterOverride = custom).filters(defaults))
    }

    @Test fun `image size picks the smallest wide-enough width`() {
        val sizes = ImageConfig.DEFAULT.posterSizes
        assertEquals("w342", ImageConfig.pickSize(sizes, 300))
        assertEquals("w92", ImageConfig.pickSize(sizes, 1))
        assertEquals("original", ImageConfig.pickSize(sizes, 2000))
        assertEquals(
            "https://image.tmdb.org/t/p/w185/x.jpg",
            ImageConfig.DEFAULT.posterUrl("/x.jpg", 160),
        )
        assertNull(ImageConfig.DEFAULT.posterUrl(null, 160))
    }
}
