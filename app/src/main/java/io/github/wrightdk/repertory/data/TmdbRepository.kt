package io.github.wrightdk.repertory.data

import androidx.room.withTransaction
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.dao.DepartmentCount
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.ImageConfig
import io.github.wrightdk.repertory.data.local.entity.MediaType
import io.github.wrightdk.repertory.data.local.entity.Person
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbError
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.TmdbService
import io.github.wrightdk.repertory.data.remote.errorOrNull
import io.github.wrightdk.repertory.data.remote.map
import io.github.wrightdk.repertory.data.remote.mergedWith
import io.github.wrightdk.repertory.data.remote.tmdbCall
import io.github.wrightdk.repertory.data.remote.toEntities
import io.github.wrightdk.repertory.data.remote.toEntity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** How long cached TMDB data counts as fresh before a non-forced refresh refetches it. */
data class CachePolicy(
    val person: Duration = 7.days,
    val filmDetails: Duration = 30.days,
    val imageConfig: Duration = 3.days,
)

data class PersonSearchResult(
    val tmdbId: Int,
    val name: String,
    val profilePath: String?,
    val knownForDepartment: String?,
    /** A few titles TMDB thinks they're known for, for telling namesakes apart. */
    val knownForTitles: List<String>,
)

data class PersonSearchPage(
    val results: List<PersonSearchResult>,
    val page: Int,
    val totalPages: Int,
    /** Across all pages, as TMDB counts them (before adult results are dropped). */
    val totalResults: Int,
) {
    companion object {
        val EMPTY = PersonSearchPage(emptyList(), page = 1, totalPages = 0, totalResults = 0)
    }
}

/**
 * The one way into TMDB data. Read from Room through the `observe*` flows, and
 * call the `refresh*` functions to fill or update the cache. Refreshes skip the
 * network while the cache is fresh, and report failures as [TmdbError]s rather
 * than throwing, so cached data stays usable offline.
 *
 * Search results are not stored in Room: they're throwaway, and caching them
 * would mix half-filled people into the person cache.
 */
class TmdbRepository(
    private val service: TmdbService,
    private val database: RepertoryDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val cachePolicy: CachePolicy = CachePolicy(),
) {
    private val personDao = database.personDao()
    private val filmDao = database.filmDao()
    private val creditDao = database.creditDao()
    private val imageConfigDao = database.imageConfigDao()

    // ---- Search ----

    suspend fun searchPeople(query: String, page: Int = 1): TmdbResult<PersonSearchPage> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return TmdbResult.Success(PersonSearchPage.EMPTY)
        return call { service.searchPeople(trimmed, page) }.map { response ->
            PersonSearchPage(
                results = response.results.filterNot { it.adult }.map { dto ->
                    PersonSearchResult(
                        tmdbId = dto.id,
                        name = dto.name,
                        profilePath = dto.profilePath,
                        knownForDepartment = dto.knownForDepartment,
                        knownForTitles = dto.knownFor.mapNotNull { it.title ?: it.name },
                    )
                },
                page = response.page,
                totalPages = response.totalPages,
                totalResults = response.totalResults,
            )
        }
    }

    // ---- People and their filmographies ----

    fun observePerson(personId: Int): Flow<Person?> = personDao.observe(personId)

    /** One row per credit in [department], chronological, undated last. Unfiltered. */
    fun observeFilmography(personId: Int, department: String): Flow<List<FilmCredit>> =
        filmDao.observeFilmography(personId, department)

    /** Departments with cached credits for [personId]; empty until [refreshPerson] has succeeded. */
    suspend fun departments(personId: Int): List<DepartmentCount> = creditDao.departments(personId)

    /**
     * Fetches the person and all their film credits and replaces the cached
     * copies, unless they were cached within [CachePolicy.person] and [force] is false.
     * Films already cached keep their `/movie/{id}` details (runtime, status).
     */
    suspend fun refreshPerson(personId: Int, force: Boolean = false): TmdbResult<Unit> {
        val cached = personDao.get(personId)
        if (!force && cached != null && isFresh(cached.cachedAt, cachePolicy.person)) {
            return TmdbResult.Success(Unit)
        }
        val person = when (val r = call { service.person(personId) }) {
            is TmdbResult.Success -> r.value
            is TmdbResult.Failure -> return r
        }
        val credits = when (val r = call { service.combinedCredits(personId) }) {
            is TmdbResult.Success -> r.value
            is TmdbResult.Failure -> return r
        }

        val now = clock()
        val mapped = credits.toEntities(personId, now)
        database.withTransaction {
            personDao.upsert(person.toEntity(now))
            val existing = mapped.films.groupBy({ it.mediaType }, { it.tmdbId })
                .flatMap { (type, ids) -> ids.chunked(SQL_IN_CHUNK).flatMap { filmDao.getByIds(type, it) } }
                .associateBy { it.mediaType to it.tmdbId }
            filmDao.upsertAll(mapped.films.map { it.mergedWith(existing[it.mediaType to it.tmdbId]) })
            creditDao.replaceForPerson(personId, mapped.credits)
        }
        return TmdbResult.Success(Unit)
    }

    // ---- Films ----

    fun observeFilm(mediaType: MediaType, filmId: Int): Flow<Film?> = filmDao.observe(mediaType, filmId)

    /**
     * Fetches `/movie/{id}` for [movieId] unless details were cached within
     * [CachePolicy.filmDetails]. TV series have no details to fetch.
     */
    suspend fun refreshMovie(movieId: Int, force: Boolean = false): TmdbResult<Unit> {
        val cachedAt = filmDao.get(MediaType.MOVIE, movieId)?.detailsCachedAt
        if (!force && cachedAt != null && isFresh(cachedAt, cachePolicy.filmDetails)) {
            return TmdbResult.Success(Unit)
        }
        return fetchMovieDetails(movieId).map { }
    }

    /**
     * Fetches `/movie/{id}` for every movie of [personId] that has never had its
     * details loaded (credits alone don't include runtime or status, so shorts
     * and unreleased films can't be told apart without this). Runs a few requests at a time and stops early on
     * errors that would fail every remaining request too; a missing film is skipped.
     *
     * @return the number of films whose details were fetched.
     */
    suspend fun fillMissingFilmDetails(personId: Int): TmdbResult<Int> {
        var fetched = 0
        for (batch in filmDao.movieIdsMissingDetails(personId).chunked(DETAILS_CONCURRENCY)) {
            val results = coroutineScope {
                batch.map { id -> async { fetchMovieDetails(id) } }.awaitAll()
            }
            fetched += results.count { it is TmdbResult.Success }
            val fatal = results.firstNotNullOfOrNull { r -> r.errorOrNull()?.takeIf { it != TmdbError.NotFound } }
            if (fatal != null) return TmdbResult.Failure(fatal)
        }
        return TmdbResult.Success(fetched)
    }

    private suspend fun fetchMovieDetails(movieId: Int): TmdbResult<Film> =
        call { service.movie(movieId) }.map { dto ->
            dto.toEntity(clock()).also { filmDao.upsert(it) }
        }

    // ---- Images ----

    /**
     * Image base URL and sizes. Never fails: on a network error it falls back to
     * the cached copy, or to [ImageConfig.DEFAULT] if there isn't one.
     */
    suspend fun imageConfig(): ImageConfig {
        val cached = imageConfigDao.get()
        if (cached != null && isFresh(cached.cachedAt, cachePolicy.imageConfig)) return cached
        return when (val r = call { service.configuration() }) {
            is TmdbResult.Success -> r.value.toEntity(clock()).also { imageConfigDao.upsert(it) }
            is TmdbResult.Failure -> cached ?: ImageConfig.DEFAULT
        }
    }

    // ----

    private suspend fun <T> call(block: suspend () -> T): TmdbResult<T> = tmdbCall(retryPolicy, block)

    private fun isFresh(cachedAt: Long, ttl: Duration): Boolean =
        (clock() - cachedAt) in 0 until ttl.inWholeMilliseconds

    private companion object {
        const val SQL_IN_CHUNK = 500
        const val DETAILS_CONCURRENCY = 4
    }
}
