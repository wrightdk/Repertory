package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.remote.TmdbClient
import io.github.wrightdk.repertory.data.remote.dto.WatchProvidersDto
import io.github.wrightdk.repertory.data.remote.fixture
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking the UK out of `/movie/{id}/watch/providers`, with the app's lenient JSON settings. */
class WatchProvidersTest {
    private fun parse(json: String): WatchAvailability =
        TmdbClient.json.decodeFromString(WatchProvidersDto.serializer(), json).availability("GB")

    private fun List<WatchProvider>.names() = map { it.name }

    @Test fun `parses a full GB entry and ignores other regions and categories`() {
        val a = parse(fixture("watch_providers_full.json"))

        assertEquals("https://www.themoviedb.org/movie/62-2001-a-space-odyssey/watch?locale=GB", a.link)
        assertEquals(listOf(WatchProvider(11, "MUBI", "/mubi.jpg")), a.stream)
        assertEquals(listOf("Apple TV", "Amazon Video", "Google Play Movies"), a.rent.names())
        assertEquals(listOf("Apple TV", "Amazon Video", "Google Play Movies", "Sky Store"), a.buy.names())
        // "ads", "free" and the US entry are left out.
        val all = (a.stream + a.rent + a.buy).names()
        assertFalse("My5" in all || "BBC iPlayer" in all || "Max" in all)
        assertFalse(a.isEmpty)
    }

    @Test fun `a response with only one category leaves the others empty`() {
        val a = parse(fixture("watch_providers_rent_only.json"))

        assertEquals(listOf("Apple TV"), a.rent.names())
        assertTrue(a.stream.isEmpty())
        assertTrue(a.buy.isEmpty())
        assertFalse(a.isEmpty)
    }

    @Test fun `no GB entry means nowhere to watch`() {
        val a = parse(fixture("watch_providers_no_gb.json"))

        assertEquals(WatchAvailability.NONE, a)
        assertTrue(a.isEmpty)
    }

    @Test fun `empty results mean nowhere to watch`() {
        assertEquals(WatchAvailability.NONE, parse("""{"id": 1, "results": {}}"""))
        assertEquals(WatchAvailability.NONE, parse("""{"id": 1}"""))
    }

    @Test fun `each list is sorted by display priority, missing priority last`() {
        val a = parse(
            """
            {"results": {"GB": {"buy": [
              {"provider_id": 4, "provider_name": "No priority"},
              {"provider_id": 3, "provider_name": "Third", "display_priority": 30},
              {"provider_id": 1, "provider_name": "First", "display_priority": 1},
              {"provider_id": 2, "provider_name": "Second", "display_priority": 2}
            ]}}}
            """,
        )

        assertEquals(listOf("First", "Second", "Third", "No priority"), a.buy.names())
    }

    @Test fun `tolerates nulls, missing fields, blank names and duplicates`() {
        val a = parse(
            """
            {"results": {"GB": {
              "link": null,
              "flatrate": null,
              "rent": [
                {"provider_id": 2, "provider_name": "Apple TV", "logo_path": null, "display_priority": null, "extra": true},
                {"provider_id": 2, "provider_name": "Apple TV", "display_priority": 9},
                {"provider_id": 7, "provider_name": "  "},
                {"provider_name": "No id"}
              ]
            }}}
            """,
        )

        assertEquals(null, a.link)
        assertTrue(a.stream.isEmpty())
        assertEquals(listOf(WatchProvider(2, "Apple TV", null), WatchProvider(0, "No id", null)), a.rent)
    }

    @Test fun `any country can be picked out of the same response`() {
        val us = TmdbClient.json.decodeFromString(WatchProvidersDto.serializer(), fixture("watch_providers_full.json")).availability("US")

        assertEquals(listOf("Max"), us.stream.names())
        assertTrue(us.rent.isEmpty() && us.buy.isEmpty())
    }

    // ---- Countries ----

    @Test fun `the phone's region comes from its language settings, if they name one`() {
        assertEquals("GB", phoneWatchRegion(Locale.UK))
        assertEquals("FR", phoneWatchRegion(Locale.forLanguageTag("en-FR")))
        assertNull(phoneWatchRegion(Locale.ENGLISH))
        // A UN M.49 area code isn't a country.
        assertNull(phoneWatchRegion(Locale.forLanguageTag("es-419")))
    }

    @Test fun `country codes are two capital letters`() {
        assertTrue(isRegionCode("GB"))
        assertFalse(isRegionCode("gb"))
        assertFalse(isRegionCode("GBR"))
        assertFalse(isRegionCode(""))
        assertFalse(isRegionCode(null))
    }

    @Test fun `country names are in the user's language, with a fallback`() {
        assertEquals("United Kingdom", regionDisplayName("GB", Locale.UK))
        assertEquals("Royaume-Uni", regionDisplayName("GB", Locale.FRANCE))
        assertEquals("Somewhere", regionDisplayName("??", Locale.UK, fallback = "Somewhere"))
    }
}
