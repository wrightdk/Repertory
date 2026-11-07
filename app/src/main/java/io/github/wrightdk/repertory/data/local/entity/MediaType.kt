package io.github.wrightdk.repertory.data.local.entity

/**
 * What kind of TMDB title a [Film] row is. TMDB numbers movies and TV series
 * separately, so the same id can mean two different titles: always key by
 * (mediaType, tmdbId) together. Stored by enum name.
 */
enum class MediaType(val tmdbName: String) {
    MOVIE("movie"),
    TV("tv");

    companion object {
        /** Null for anything else TMDB sends (e.g. a missing media_type), which callers skip. */
        fun fromTmdb(value: String?): MediaType? = entries.firstOrNull { it.tmdbName == value }
    }
}
