package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A person the user is tracking under one role. The same person can be tracked
 * under several departments, so (personId, department) is unique rather than personId.
 *
 * User data. No foreign key to [Person] on purpose: refreshing or evicting the
 * person cache must never delete what the user has chosen to track.
 */
@Entity(
    tableName = "tracked_person",
    indices = [Index(value = ["personId", "department"], unique = true)],
)
data class TrackedPerson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Int,
    /** TMDB department, e.g. "Directing", "Acting", "Writing", "Camera", "Sound". */
    val department: String,
    /** Epoch millis. */
    val addedAt: Long,
    /** Epoch millis of the last change relevant to "recently updated" sorting. */
    val updatedAt: Long = addedAt,
    @Embedded val filters: FilmographyFilters = FilmographyFilters(),
)

/** Per-tracked-person filter preferences. Defaults hide the noisy entries. */
data class FilmographyFilters(
    val showShorts: Boolean = false,
    val showTv: Boolean = false,
    val showDocumentaries: Boolean = false,
    val showMinorRoles: Boolean = false,
)
