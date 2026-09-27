package com.v2ray.ang.handler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class ServerCountryLookupTest {
    @Test fun everyProviderShapeYieldsACountry() {
        // The three providers disagree on the field name and on the success wrapper.
        assertEquals("FR", ServerCountryLookup.parseResponse("""{"success":true,"country_code":"FR"}"""))
        assertEquals("DE", ServerCountryLookup.parseResponse("""{"country_code":"DE","ip":"1.2.3.4"}"""))
        assertEquals("JP", ServerCountryLookup.parseResponse("""{"country":"JP","ip":"1.2.3.4"}"""))
        assertEquals("NL", ServerCountryLookup.parseResponse("""{"countryCode":"NL"}"""))
    }

    @Test fun aFailedOrUnknownPayloadYieldsNoCountry() {
        assertNull(ServerCountryLookup.parseResponse("""{"success":false,"message":"quota"}"""))
        assertNull(ServerCountryLookup.parseResponse("""{"country_code":"ZZ"}"""))
        assertNull(ServerCountryLookup.parseResponse("""{"error":"nope"}"""))
        assertNull(ServerCountryLookup.parseResponse("not json"))
    }

    @Test fun moreThanOneEndpointIsConfiguredSoOneCanBeBlocked() {
        assertTrue(
            "a single endpoint leaves the flag blank whenever that one is blocked",
            ServerCountryLookup.COUNTRY_ENDPOINTS.size > 1,
        )
        assertTrue(
            "every provider has to ask about an address, or it returns the tunnel's own exit and " +
                "every row shows the selected server's country",
            ServerCountryLookup.COUNTRY_ENDPOINTS.all { it.contains("{ip}") },
        )
    }

    @Test fun theTlsProvidersAreTriedBeforeAnyPlainHttpOne() {
        // ip-api.com is plain HTTP: the request and the answer both travel in clear, so whoever is
        // on the path can rewrite the country. It answers faster than the others and from networks
        // that refuse them, which is why it is configured at all -- but it is configured last, so
        // it is only reached when every TLS provider has already failed.
        val endpoints = ServerCountryLookup.COUNTRY_ENDPOINTS
        val schemes = endpoints.map { it.substringBefore("://") }
        val firstPlain = schemes.indexOf("http")
        if (firstPlain >= 0) {
            assertEquals(
                "a plain-HTTP provider is configured at $firstPlain of $endpoints, so it is " +
                    "reached before the TLS providers that would answer it",
                List(firstPlain) { "https" },
                schemes.take(firstPlain),
            )
            assertEquals(
                "the plain-HTTP provider should be the last resort, not one of several",
                "http",
                schemes.last(),
            )
        }
        // And the fastest measured TLS provider leads, since it is the one that answers from a
        // restricted network.
        assertTrue(
            "the fastest measured provider should lead the list, was $endpoints",
            endpoints.first().contains("ipwho.is"),
        )
    }

    @Test fun privateReservedAndMalformedInputsNeverLeaveDevice() = runTest {
        var dns = 0
        var http = 0
        val lookup = ServerCountryLookup(resolveDns = { dns++; emptyList() }, fetch = { http++; null })
        listOf("127.0.0.1", "10.0.0.1", "172.16.1.1", "192.168.1.1", "169.254.1.1",
            "100.64.0.1", "192.0.0.8", "192.0.2.1", "198.51.100.1", "203.0.113.1",
            "198.18.0.1", "224.0.0.1", "255.255.255.255", "0.0.0.0", "::1", "fc00::1",
            "fe80::1", "ff02::1", "2001:db8::1", "2002::1", "3fff::1", "::ffff:10.0.0.1",
            "999.1.1.1", "127.1", "2130706433", "localhost", "router.local", "x.internal",
            "https://example.com", "user:password@example.com", "host/path", "", "[::1]").forEach {
            assertNull(it, lookup.resolve(it))
        }
        assertEquals(0, dns)
        assertEquals(0, http)
    }

    @Test fun mixedDnsAnswersSendOnlyPublicIpAndNeverHostname() = runTest {
        val sent = mutableListOf<String>()
        val lookup = ServerCountryLookup(resolveDns = {
            listOf(InetAddress.getByName("192.168.1.1"), InetAddress.getByName("8.8.8.8"))
        }, fetch = { sent += it; "US" }, nowMillis = { testScheduler.currentTime })
        assertEquals("US", lookup.resolve("Example.COM."))
        assertEquals("US", lookup.resolve("example.com"))
        assertEquals(listOf("8.8.8.8"), sent)
    }

    @Test fun concurrentRequestsAreDeduplicatedAndRateLimited() = runTest {
        val starts = mutableListOf<Long>()
        val lookup = ServerCountryLookup(fetch = {
            starts += testScheduler.currentTime
            delay(10)
            "DE"
        }, nowMillis = { testScheduler.currentTime })
        val results = listOf("8.8.8.8", "1.1.1.1", "9.9.9.9", "8.8.8.8").map {
            async { lookup.resolve(it) }
        }.awaitAll()
        assertTrue(results.all { it == "DE" })
        assertEquals(3, starts.size)
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 250 })
    }

    @Test fun failuresAndExceptionsAreNegativelyCachedThenExpire() = runTest {
        var calls = 0
        var clock = 0L
        val lookup = ServerCountryLookup(fetch = { calls++; throw IllegalStateException("offline") },
            nowMillis = { clock })
        assertNull(lookup.resolve("8.8.8.8"))
        assertNull(lookup.resolve("8.8.8.8"))
        assertEquals(1, calls)
        clock = 300_001
        assertNull(lookup.resolve("8.8.8.8"))
        assertEquals(2, calls)
    }

    @Test fun cacheEvictsOldestAndRejectsInvalidCountry() = runTest {
        var calls = 0
        val lookup = ServerCountryLookup(fetch = { calls++; "ZZ" }, cacheLimit = 2,
            nowMillis = { testScheduler.currentTime })
        listOf("8.8.8.8", "1.1.1.1", "9.9.9.9", "8.8.8.8").forEach {
            assertNull(lookup.resolve(it))
        }
        assertEquals(4, calls)
    }

    @Test fun responseRejectsUnusablePayloads() {
        assertEquals("JP", ServerCountryLookup.parseResponse("""{"success":true,"country_code":"JP"}"""))
        // A missing "success" key is no longer a rejection: ipapi.co and ipinfo.io both answer in
        // that shape, and requiring the wrapper left those two providers unusable. A payload is
        // only rejected when it explicitly reports failure or carries no usable country.
        listOf("{}", "bad json", """{"success":false,"country_code":"US"}""",
            """{"success":true,"country_code":"ZZ"}""", """{"error":"nope"}""").forEach {
            assertNull(ServerCountryLookup.parseResponse(it))
        }
    }

    @Test fun cancellationDoesNotBecomeNegativeCache() = runTest {
        var calls = 0
        val lookup = ServerCountryLookup(fetch = { calls++; delay(5000); "US" },
            nowMillis = { testScheduler.currentTime })
        val job = async { lookup.resolve("8.8.8.8") }
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        assertEquals("US", lookup.resolve("8.8.8.8"))
        assertEquals(2, calls)
    }
}
