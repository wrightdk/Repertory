package io.github.wrightdk.repertory.data.remote

import io.github.wrightdk.repertory.BuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks the key the build resolved (local.properties -> TMDB_API_KEY env ->
 * gradle.properties) against the real TMDB API. Skipped unless asked for, so
 * normal test runs never touch the network:
 *
 *     ./gradlew testDebugUnitTest -PtmdbLive --tests '*TmdbLiveKeyTest'
 */
class TmdbLiveKeyTest {
    @Before fun onlyWhenRequested() {
        assumeTrue("Run with -PtmdbLive", System.getProperty("tmdb.live") == "true")
    }

    @Test fun `built-in key works against the real API`() = runTest {
        assertTrue("TMDB_API_KEY is empty in this build", BuildConfig.TMDB_API_KEY.isNotBlank())
        val service = TmdbClient.createService(keyProvider = { BuildConfig.TMDB_API_KEY })

        val result = tmdbCall { service.searchPeople("Agnès Varda") }

        val page = when (result) {
            is TmdbResult.Success -> result.value
            is TmdbResult.Failure -> throw AssertionError("TMDB call failed: ${result.error}")
        }
        assertEquals(6817, page.results.first().id)
    }
}
