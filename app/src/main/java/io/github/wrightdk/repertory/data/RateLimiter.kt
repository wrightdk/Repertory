package io.github.wrightdk.repertory.data

import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces calls at least [interval] apart, however many coroutines ask at once.
 * Each [acquire] reserves the next free slot and waits for it.
 */
class RateLimiter(
    private val interval: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    private var nextSlot = timeSource.markNow()

    suspend fun acquire() {
        if (interval <= Duration.ZERO) return
        val wait = mutex.withLock {
            val now = timeSource.markNow()
            val slot = if (nextSlot > now) nextSlot else now
            nextSlot = slot + interval
            slot - now
        }
        if (wait > Duration.ZERO) delay(wait)
    }
}
