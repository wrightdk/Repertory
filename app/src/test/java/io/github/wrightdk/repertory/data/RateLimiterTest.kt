package io.github.wrightdk.repertory.data

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {
    @Test fun `concurrent callers are spaced by the interval`() = runTest {
        val limiter = RateLimiter(50.milliseconds, testTimeSource)
        val times = mutableListOf<Long>()
        repeat(4) { launch { limiter.acquire(); times += currentTime } }
        advanceUntilIdle()
        assertEquals(listOf(0L, 50L, 100L, 150L), times.sorted())
    }
}
