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
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
    val character: String? = null,
    val order: Int? = null,
)

@Serializable
data class CrewCreditDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
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
