package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached TMDB person. Cache data: safe to refresh or evict. */
@Entity(tableName = "person")
data class Person(
    @PrimaryKey val tmdbId: Int,
    val name: String,
    val profilePath: String?,
    /** Epoch millis when this row was last fetched from TMDB. */
    val cachedAt: Long,
)
