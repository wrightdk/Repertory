package io.github.wrightdk.repertory.ui.screens

import io.github.wrightdk.repertory.data.local.entity.WatchRegion
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCountryModelTest {
    private val regions = listOf("US" to "United States of America", "GB" to "United Kingdom", "CI" to "Cote D'Ivoire", "DE" to "Germany", "AT" to "Austria")
        .map { (code, name) -> WatchRegion(code, name, fetchedAt = 0) }

    private fun names(query: String = "", locale: Locale = Locale.UK) = countryOptions(regions, query, locale).map { it.name }

    @Test fun `countries are listed by name in the user's language, in its alphabetical order`() {
        assertEquals(listOf("Austria", "Côte d’Ivoire", "Germany", "United Kingdom", "United States"), names())
        assertEquals(listOf("Allemagne", "Autriche", "Côte d’Ivoire", "États-Unis", "Royaume-Uni"), names(locale = Locale.FRANCE))
    }

    @Test fun `search ignores case and accents`() {
        assertEquals(listOf("Côte d’Ivoire"), names("COTE"))
        assertEquals(listOf("États-Unis"), names("etats", Locale.FRANCE))
    }

    @Test fun `search also matches TMDB's English name and the code`() {
        // In French the UK is "Royaume-Uni", but "kingdom" still finds it.
        assertEquals(listOf("Royaume-Uni"), names("kingdom", Locale.FRANCE))
        assertEquals(listOf("Germany"), names("de"))
    }

    @Test fun `no match is an empty list`() {
        assertTrue(names("narnia").isEmpty())
    }
}
