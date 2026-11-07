package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Cached TMDB film. Cache data: safe to refresh or evict. */
@Entity(tableName = "film")
data class Film(
    @PrimaryKey val tmdbId: Int,
    val title: String,
    /** Null when TMDB has no date (usually unreleased / in development). */
    val releaseDate: LocalDate?,
    val posterPath: String?,
    /** Minutes. Null until the full /movie/{id} details have been fetched. */
    val runtime: Int?,
    val isShort: Boolean,
    val isDocumentary: Boolean,
    /** Epoch millis. */
    val cachedAt: Long,
)
