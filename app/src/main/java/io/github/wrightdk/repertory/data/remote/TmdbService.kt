package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.remote.dto.CombinedCreditsDto
import io.github.wrightdk.repertory.data.remote.dto.ConfigurationDto
import io.github.wrightdk.repertory.data.remote.dto.MovieDetailsDto
import io.github.wrightdk.repertory.data.remote.dto.PagedResponse
import io.github.wrightdk.repertory.data.remote.dto.PersonDetailsDto
import io.github.wrightdk.repertory.data.remote.dto.PersonSearchResultDto
import io.github.wrightdk.repertory.data.remote.dto.WatchProvidersDto
import io.github.wrightdk.repertory.data.remote.dto.WatchRegionsDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB API v3. The API key is added by [ApiKeyInterceptor], not here.
 *
 * Calls throw on failure (HTTP errors, IO, parsing); go through [TmdbRepository]
 * which turns those into [TmdbError]s.
 */
interface TmdbService {
    @GET("search/person")
    suspend fun searchPeople(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): PagedResponse<PersonSearchResultDto>

    @GET("person/{id}")
    suspend fun person(@Path("id") personId: Int): PersonDetailsDto

    @GET("person/{id}/combined_credits")
    suspend fun combinedCredits(@Path("id") personId: Int): CombinedCreditsDto

    @GET("movie/{id}")
    suspend fun movie(@Path("id") movieId: Int): MovieDetailsDto

    /** Every region's availability; callers pick one out. */
    @GET("movie/{id}/watch/providers")
    suspend fun watchProviders(@Path("id") movieId: Int): WatchProvidersDto

    /** The countries watch provider data covers. */
    @GET("watch/providers/regions")
    suspend fun watchRegions(): WatchRegionsDto

    @GET("configuration")
    suspend fun configuration(): ConfigurationDto
}
