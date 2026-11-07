package io.github.wrightdk.repertory.data.remote

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import retrofit2.HttpException

/** Why a TMDB call failed. The UI maps each case to a message; none of them crash. */
sealed interface TmdbError {
    /** No connection, DNS failure, timeout. Cached data is still usable. */
    data object NoNetwork : TmdbError

    /** HTTP 401: the key is missing, wrong or revoked. Point the user at the own-key setting. */
    data object InvalidApiKey : TmdbError

    /** HTTP 429 that persisted through retries. Point the user at the own-key setting. */
    data object RateLimited : TmdbError

    /** HTTP 404: TMDB has no such person or film (deleted or merged). */
    data object NotFound : TmdbError

    /** Any other non-2xx status, e.g. 5xx during a TMDB outage. */
    data class Http(val code: Int) : TmdbError

    /** The response arrived but could not be parsed, or something else unexpected went wrong. */
    data class Unexpected(val cause: Throwable) : TmdbError
}

sealed interface TmdbResult<out T> {
    data class Success<T>(val value: T) : TmdbResult<T>
    data class Failure(val error: TmdbError) : TmdbResult<Nothing>
}

inline fun <T, R> TmdbResult<T>.map(transform: (T) -> R): TmdbResult<R> = when (this) {
    is TmdbResult.Success -> TmdbResult.Success(transform(value))
    is TmdbResult.Failure -> this
}

fun <T> TmdbResult<T>.getOrNull(): T? = (this as? TmdbResult.Success)?.value

fun <T> TmdbResult<T>.errorOrNull(): TmdbError? = (this as? TmdbResult.Failure)?.error

/** How [tmdbCall] retries 429s. */
data class RetryPolicy(
    /** Extra attempts after the first 429. */
    val maxRetries: Int = 2,
    /** Wait used when TMDB sends no usable Retry-After header. */
    val defaultDelay: Duration = 1.seconds,
    /** Never wait longer than this per attempt, whatever Retry-After says. */
    val maxDelay: Duration = 10.seconds,
) {
    companion object {
        val NONE = RetryPolicy(maxRetries = 0)
    }
}

/**
 * Runs a TMDB call and converts every failure into a [TmdbError]. Retries 429s
 * per [retryPolicy]; any other failure is returned straight away. Coroutine
 * cancellation is rethrown, never swallowed.
 */
suspend fun <T> tmdbCall(
    retryPolicy: RetryPolicy = RetryPolicy(),
    block: suspend () -> T,
): TmdbResult<T> {
    var attempt = 0
    while (true) {
        try {
            return TmdbResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 429 && attempt < retryPolicy.maxRetries) {
                attempt++
                delay(retryAfter(e, retryPolicy))
                continue
            }
            return TmdbResult.Failure(httpError(e.code()))
        } catch (e: IOException) {
            return TmdbResult.Failure(TmdbError.NoNetwork)
        } catch (e: Exception) {
            // Includes kotlinx.serialization failures on malformed responses.
            return TmdbResult.Failure(TmdbError.Unexpected(e))
        }
    }
}

internal fun httpError(code: Int): TmdbError = when (code) {
    401 -> TmdbError.InvalidApiKey
    404 -> TmdbError.NotFound
    429 -> TmdbError.RateLimited
    else -> TmdbError.Http(code)
}

private fun retryAfter(e: HttpException, policy: RetryPolicy): Duration {
    val header = e.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()
    val wait = header?.takeIf { it >= 0 }?.seconds ?: policy.defaultDelay
    return minOf(wait, policy.maxDelay)
}
