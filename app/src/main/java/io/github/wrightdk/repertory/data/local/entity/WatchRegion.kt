package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A country TMDB has watch provider (JustWatch) data for, from
 * `/watch/providers/regions`. The whole list is replaced at once, so every row
 * has the same [fetchedAt]. Cache data, never in the JSON backup.
 */
@Entity(tableName = "watch_region")
data class WatchRegion(
    /** ISO 3166-1 alpha-2, e.g. "GB". */
    @PrimaryKey val code: String,
    /** TMDB's English name, for when the phone has no name of its own for [code]. */
    val englishName: String,
    /** Epoch millis. */
    val fetchedAt: Long,
)
