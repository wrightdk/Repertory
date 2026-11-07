package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import io.github.wrightdk.repertory.data.WatchAvailability

/**
 * Where to watch one movie in one region, as last fetched from TMDB. Cache
 * data only: short-lived (see `WatchProvidersRepository`), never in the JSON
 * backup. An empty [availability] is a cached "nowhere", not a missing row.
 */
@Entity(tableName = "watch_providers", primaryKeys = ["movieId", "region"])
data class WatchProvidersCache(
    val movieId: Int,
    /** ISO 3166-1, e.g. "GB". */
    val region: String,
    /** Stored as JSON. */
    val availability: WatchAvailability,
    /** Epoch millis. */
    val fetchedAt: Long,
)
