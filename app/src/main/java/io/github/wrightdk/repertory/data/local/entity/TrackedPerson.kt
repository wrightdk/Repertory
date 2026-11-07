package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A list: one person tracked in one role. The same person can be tracked
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
    /** Epoch millis of the last change relevant to "Recent" sorting. */
    val updatedAt: Long = addedAt,
    /** This list's own filters, or null to follow the global defaults from Settings. */
    @Embedded(prefix = "filter_") val filterOverride: FilmographyFilters? = null,
) {
    fun filters(defaults: FilmographyFilters): FilmographyFilters = filterOverride ?: defaults
}

/** Which kinds of film a list shows. Each flag means "show". */
data class FilmographyFilters(
    val showShorts: Boolean,
    val showTv: Boolean,
    val showDocumentaries: Boolean,
    val showSelf: Boolean,
    val showMinorRoles: Boolean,
) {
    companion object {
        /** Out of the box: everything hidden except documentaries. */
        val DEFAULTS = FilmographyFilters(
            showShorts = false,
            showTv = false,
            showDocumentaries = true,
            showSelf = false,
            showMinorRoles = false,
        )
    }
}
