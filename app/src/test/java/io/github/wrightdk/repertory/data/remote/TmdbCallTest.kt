package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.data.remote.FakeTmdbServer.Companion.json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every failure mode comes back as a typed [TmdbError], never an exception. */
class TmdbCallTest {
    private val tmdb = FakeTmdbServer()
    private val service = tmdb.service()
    private val policy = RetryPolicy(maxRetries = 2, defaultDelay = 1.seconds, maxDelay = 5.seconds)

    @After fun tearDown() = tmdb.close()

    private suspend fun configuration() = tmdbCall(policy) { service.configuration() }

    @Test fun `success`() = runTest {
        tmdb.routeFixture("/configuration", "configuration.json")
        assertTrue(configuration() is TmdbResult.Success)
    }

    @Test fun `401 is InvalidApiKey`() = runTest {
        tmdb.route("/configuration", json(401, fixture("error_401.json")))
        assertEquals(TmdbError.InvalidApiKey, configuration().errorOrNull())
        assertEquals("401 is not retried", 1, tmdb.requests.size)
    }

    @Test fun `404 is NotFound`() = runTest {
        val result = tmdbCall(policy) { service.movie(999_999_999) }
        assertEquals(TmdbError.NotFound, result.errorOrNull())
    }

    @Test fun `5xx is Http with the status code`() = runTest {
        tmdb.route("/configuration", json(503, "{}"))
        assertEquals(TmdbError.Http(503), configuration().errorOrNull())
        assertEquals("5xx is not retried", 1, tmdb.requests.size)
    }

    @Test fun `429 is retried after Retry-After, then succeeds`() = runTest {
        tmdb.route(
            "/configuration",
            json(429, fixture("error_429.json"), "Retry-After" to "3"),
            json(200, fixture("configuration.json")),
        )

        val result = configuration()

        assertTrue(result is TmdbResult.Success)
        assertEquals(2, tmdb.requests.size)
        assertEquals("waited for Retry-After", 3_000, currentTime)
    }

    @Test fun `repeated 429 gives RateLimited after the retries run out`() = runTest {
        tmdb.route("/configuration", json(429, fixture("error_429.json")))

        val result = configuration()

        assertEquals(TmdbError.RateLimited, result.errorOrNull())
        assertEquals("1 attempt + 2 retries", 3, tmdb.requests.size)
        assertEquals("two default waits", 2_000, currentTime)
    }

    @Test fun `Retry-After is capped`() = runTest {
        tmdb.route(
            "/configuration",
            json(429, fixture("error_429.json"), "Retry-After" to "3600"),
            json(200, fixture("configuration.json")),
        )
        assertTrue(configuration() is TmdbResult.Success)
        assertEquals(5_000, currentTime)
    }

    @Test fun `no retries with RetryPolicy NONE`() = runTest {
        tmdb.route("/configuration", json(429, fixture("error_429.json")))
        assertEquals(TmdbError.RateLimited, tmdbCall(RetryPolicy.NONE) { service.configuration() }.errorOrNull())
        assertEquals(1, tmdb.requests.size)
    }

    @Test fun `malformed body is Unexpected`() = runTest {
        tmdb.route("/configuration", json(200, """{"images": "not an object"}"""))
        assertTrue(configuration().errorOrNull() is TmdbError.Unexpected)
    }

    @Test fun `truncated body is Unexpected`() = runTest {
        tmdb.route("/configuration", json(200, fixture("configuration.json").take(100)))
        assertTrue(configuration().errorOrNull() is TmdbError.Unexpected)
    }

    @Test fun `no connection is NoNetwork`() = runTest {
        val deadUrl = tmdb.baseUrl
        tmdb.close() // Nothing listening any more: connection refused.
        val offline = TmdbClient.createService(keyProvider = { "k" }, baseUrl = deadUrl)
        assertEquals(TmdbError.NoNetwork, tmdbCall(policy) { offline.configuration() }.errorOrNull())
    }

    @Test fun `unknown host is NoNetwork`() = runTest {
        val offline = TmdbClient.createService(
            keyProvider = { "k" },
            baseUrl = "https://no-such-host.invalid/3/".toHttpUrl(),
        )
        assertEquals(TmdbError.NoNetwork, tmdbCall(policy) { offline.configuration() }.errorOrNull())
    }

    @Test fun `any other exception is Unexpected`() = runTest {
        val result = tmdbCall<Unit>(policy) { error("boom") }
        assertTrue(result.errorOrNull() is TmdbError.Unexpected)
    }

    @Test fun `cancellation is rethrown, not swallowed`() {
        assertThrows(CancellationException::class.java) {
            runTest { tmdbCall<Unit>(policy) { throw CancellationException("cancelled") } }
        }
    }
}
