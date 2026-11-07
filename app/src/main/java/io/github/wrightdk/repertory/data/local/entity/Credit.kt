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
            parentColumns = ["mediaType", "tmdbId"],
            childColumns = ["mediaType", "filmId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId", "department"), Index("mediaType", "filmId")],
)
data class Credit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Int,
    /** With [filmId], the [Film] this credit is on. */
    val mediaType: MediaType,
    val filmId: Int,
    /** TMDB department: "Acting" for cast, else the crew department, e.g. "Directing". */
    val department: String,
    /** Crew job, e.g. "Director". Null for cast credits. */
    val job: String?,
    /** Cast character name as TMDB sends it, e.g. "Harry Palmer", "Self", "Nun (uncredited)". Null for crew. */
    val character: String?,
    /** TMDB cast order (billing position, 0 first). Null for crew credits. */
    val order: Int?,
    /** Cast credit playing themselves (interviews, making-ofs, talk shows). Always false for crew. */
    val isSelf: Boolean,
)
