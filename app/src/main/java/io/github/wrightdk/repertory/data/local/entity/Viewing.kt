package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The user has seen a film. Keyed by film, so seeing it ticks it for every
 * person it appears under.
 *
 * User data. No foreign key to [Film] on purpose: evicting the film cache must
 * never lose the user's viewing history.
 */
@Entity(tableName = "viewing")
data class Viewing(
    @PrimaryKey val filmId: Int,
    val seenOn: LocalDate?,
    /** Out of 10 (so half-stars on a 5-star scale fit). */
    val rating: Int?,
    val note: String?,
)
