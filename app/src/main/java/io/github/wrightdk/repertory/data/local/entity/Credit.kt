package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One credit of a person on a film. Cache data, so it cascades with the cached
 * [Person] and [Film] rows it links.
 */
@Entity(
    tableName = "credit",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["tmdbId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Film::class,
            parentColumns = ["tmdbId"],
            childColumns = ["filmId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId", "department"), Index("filmId")],
)
data class Credit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Int,
    val filmId: Int,
    val department: String,
    /** Crew job, e.g. "Director". Null for cast credits. */
    val job: String?,
    /** Cast character name. Null for crew credits. */
    val character: String?,
    /** TMDB cast order (billing position). Null for crew credits. */
    val order: Int?,
)
