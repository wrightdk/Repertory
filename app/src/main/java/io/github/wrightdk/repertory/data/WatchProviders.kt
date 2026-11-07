package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.remote.dto.WatchProviderDto
import io.github.wrightdk.repertory.data.remote.dto.WatchProvidersDto
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * The country "Where to watch" starts on before the user picks one in
 * Settings: the region in the phone's language settings ("GB" for en-GB).
 * Never location. Null when the locale has no region, e.g. plain "en".
 */
fun phoneWatchRegion(locale: Locale = Locale.getDefault()): String? = locale.country.takeIf(::isRegionCode)

/** An ISO 3166-1 alpha-2 code as TMDB writes them: two capital letters. */
fun isRegionCode(code: String?): Boolean = code != null && code.length == 2 && code.all { it in 'A'..'Z' }

/** The country in the user's own language ("Royaume-Uni" in French), or [fallback] if the phone doesn't know it. */
fun regionDisplayName(code: String, locale: Locale = Locale.getDefault(), fallback: String = code): String {
    if (!isRegionCode(code)) return fallback
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
    return if (name.isBlank() || name == code) fallback else name
}

/** A streaming service or shop, as JustWatch names it ("MUBI", "Apple TV"). */
@Serializable
data class WatchProvider(
    val id: Int,
    val name: String,
    /** TMDB image path for the provider's logo; null when TMDB has none. */
    val logoPath: String? = null,
)

/**
 * Where a film can be watched in one region, each list in JustWatch's display
 * order. Stored as-is in the watch providers cache, so keep it serializable.
 */
@Serializable
data class WatchAvailability(
    /** TMDB's watch page for the film in this region ("See all options on TMDB"). */
    val link: String? = null,
    /** Subscription streaming (TMDB's "flatrate"). */
    val stream: List<WatchProvider> = emptyList(),
    val rent: List<WatchProvider> = emptyList(),
    val buy: List<WatchProvider> = emptyList(),
) {
    /** Nothing to stream, rent or buy: the empty-state card. */
    val isEmpty: Boolean get() = stream.isEmpty() && rent.isEmpty() && buy.isEmpty()

    companion object {
        /** No entry for the region at all. */
        val NONE = WatchAvailability()
    }
}

/**
 * Picks [region] ("GB") out of a `/watch/providers` response, which holds every
 * country; only the film's id is sent to TMDB, never the region. A missing region, or
 * missing lists, mean nothing there. Providers without a name are dropped, and
 * one listed twice in a category is kept once.
 */
fun WatchProvidersDto.availability(region: String): WatchAvailability {
    val entry = results[region] ?: return WatchAvailability.NONE
    return WatchAvailability(
        link = entry.link?.takeIf { it.isNotBlank() },
        stream = entry.flatrate.toProviders(),
        rent = entry.rent.toProviders(),
        buy = entry.buy.toProviders(),
    )
}

private fun List<WatchProviderDto>.toProviders(): List<WatchProvider> =
    filter { it.providerName.isNotBlank() }
        // Stable for equal priorities, so JustWatch's own order breaks ties.
        .sortedBy { it.displayPriority }
        .distinctBy { it.providerId.takeIf { id -> id != 0 } ?: it.providerName }
        .map { WatchProvider(it.providerId, it.providerName.trim(), it.logoPath?.takeIf(String::isNotBlank)) }
