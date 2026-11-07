package io.github.wrightdk.repertory.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for TMDB API v3. Only the fields the app uses are declared; the
// Json instance ignores the rest. Everything TMDB may omit or send as null has a
// default so a sparse record never fails the whole response.

@Serializable
data class PagedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("total_results") val totalResults: Int = 0,
)

/** One result from `/search/person`. */
@Serializable
data class PersonSearchResultDto(
    val id: Int,
    val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    val popularity: Double = 0.0,
    val adult: Boolean = false,
    @SerialName("known_for") val knownFor: List<KnownForDto> = emptyList(),
)

@Serializable
data class KnownForDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    /** Films. */
    val title: String? = null,
    /** TV. */
    val name: String? = null,
)

/** `/person/{id}`. */
@Serializable
data class PersonDetailsDto(
    val id: Int,
    val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
)

/** `/person/{id}/combined_credits`. */
@Serializable
data class CombinedCreditsDto(
    val id: Int = 0,
    val cast: List<CastCreditDto> = emptyList(),
    val crew: List<CrewCreditDto> = emptyList(),
)

@Serializable
data class CastCreditDto(
    val id: Int,
    /** "movie" or "tv". Movie and TV ids are separate namespaces. */
    @SerialName("media_type") val mediaType: String? = null,
    /** Movies. */
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    /** TV. */
    val name: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
    val character: String? = null,
    val order: Int? = null,
)

@Serializable
data class CrewCreditDto(
    val id: Int,
    /** "movie" or "tv". Movie and TV ids are separate namespaces. */
    @SerialName("media_type") val mediaType: String? = null,
    /** Movies. */
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    /** TV. */
    val name: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
    val department: String? = null,
    val job: String? = null,
)

/** `/movie/{id}`. */
@Serializable
data class MovieDetailsDto(
    val id: Int,
    val title: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val runtime: Int? = null,
    val genres: List<GenreDto> = emptyList(),
    /** "Released", "Post Production", "In Production", "Planned", "Rumored", "Canceled". */
    val status: String? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String = "")

/** `/configuration`. */
@Serializable
data class ConfigurationDto(
    val images: ImagesConfigurationDto,
)

@Serializable
data class ImagesConfigurationDto(
    @SerialName("secure_base_url") val secureBaseUrl: String,
    @SerialName("poster_sizes") val posterSizes: List<String> = emptyList(),
    @SerialName("profile_sizes") val profileSizes: List<String> = emptyList(),
)

/** `/watch/providers/regions`: every country JustWatch (and so TMDB) has availability for. */
@Serializable
data class WatchRegionsDto(
    val results: List<WatchRegionDto> = emptyList(),
)

@Serializable
data class WatchRegionDto(
    /** "GB". */
    @SerialName("iso_3166_1") val code: String = "",
    @SerialName("english_name") val englishName: String = "",
)

/**
 * `/movie/{id}/watch/providers`: JustWatch availability, keyed by ISO 3166-1
 * region ("GB"). Only the categories the app shows are declared; "ads" and
 * "free" are ignored with any other unknown field.
 */
@Serializable
data class WatchProvidersDto(
    val id: Int = 0,
    val results: Map<String, WatchProviderRegionDto> = emptyMap(),
)

@Serializable
data class WatchProviderRegionDto(
    /** TMDB's own watch page for the film in this region. */
    val link: String? = null,
    /** Subscription streaming ("Stream" in the app). */
    val flatrate: List<WatchProviderDto> = emptyList(),
    val rent: List<WatchProviderDto> = emptyList(),
    val buy: List<WatchProviderDto> = emptyList(),
)

@Serializable
data class WatchProviderDto(
    @SerialName("provider_id") val providerId: Int = 0,
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    /** Lower comes first. Missing sorts last. */
    @SerialName("display_priority") val displayPriority: Int = Int.MAX_VALUE,
)
