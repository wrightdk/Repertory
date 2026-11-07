package io.github.wrightdk.repertory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Cached TMDB person from `/person/{id}`. Cache data: safe to refresh or evict. */
@Entity(tableName = "person")
data class Person(
    @PrimaryKey val tmdbId: Int,
    val name: String,
    val profilePath: String?,
    /** TMDB department, e.g. "Directing". Pre-ticked on Add to your list. */
    val knownForDepartment: String?,
    /** For life dates: "1928–1999", "b. 1983". Null when TMDB has none. */
    val birthday: LocalDate?,
    val deathday: LocalDate?,
    /** Epoch millis when this person and their credits were last fetched from TMDB (always together). */
    val cachedAt: Long,
)
