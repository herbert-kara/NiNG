package com.v2ray.ang.handler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A page of flags never filled in, and the reason was a lock rather than the network: `resolve`
 * held one mutex across the whole lookup, so a request waited for the previous request's full
 * round trip. Eight concurrent lookups were therefore still issued one after another.
 *
 * These assert the property the fix is about: a *lock* may serialise the pacing, but it must not
 * serialise the round trips.
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
                delay(100)
                inFlight.decrementAndGet()
                """{"status":"ok","$it":{"isocode":"DE","proxy":"no","risk":0}}"""
            },
            nowMillis = { 0L },
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
            nowMillis = { 0L },
        )
        assertEquals("DE", lookup.resolve("1.1.1.1")?.countryCode ?: "DE")
        val first = lookup.resolve("1.1.1.1")
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
            fetch = {
                peak.updateAndGet { p -> maxOf(p, inFlight.incrementAndGet()) }
                delay(100)
                inFlight.decrementAndGet()
                """{"country_code":"NL"}"""
            },
            nowMillis = { 0L },
        )
        coroutineScope {
            (1..8).map { i -> async { lookup.resolve("10.0.0.$i") } }.awaitAll()
        }
        lookup.close()
        assertTrue(
            "the country fallback is still serialised, so a failed verdict costs a second wait",
            peak.get() > 1,
        )
    }

    @Test
    fun concurrentResolvesOfTheSameAddressAgreeOnOneAnswer() = runTest {
        var asked = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { it },
            fetch = { asked++; delay(50); """{"status":"ok","$it":{"isocode":"DE","proxy":"no","risk":0}}""" },
            nowMillis = { 0L },
        )
        val answers = coroutineScope {
            (1..6).map { async { lookup.resolve("9.9.9.9")?.countryCode } }.awaitAll()
        }
        lookup.close()
        assertTrue("concurrent lookups of one address disagreed: $answers", answers.all { it == "DE" })
    }
}
