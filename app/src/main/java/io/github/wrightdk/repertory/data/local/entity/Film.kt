package io.github.wrightdk.repertory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Cached TMDB film. Cache data: safe to refresh or evict.
 *
 * Rows first arrive from a person's credits (title, date, poster, genres) and are
 * filled in later from `/movie/{id}` (runtime, and so [isShort]).
 */
@Entity(tableName = "film")
data class Film(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    /** Null when TMDB has no date (usually unreleased / in development). */
    val releaseDate: LocalDate?,
    val posterPath: String?,
    /** Minutes. Null until the full /movie/{id} details have been fetched, or if TMDB has none. */
    val runtime: Int?,
    /** Runtime of 40 minutes or less (the Academy's definition). False until details are fetched. */
    val isShort: Boolean,
    val isDocumentary: Boolean,
    /** TMDB "TV Movie" genre. What the "TV" filmography filter hides. */
    @ColumnInfo(defaultValue = "0") val isTvMovie: Boolean = false,
    /** Epoch millis this row was last written from any TMDB response. */
    val cachedAt: Long,
    /** Epoch millis of the last `/movie/{id}` fetch. Null if details have never been fetched. */
    val detailsCachedAt: Long? = null,
)
