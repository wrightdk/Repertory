package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.remote.dto.CombinedCreditsDto
import io.github.wrightdk.repertory.data.remote.dto.MovieDetailsDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMappersTest {
    private val credits = TmdbClient.json.decodeFromString<CombinedCreditsDto>(fixture("combined_credits.json"))
    private val mapped = credits.toEntities(personId = 6817, now = 1_000L)

    @Test fun `tv series credits are dropped`() {
        // Fixture has "Varda by Agnès" (tv 87772) in both cast and crew.
        assertFalse(mapped.films.any { it.tmdbId == 87772 })
        assertFalse(mapped.credits.any { it.filmId == 87772 })
    }

    @Test fun `one film row per film, one credit per job`() {
        assertEquals(mapped.films.size, mapped.films.map { it.tmdbId }.toSet().size)
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
    }

    @Test fun `film flags come from genre ids`() {
        val byId = mapped.films.associateBy { it.tmdbId }
        assertTrue("Faces Places is a documentary", byId.getValue(451995).isDocumentary)
        assertTrue("Nausicaa is a TV movie", byId.getValue(324072).isTvMovie)
        assertFalse(byId.getValue(499).isDocumentary)
        assertFalse(byId.getValue(499).isTvMovie)
        assertEquals(LocalDate.of(1962, 4, 11), byId.getValue(499).releaseDate)
    }

    @Test fun `films from credits have no details yet`() {
        mapped.films.forEach {
            assertNull(it.runtime)
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
        assertEquals(18, short.runtime)
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
        val fromCredits = mapped.films.first { it.tmdbId == 277677 }
        val cached = TmdbClient.json.decodeFromString<MovieDetailsDto>(fixture("movie_short.json")).toEntity(5L)

        val merged = fromCredits.mergedWith(cached)

        assertEquals(18, merged.runtime)
        assertTrue(merged.isShort)
        assertEquals(5L, merged.detailsCachedAt)
        assertEquals(fromCredits.cachedAt, merged.cachedAt)
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
