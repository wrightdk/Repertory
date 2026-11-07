package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.local.entity.Credit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
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

/**
 * TMDB's ways of saying someone plays themselves: "Self", "Himself",
 * "Herself", "Themselves", often with a suffix ("Self - Host",
 * "Himself (archive footage)").
 */
private val SELF_CHARACTER = Regex("""^\s*(self|himself|herself|themselves|themself)\b""", RegexOption.IGNORE_CASE)

internal fun isSelfCharacter(character: String?): Boolean =
    character != null && SELF_CHARACTER.containsMatchIn(character)

internal fun PersonDetailsDto.toEntity(now: Long) = Person(
    tmdbId = id,
    name = name,
    profilePath = profilePath,
    knownForDepartment = knownForDepartment,
    birthday = parseTmdbDate(birthday),
    deathday = parseTmdbDate(deathday),
    cachedAt = now,
)

/** Films and credits for one person: movies and TV series, no adult titles. */
internal data class MappedCredits(val films: List<Film>, val credits: List<Credit>)

internal fun CombinedCreditsDto.toEntities(personId: Int, now: Long): MappedCredits {
    val films = LinkedHashMap<Pair<MediaType, Int>, Film>()
    val credits = mutableListOf<Credit>()

    fun addFilm(type: MediaType, id: Int, title: String?, date: String?, posterPath: String?, genreIds: List<Int>) {
        films.getOrPut(type to id) {
            Film(
                mediaType = type,
                tmdbId = id,
                title = title.orEmpty(),
                releaseDate = parseTmdbDate(date),
                posterPath = posterPath,
                genreIds = genreIds,
                runtime = null,
                isShort = false,
                isDocumentary = TmdbGenre.DOCUMENTARY in genreIds,
                isTvMovie = TmdbGenre.TV_MOVIE in genreIds,
                status = null,
                cachedAt = now,
            )
        }
    }

    for (c in cast) {
        val type = MediaType.fromTmdb(c.mediaType) ?: continue
        if (c.adult) continue
        when (type) {
            MediaType.MOVIE -> addFilm(type, c.id, c.title, c.releaseDate, c.posterPath, c.genreIds)
            MediaType.TV -> addFilm(type, c.id, c.name, c.firstAirDate, c.posterPath, c.genreIds)
        }
        credits += Credit(
            personId = personId,
            mediaType = type,
            filmId = c.id,
            department = ACTING_DEPARTMENT,
            job = null,
            character = c.character,
            order = c.order,
            isSelf = isSelfCharacter(c.character),
        )
    }
    for (c in crew) {
        val type = MediaType.fromTmdb(c.mediaType) ?: continue
        if (c.adult) continue
        when (type) {
            MediaType.MOVIE -> addFilm(type, c.id, c.title, c.releaseDate, c.posterPath, c.genreIds)
            MediaType.TV -> addFilm(type, c.id, c.name, c.firstAirDate, c.posterPath, c.genreIds)
        }
        credits += Credit(
            personId = personId,
            mediaType = type,
            filmId = c.id,
            department = c.department ?: "Crew",
            job = c.job,
            character = null,
            order = null,
            isSelf = false,
        )
    }
    return MappedCredits(films.values.toList(), credits)
}

/**
 * Merges a film seen in a credits list with what is already cached: credits
 * carry the newer title/date/poster/genres, but no runtime or status, so keep
 * the details-derived fields from [existing].
 */
internal fun Film.mergedWith(existing: Film?): Film =
    if (existing == null) this
    else copy(
        runtime = existing.runtime,
        isShort = existing.isShort,
        status = existing.status,
        detailsCachedAt = existing.detailsCachedAt,
    )

internal fun MovieDetailsDto.toEntity(now: Long): Film {
    val genreIds = genres.map { it.id }
    val minutes = runtime?.takeIf { it > 0 }
    return Film(
        mediaType = MediaType.MOVIE,
        tmdbId = id,
        title = title,
        releaseDate = parseTmdbDate(releaseDate),
        posterPath = posterPath,
        genreIds = genreIds,
        runtime = minutes,
        isShort = minutes != null && minutes <= SHORT_MAX_MINUTES,
        isDocumentary = TmdbGenre.DOCUMENTARY in genreIds,
        isTvMovie = TmdbGenre.TV_MOVIE in genreIds,
        status = status?.takeIf { it.isNotBlank() },
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
