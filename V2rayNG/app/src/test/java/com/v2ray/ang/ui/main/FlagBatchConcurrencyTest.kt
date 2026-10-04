package com.v2ray.ang.ui.main

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The flags never appeared on a real page, and the reason was never a lookup failing: the walk
 * that asked for them was strictly one row at a time. The lookups are rate limited, so a serial
 * walk of a full page came to roughly two hours and the last row of the first screen was still
 * hours away. These pin the two properties that fix it: the walk overlaps its lookups, and it
 * still visits every target.
 */
class FlagBatchConcurrencyTest {

    @Test
    fun lookupsOverlapInsteadOfRunningOneAfterAnother() = runTest {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val targets = (1..16).map { "guid$it" to "10.0.0.$it" }

        runFlagBatch(
            targets = targets,
            force = true,
            lookup = { _, _ ->
                val now = inFlight.incrementAndGet()
                // Record the high-water mark: a serial walk can never exceed one.
                peak.updateAndGet { previous -> maxOf(previous, now) }
                delay(50)
                inFlight.decrementAndGet()
                "done"
            },
            publish = { _, _, _ -> },
        )

        assertTrue(
            "the walk is still serial, so a full page of rate-limited lookups takes hours",
            peak.get() > 1,
        )
    }

    @Test
    fun concurrencyStaysWithinTheConfiguredBound() = runTest {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        runFlagBatch(
            targets = (1..64).map { "g$it" to "10.0.0.$it" },
            force = true,
            concurrency = 4,
            lookup = { _, _ ->
                peak.updateAndGet { p -> maxOf(p, inFlight.incrementAndGet()) }
                delay(10)
                inFlight.decrementAndGet()
                "done"
            },
            publish = { _, _, _ -> },
        )
        assertTrue("the concurrency bound is ignored", peak.get() <= 4)
    }

    @Test
    fun everyTargetIsStillVisited() = runTest {
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val targets = (1..40).map { "guid$it" to "10.0.0.$it" }
        runFlagBatch(
            targets = targets,
            force = true,
            lookup = { address, _ -> address },
            publish = { _, address, _ -> seen += address },
        )
        assertEquals("a target was skipped", 40, seen.size)
    }

    @Test
    fun aFreshLatencyTestAsksTheProviderAgainRatherThanReplayingTheCache() = runTest {
        var asked = 0
        runFlagBatch(
            targets = listOf("a" to "1.1.1.1", "b" to "8.8.8.8"),
            force = true,
            lookup = { _, force ->
                asked++
                if (force) "fresh" else "cached"
            },
            publish = { _, _, _ -> },
        )
        // A cached pass would reuse the verdict filled before the measurement.
        assertEquals("every row of a fresh test has to be re-asked", 2, asked)
    }

    @Test
    fun oneUnresolvableRowDoesNotCostTheOthersTheirFlag() = runTest {
        val published = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        runFlagBatch(
            targets = listOf("a" to "1.1.1.1", "b" to "bad", "c" to "9.9.9.9"),
            force = true,
            lookup = { address, _ -> if (address == "bad") null else address },
            publish = { _, address, _ -> published += address },
        )
        assertEquals(setOf("1.1.1.1", "9.9.9.9"), published.toSet())
    }
}
