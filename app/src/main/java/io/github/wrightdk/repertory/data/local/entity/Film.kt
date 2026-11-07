package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

/**
 * Cached TMDB title a person is credited on: usually a movie, sometimes a TV
 * series (kept so the TV filter can show it; hidden by default). Cache data:
 * safe to refresh or evict.
 *
 * Rows first arrive from a person's credits (title, date, poster, genres) and
 * movies are filled in later from `/movie/{id}` (runtime, so [isShort], and
 * [status]). TV series never get details.
 */
@Entity(tableName = "film", primaryKeys = ["mediaType", "tmdbId"])
data class Film(
    val mediaType: MediaType,
    val tmdbId: Int,
    /** Movie title, or series name. */
    val title: String,
    /** Movie release date, or series first air date. Null when TMDB has none (usually unreleased). */
    val releaseDate: LocalDate?,
    val posterPath: String?,
    /** TMDB genre ids, as sent. [isDocumentary] and [isTvMovie] are derived from these. */
    val genreIds: List<Int>,
    /** Minutes. Null until the full /movie/{id} details have been fetched, or if TMDB has none. */
    val runtime: Int?,
    /** Runtime of 40 minutes or less (the Academy's definition). False until details are fetched. */
    val isShort: Boolean,
    val isDocumentary: Boolean,
    /** TMDB "TV Movie" genre. */
    val isTvMovie: Boolean,
    /**
     * TMDB movie status from `/movie/{id}`: "Released", "Post Production",
     * "In Production", "Planned", "Rumored", "Canceled". Null until details are
     * fetched, and always for TV.
     */
    val status: String?,
    /** Epoch millis this row was last written from any TMDB response. */
    val cachedAt: Long,
    /** Epoch millis of the last `/movie/{id}` fetch. Null if details have never been fetched. */
    val detailsCachedAt: Long? = null,
) {
    /** What the "TV" filter hides: series, TV films and specials. */
    val isTv: Boolean get() = mediaType == MediaType.TV || isTvMovie

    /**
     * Whether the film is out, so it can be ticked and counts towards progress.
     * Trusts the movie status once details are fetched; otherwise goes by date,
     * and an undated title counts as unreleased.
     */
    fun isReleased(today: LocalDate): Boolean = when {
        status == STATUS_RELEASED -> true
        status != null -> false
        else -> releaseDate != null && !releaseDate.isAfter(today)
    }

    companion object {
        const val STATUS_RELEASED = "Released"
    }
}
