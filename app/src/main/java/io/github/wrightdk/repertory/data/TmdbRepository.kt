package io.github.wrightdk.repertory.data

import androidx.room.withTransaction
import io.github.wrightdk.repertory.data.local.RepertoryDatabase
import io.github.wrightdk.repertory.data.local.dao.DepartmentCount
import io.github.wrightdk.repertory.data.local.dao.FilmCredit
import io.github.wrightdk.repertory.data.local.entity.Film
import io.github.wrightdk.repertory.data.local.entity.FilmographyFilters
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** How long cached TMDB data counts as fresh before a non-forced refresh refetches it. */
data class CachePolicy(
    val person: Duration = 7.days,
    val filmDetails: Duration = 30.days,
    /** Unreleased films change (date, status, runtime) as release nears, so check them more often. */
    val unreleasedFilmDetails: Duration = 1.days,
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

/** A department a person has credits in. */
data class RoleSummary(
    val department: String,
    /** Distinct titles credited in [department]. */
    val creditCount: Int,
    /** The crew job they most often had in [department] ("Screenplay"); null for acting. */
    val topJob: String?,
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
    /** Minimum gap between details requests. TMDB allows about 50 a second; stay well under. */
    detailsRequestInterval: Duration = 50.milliseconds,
    /** Waits before each retry of a transient details failure, on top of [retryPolicy]'s 429 handling. */
    private val detailsRetryDelays: List<Duration> = listOf(2.seconds, 5.seconds, 15.seconds),
) {
    private val detailsRateLimiter = RateLimiter(detailsRequestInterval)

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

    /**
     * Departments with cached credits for [personId], each with how many titles
     * its list would show under [filters] (canceled films never count). Most
     * first; departments that would show nothing are left out. Empty until
     * [refreshPerson] has succeeded.
     *
     * Runtimes aren't known until a list's film details are fetched, so shorts
     * are only left out where those details are already cached.
     */
    suspend fun departments(personId: Int, filters: FilmographyFilters = FilmographyFilters.DEFAULTS): List<DepartmentCount> {
        val name = personDao.get(personId)?.name
        return creditDao.creditsWithFilms(personId)
            .filterNot { it.film.isCanceled }
            .groupBy { it.department }
            .map { (department, credits) ->
                val shown = credits.groupBy { it.film.mediaType to it.film.tmdbId }.values.count { film ->
                    filters.showsAll(filmCategories(film.first().film, film.map { it.credit }, name))
                }
                DepartmentCount(department, shown)
            }
            .filter { it.filmCount > 0 }
            .sortedWith(compareByDescending<DepartmentCount> { it.filmCount }.thenBy { it.department })
    }

    /**
     * The roles [personId] could be tracked in: each department from
     * [departments] under [filters] (the defaults from Settings), its title
     * count and the job they most often had in it. Most titles first.
     */
    suspend fun roles(personId: Int, filters: FilmographyFilters = FilmographyFilters.DEFAULTS): List<RoleSummary> {
        val topJobs = creditDao.jobCounts(personId)
            .groupBy { it.department }
            .mapValues { (_, jobs) -> jobs.first().job }
        return departments(personId, filters).map { d ->
            RoleSummary(d.department, d.filmCount, topJobs[d.department])
        }
    }

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
     * [CachePolicy.filmDetails] ([CachePolicy.unreleasedFilmDetails] for a film
     * that isn't out yet). TV series have no details to fetch.
     */
    suspend fun refreshMovie(movieId: Int, force: Boolean = false): TmdbResult<Unit> {
        val cached = filmDao.get(MediaType.MOVIE, movieId)
        val cachedAt = cached?.detailsCachedAt
        val upcoming = cached != null && !cached.isCanceled && !cached.isReleased(today())
        val ttl = if (upcoming) cachePolicy.unreleasedFilmDetails else cachePolicy.filmDetails
        if (!force && cachedAt != null && isFresh(cachedAt, ttl)) {
            return TmdbResult.Success(Unit)
        }
        return fetchMovieDetails(movieId).map { }
    }

    /**
     * Fetches `/movie/{id}` for every movie in one list ([personId] in
     * [department]) whose details have never been loaded, plus unreleased ones
     * whose details have gone stale. Credits alone carry no runtime or status,
     * so until this finishes, shorts and unreleased films can't be told apart
     * and the list's count isn't final.
     *
     * Runs a few requests at a time, spaced to stay well under TMDB's rate
     * limit (shared by every caller). Transient failures (no network, 429, 5xx)
     * are retried with backoff; a film TMDB doesn't have (404) or can't serve is
     * marked as checked so it never blocks the list. Stops early on errors that
     * would fail every remaining request too.
     *
     * @return the number of films whose details were fetched.
     */
    suspend fun fillMissingFilmDetails(personId: Int, department: String): TmdbResult<Int> {
        val today = today()
        val ids = filmDao.listMovies(personId, department).filter { film ->
            val cachedAt = film.detailsCachedAt
            val upcoming = !film.isCanceled && !film.isReleased(today)
            cachedAt == null || (upcoming && !isFresh(cachedAt, cachePolicy.unreleasedFilmDetails))
        }.map { it.tmdbId }

        var fetched = 0
        for (batch in ids.chunked(DETAILS_CONCURRENCY)) {
            val results = coroutineScope {
                batch.map { id -> async { fetchDetailsForList(id) } }.awaitAll()
            }
            fetched += results.count { it is TmdbResult.Success && it.value }
            val fatal = results.firstNotNullOfOrNull { it.errorOrNull() }
            if (fatal != null) return TmdbResult.Failure(fatal)
        }
        return TmdbResult.Success(fetched)
    }

    /**
     * Ids of the lists whose count isn't final yet (some films still lack details).
     * Home shows those lists' count and progress bar as loading.
     */
    fun observeListIdsMissingDetails(): Flow<List<Long>> = filmDao.observeListIdsMissingDetails()

    /** Movies in this list whose details have never been fetched; 0 once its count is final. */
    fun observeMissingDetailsCount(personId: Int, department: String): Flow<Int> =
        filmDao.observeMissingDetailsCount(personId, department)

    /**
     * One film's details for [fillMissingFilmDetails]. Success(true) when
     * fetched, Success(false) when TMDB has nothing usable (marked as checked),
     * Failure when it should stop the whole run.
     */
    private suspend fun fetchDetailsForList(movieId: Int): TmdbResult<Boolean> {
        var attempt = 0
        while (true) {
            detailsRateLimiter.acquire()
            val error = fetchMovieDetails(movieId).errorOrNull() ?: return TmdbResult.Success(true)
            val transient = error == TmdbError.NoNetwork || error == TmdbError.RateLimited ||
                (error is TmdbError.Http && error.code >= 500)
            when {
                transient && attempt < detailsRetryDelays.size -> delay(detailsRetryDelays[attempt++])
                transient || error == TmdbError.InvalidApiKey -> return TmdbResult.Failure(error)
                else -> {
                    // 404 (deleted or merged), another 4xx, or a response that won't parse:
                    // retrying won't help, and the film shouldn't hold up the list forever.
                    markDetailsChecked(movieId)
                    return TmdbResult.Success(false)
                }
            }
        }
    }

    private suspend fun markDetailsChecked(movieId: Int) {
        val film = filmDao.get(MediaType.MOVIE, movieId) ?: return
        filmDao.upsert(film.copy(detailsCachedAt = clock()))
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

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun isFresh(cachedAt: Long, ttl: Duration): Boolean =
        (clock() - cachedAt) in 0 until ttl.inWholeMilliseconds

    private companion object {
        const val SQL_IN_CHUNK = 500
        const val DETAILS_CONCURRENCY = 4
    }
}
