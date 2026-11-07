package io.github.wrightdk.repertory.data

import io.github.wrightdk.repertory.data.local.dao.WatchProvidersDao
import io.github.wrightdk.repertory.data.local.dao.WatchRegionDao
import io.github.wrightdk.repertory.data.local.entity.WatchProvidersCache
import io.github.wrightdk.repertory.data.local.entity.WatchRegion
import io.github.wrightdk.repertory.data.remote.RetryPolicy
import io.github.wrightdk.repertory.data.remote.TmdbResult
import io.github.wrightdk.repertory.data.remote.TmdbService
import io.github.wrightdk.repertory.data.remote.tmdbCall
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** How long watch data is served without asking TMDB again, and kept at most (as an offline fallback). */
data class WatchCachePolicy(
    /** Availability changes daily. */
    val providersFreshFor: Duration = 24.hours,
    val providersKeepFor: Duration = 48.hours,
    /** JustWatch adds a country now and then. */
    val regionsFreshFor: Duration = 90.days,
    /** TMDB's terms allow caching for up to six months. */
    val regionsKeepFor: Duration = 180.days,
) {
    init {
        require(providersKeepFor >= providersFreshFor && regionsKeepFor >= regionsFreshFor)
    }
}

/**
 * "Where to watch" for a film page: JustWatch data via TMDB, cached in Room,
 * and the list of countries it covers, for the country setting.
 *
 * Both follow the same rules ([WatchCachePolicy]):
 * - Younger than "fresh for": served from the cache, no network call.
 * - Older: fetched again. If that fails, the old copy is served instead of the
 *   error, as long as it's younger than "keep for".
 * - Older than "keep for": deleted, when read and by [purgeExpired] at app start.
 *
 * "Nowhere to watch" is cached like anything else. Availability is cached per
 * film per country, so changing country means a fetch for the new one. This is
 * cache data only, never part of the JSON backup. The country is never sent to
 * TMDB: a film's response holds every country and is narrowed down here.
 */
class WatchProvidersRepository(
    private val service: TmdbService,
    private val providersDao: WatchProvidersDao,
    private val regionDao: WatchRegionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val policy: WatchCachePolicy = WatchCachePolicy(),
) {
    /**
     * Where [movieId] can be watched in [region] ("GB"). Never throws (except
     * cancellation); a failure with nothing cached to fall back on is a [TmdbResult.Failure].
     */
    suspend fun load(movieId: Int, region: String): TmdbResult<WatchAvailability> {
        val cached = cachedProviders(movieId, region)
        if (cached != null && age(cached.fetchedAt) < policy.providersFreshFor.inWholeMilliseconds) {
            return TmdbResult.Success(cached.availability)
        }
        return when (val result = tmdbCall(retryPolicy) { service.watchProviders(movieId) }) {
            is TmdbResult.Success -> {
                val availability = result.value.availability(region)
                providersDao.upsert(WatchProvidersCache(movieId, region, availability, fetchedAt = clock()))
                TmdbResult.Success(availability)
            }
            is TmdbResult.Failure -> cached?.let { TmdbResult.Success(it.availability) } ?: result
        }
    }

    /**
     * Every country with watch data, in no particular order. Same caching as
     * [load]: a failure with nothing cached to fall back on is a [TmdbResult.Failure].
     */
    suspend fun regions(): TmdbResult<List<WatchRegion>> {
        val cached = cachedRegions()
        val fetchedAt = cached.firstOrNull()?.fetchedAt
        if (fetchedAt != null && age(fetchedAt) < policy.regionsFreshFor.inWholeMilliseconds) {
            return TmdbResult.Success(cached)
        }
        return when (val result = tmdbCall(retryPolicy) { service.watchRegions() }) {
            is TmdbResult.Success -> {
                val now = clock()
                val regions = result.value.results
                    .filter { isRegionCode(it.code) }
                    .distinctBy { it.code }
                    .map { WatchRegion(it.code, it.englishName.trim().ifEmpty { it.code }, fetchedAt = now) }
                // An empty answer would leave nothing to choose from; keep what we had.
                if (regions.isEmpty()) return TmdbResult.Success(cached)
                regionDao.replaceAll(regions)
                TmdbResult.Success(regions)
            }
            is TmdbResult.Failure -> if (cached.isNotEmpty()) TmdbResult.Success(cached) else result
        }
    }

    /** Deletes everything too old to serve even offline. Returns how many rows went. */
    suspend fun purgeExpired(): Int {
        val now = clock()
        return providersDao.deleteFetchedOutside(cutoff = now - policy.providersKeepFor.inWholeMilliseconds, now = now) +
            regionDao.deleteFetchedOutside(cutoff = now - policy.regionsKeepFor.inWholeMilliseconds, now = now)
    }

    /** The cached entry if it's still usable (fresh or stale); deletes it if it's past keeping or won't parse. */
    private suspend fun cachedProviders(movieId: Int, region: String): WatchProvidersCache? {
        val entry = try {
            providersDao.get(movieId, region) ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A row written in an older format: drop it and fetch again.
            providersDao.delete(movieId, region)
            return null
        }
        if (age(entry.fetchedAt) !in 0 until policy.providersKeepFor.inWholeMilliseconds) {
            providersDao.delete(movieId, region)
            return null
        }
        return entry
    }

    /** The cached list if it's still usable; empty (and cleared) if it's past keeping. */
    private suspend fun cachedRegions(): List<WatchRegion> {
        val regions = regionDao.getAll()
        val fetchedAt = regions.firstOrNull()?.fetchedAt ?: return emptyList()
        if (age(fetchedAt) !in 0 until policy.regionsKeepFor.inWholeMilliseconds) {
            regionDao.deleteAll()
            return emptyList()
        }
        return regions
    }

    private fun age(fetchedAt: Long): Long = clock() - fetchedAt
}
