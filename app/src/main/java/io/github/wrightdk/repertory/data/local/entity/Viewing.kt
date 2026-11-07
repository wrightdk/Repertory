package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

/**
 * The user has seen a film. A row exists only while the film is ticked. Keyed
 * by film, so seeing it ticks it on every list it appears in. No ratings or
 * notes, by design: a viewing is "seen" plus an optional date.
 *
 * User data. No foreign key to [Film] on purpose: evicting the film cache or
 * removing a list must never lose the user's viewing history.
 */
@Entity(tableName = "viewing", primaryKeys = ["mediaType", "filmId"])
data class Viewing(
    val mediaType: MediaType,
    val filmId: Int,
    /** Null when seen but the user cleared the date. */
    val seenOn: LocalDate?,
)
