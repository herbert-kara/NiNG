package com.v2ray.ang.handler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class ServerCountryLookupTest {
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
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 1100 })
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

    @Test fun responseRequiresSuccessAndValidCountry() {
        assertEquals("JP", ServerCountryLookup.parseResponse("""{"success":true,"country_code":"JP"}"""))
        listOf("{}", "bad json", """{"success":false,"country_code":"US"}""",
            """{"country_code":"US"}""", """{"success":true,"country_code":"ZZ"}""").forEach {
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
