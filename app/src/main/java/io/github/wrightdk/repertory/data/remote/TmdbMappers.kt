package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.remote.dto.CombinedCreditsDto
import io.github.wrightdk.repertory.data.remote.dto.ConfigurationDto
import io.github.wrightdk.repertory.data.remote.dto.MovieDetailsDto
import io.github.wrightdk.repertory.data.remote.dto.PersonDetailsDto
import java.time.LocalDate
import java.time.format.DateTimeParseException

// DTO -> Room entity conversion. Pure functions, no I/O.

internal object TmdbGenre {
    const val DOCUMENTARY = 99
    const val TV_MOVIE = 10770
}

internal const val ACTING_DEPARTMENT = "Acting"

/** Academy definition of a short: 40 minutes or less. TMDB uses 0 for "unknown". */
internal const val SHORT_MAX_MINUTES = 40

/** TMDB sends "" for unknown dates; treat that, and anything unparseable, as no date. */
internal fun parseTmdbDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun PersonDetailsDto.toEntity(now: Long) = Person(
    tmdbId = id,
    name = name,
    profilePath = profilePath,
    cachedAt = now,
)

/** Films and credits for one person, already filtered to films (no TV series, no adult titles). */
internal data class MappedCredits(val films: List<Film>, val credits: List<Credit>)

internal fun CombinedCreditsDto.toEntities(personId: Int, now: Long): MappedCredits {
    val films = LinkedHashMap<Int, Film>()
    val credits = mutableListOf<Credit>()

    fun addFilm(id: Int, title: String?, releaseDate: String?, posterPath: String?, genreIds: List<Int>) {
        films.getOrPut(id) {
            Film(
                tmdbId = id,
                title = title.orEmpty(),
                releaseDate = parseTmdbDate(releaseDate),
                posterPath = posterPath,
                runtime = null,
                isShort = false,
                isDocumentary = TmdbGenre.DOCUMENTARY in genreIds,
                isTvMovie = TmdbGenre.TV_MOVIE in genreIds,
                cachedAt = now,
            )
        }
    }

    for (c in cast) {
        if (c.mediaType != "movie" || c.adult) continue
        addFilm(c.id, c.title, c.releaseDate, c.posterPath, c.genreIds)
        credits += Credit(
            personId = personId,
            filmId = c.id,
            department = ACTING_DEPARTMENT,
            job = null,
            character = c.character,
            order = c.order,
        )
    }
    for (c in crew) {
        if (c.mediaType != "movie" || c.adult) continue
        addFilm(c.id, c.title, c.releaseDate, c.posterPath, c.genreIds)
        credits += Credit(
            personId = personId,
            filmId = c.id,
            department = c.department ?: "Crew",
            job = c.job,
            character = null,
            order = null,
        )
    }
    return MappedCredits(films.values.toList(), credits)
}

/**
 * Merges a film seen in a credits list with what is already cached: credits
 * carry the newer title/date/poster/genres, but no runtime, so keep the
 * details-derived fields from [existing].
 */
internal fun Film.mergedWith(existing: Film?): Film =
    if (existing == null) this
    else copy(
        runtime = existing.runtime,
        isShort = existing.isShort,
        detailsCachedAt = existing.detailsCachedAt,
    )

internal fun MovieDetailsDto.toEntity(now: Long): Film {
    val genreIds = genres.map { it.id }
    val minutes = runtime?.takeIf { it > 0 }
    return Film(
        tmdbId = id,
        title = title,
        releaseDate = parseTmdbDate(releaseDate),
        posterPath = posterPath,
        runtime = minutes,
        isShort = minutes != null && minutes <= SHORT_MAX_MINUTES,
        isDocumentary = TmdbGenre.DOCUMENTARY in genreIds,
        isTvMovie = TmdbGenre.TV_MOVIE in genreIds,
        cachedAt = now,
        detailsCachedAt = now,
    )
}

internal fun ConfigurationDto.toEntity(now: Long) = ImageConfig(
    secureBaseUrl = images.secureBaseUrl,
    posterSizes = images.posterSizes,
    profileSizes = images.profileSizes,
    cachedAt = now,
)
