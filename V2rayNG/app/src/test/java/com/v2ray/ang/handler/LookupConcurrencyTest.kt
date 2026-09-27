package com.v2ray.ang.handler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * A page of flags never filled in, and the reason was a lock rather than the network: `resolve`
 * held one mutex across the whole lookup, so a request waited for the previous request's full
 * round trip. Eight concurrent lookups were therefore still issued one after another.
 *
 * These assert the property the fix is about: a *lock* may serialise the pacing, but it must not
 * serialise the round trips.
 *
 * Two things about the numbers here are load-bearing and were both wrong before. The clock is the
 * scheduler's virtual one, not a constant zero: rateLimit() reads it to decide how long to wait, so
 * a frozen clock makes every lookup pay the full gap and the test measures its own stub instead of
 * the code. And the fetch has to outlast the pacing gap, or the first request is always finished
 * before the second may start and "concurrent" is unreachable by arithmetic rather than by bug.
 */
class LookupConcurrencyTest {

    @Test
    fun concurrentResolvesOverlapTheirRoundTrips() = runTest {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val lookup = ServerFlaggedLookup(
            publicIpOf = { it },
            fetch = {
                peak.updateAndGet { p -> maxOf(p, inFlight.incrementAndGet()) }
                // Must outlast the 250ms pacing gap, or the first request is always finished before
                // the second one is allowed to start and nothing can ever overlap.
                delay(400)
                inFlight.decrementAndGet()
                """{"status":"ok","$it":{"isocode":"DE","proxy":"no","risk":0}}"""
            },
            nowMillis = { testScheduler.currentTime },
        )
        coroutineScope {
            (1..8).map { i -> async { lookup.resolve("10.0.0.$i") } }.awaitAll()
        }
        lookup.close()
        assertTrue(
            "the lookup is still serialised, so a page of rate-limited lookups takes minutes",
            peak.get() > 1,
        )
    }

    @Test
    fun aFreshPassStillReachesTheProviderRatherThanTheCache() = runTest {
        var asked = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { it },
            fetch = { asked++; """{"status":"ok","$it":{"isocode":"FR","proxy":"no","risk":0}}""" },
            nowMillis = { testScheduler.currentTime },
        )
        // The stub answers FR for every call; what is under test is how many times it is asked.
        assertEquals("FR", lookup.resolve("1.1.1.1")?.countryCode)
        assertEquals(1, asked)

        // The second pass without force is served from the cache.
        lookup.resolve("1.1.1.1")
        assertEquals("the cache was bypassed by a plain pass", 1, asked)

        // A latency test re-asks, because a cached verdict can predate the measurement.
        lookup.resolve("1.1.1.1", forceRefresh = true)
        assertEquals("a forced pass must reach the provider again", 2, asked)
        lookup.close()
    }

    @Test
    fun theCountryLookupAlsoOverlapsItsRoundTrips() = runTest {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val lookup = ServerCountryLookup(
            // Without this the lookup reaches for the platform DNS, which runs on a real thread
            // pool. runTest's virtual clock does not govern it, so the round trips stop overlapping
            // for a reason that has nothing to do with the locking under test.
            resolveDns = { host -> listOf(InetAddress.getByName(host)) },
            fetch = {
                peak.updateAndGet { p -> maxOf(p, inFlight.incrementAndGet()) }
                // Must outlast the 250ms pacing gap, or the first request is always finished before
                // the second one is allowed to start and nothing can ever overlap.
                delay(400)
                inFlight.decrementAndGet()
                """{"country_code":"NL"}"""
            },
            nowMillis = { testScheduler.currentTime },
        )
        val timeline = mutableListOf<String>()
        coroutineScope {
            (1..8).map { i ->
                async {
                    val at = lookup.resolve("10.0.0.$i")
                    timeline += "10.0.0.$i -> $at at t=${testScheduler.currentTime}"
                    at
                }
            }.awaitAll()
        }
        lookup.close()
        assertTrue(
            "the country fallback is still serialised, so a failed verdict costs a second wait. "
                + "peak in flight was " + peak.get() + "; the batch finished at "
                + testScheduler.currentTime + "ms and the row order was " + timeline,
            peak.get() > 1,
        )
    }

    @Test
    fun concurrentResolvesOfTheSameAddressAgreeOnOneAnswer() = runTest {
        var asked = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { it },
            fetch = { asked++; delay(50); """{"status":"ok","$it":{"isocode":"DE","proxy":"no","risk":0}}""" },
            nowMillis = { testScheduler.currentTime },
        )
        val answers = coroutineScope {
            (1..6).map { async { lookup.resolve("9.9.9.9")?.countryCode } }.awaitAll()
        }
        lookup.close()
        assertTrue("concurrent lookups of one address disagreed: $answers", answers.all { it == "DE" })
    }
}
