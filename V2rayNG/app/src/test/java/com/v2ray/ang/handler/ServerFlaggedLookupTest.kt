package com.v2ray.ang.handler

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFlaggedLookupTest {

    private fun ok(vararg entries: String) = """{"status":"ok",${entries.joinToString(",")}}"""

    private fun entry(ip: String, proxy: String, type: String?, risk: Int) =
        """"$ip":{"proxy":"$proxy"${type?.let { ""","type":"$it"""" } ?: ""},"risk":$risk,"isocode":"US","asn":"AS1"}"""

    // ---------- verdict parsing ----------

    @Test fun proxyYesIsFlagged() = runTest {
        val verdict = ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "yes", "VPN", 10)), "1.2.3.4")
        assertEquals(FlagStatus.FLAGGED, verdict?.status)
        assertEquals("VPN", verdict?.type)
        assertEquals(10, verdict?.risk)
        assertEquals("US", verdict?.countryCode)
    }

    @Test fun vpnTypeIsFlaggedEvenWhenProxySaysNo() {
        val verdict = ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", "VPN", 0)), "1.2.3.4")
        assertEquals(FlagStatus.FLAGGED, verdict?.status)
    }

    @Test fun riskAtOrAboveThresholdIsFlagged() {
        assertEquals(
            FlagStatus.FLAGGED,
            ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, 60)), "1.2.3.4")?.status,
        )
        assertEquals(
            FlagStatus.FLAGGED,
            ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, 100)), "1.2.3.4")?.status,
        )
    }

    @Test fun riskBelowThresholdIsClean() {
        val verdict = ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, 59)), "1.2.3.4")
        assertEquals(FlagStatus.CLEAN, verdict?.status)
        assertEquals(59, verdict?.risk)
    }

    @Test fun nonOkStatusIsNotAVerdict() {
        assertNull(ServerFlaggedLookup.parseVerdict("""{"status":"denied"}""", "1.2.3.4"))
        assertNull(ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "yes", "VPN", 90)).replace("\"ok\"", "\"denied\""), "1.2.3.4"))
    }

    @Test fun aVerdictForAnotherAddressIsNeverReused() {
        // The payload is keyed by IP; a different key must not answer for the queried one.
        val body = ok(entry("9.9.9.9", "yes", "VPN", 90))
        assertNull(ServerFlaggedLookup.parseVerdict(body, "1.2.3.4"))
        assertEquals(FlagStatus.FLAGGED, ServerFlaggedLookup.parseVerdict(body, "9.9.9.9")?.status)
    }

    @Test fun malformedBodiesFailClosed() {
        assertNull(ServerFlaggedLookup.parseVerdict(null, "1.2.3.4"))
        assertNull(ServerFlaggedLookup.parseVerdict("", "1.2.3.4"))
        assertNull(ServerFlaggedLookup.parseVerdict("not json", "1.2.3.4"))
        assertNull(ServerFlaggedLookup.parseVerdict("""{"status":"ok"}""", "1.2.3.4"))
        assertNull(ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, 0)), null))
    }

    @Test fun riskIsClampedToItsDocumentedRange() {
        assertEquals(100, ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, 999)), "1.2.3.4")?.risk)
        assertEquals(0, ServerFlaggedLookup.parseVerdict(ok(entry("1.2.3.4", "no", null, -5)), "1.2.3.4")?.risk)
    }

    // ---------- resolution, cache and privacy ----------

    @Test fun aNonPublicAddressIsRefusedWithoutAnyRequest() = runTest {
        var requested = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { null },
            fetch = { requested++; ok(entry(it, "no", null, 0)) },
        )
        assertNull(lookup.resolve("127.0.0.1"))
        assertNull(lookup.resolve("10.0.0.5"))
        assertNull(lookup.resolve("192.168.1.1"))
        assertEquals(0, requested)
    }

    @Test fun aBlankOrUnusableAddressIsRefusedWithoutAnyRequest() = runTest {
        var requested = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { null },
            fetch = { requested++; ok(entry(it, "no", null, 0)) },
        )
        assertNull(lookup.resolve(null))
        assertNull(lookup.resolve(""))
        assertNull(lookup.resolve("localhost"))
        assertNull(lookup.resolve("not a host"))
        assertEquals(0, requested)
    }

    @Test fun aHostnameResolvesThroughTheSharedPublicIpPath() = runTest {
        var asked = ""
        val lookup = ServerFlaggedLookup(
            publicIpOf = { host -> if (host == "node.example.com") "1.2.3.4" else null },
            fetch = { ip -> asked = ip; ok(entry(ip, "yes", "VPN", 70)) },
        )
        val verdict = lookup.resolve("node.example.com")
        assertEquals(FlagStatus.FLAGGED, verdict?.status)
        assertEquals("1.2.3.4", asked)
    }

    @Test fun onlyThePublicIpReachesTheProvider() = runTest {
        val seen = mutableListOf<String>()
        val lookup = ServerFlaggedLookup(
            publicIpOf = { "1.2.3.4" },
            fetch = { ip -> seen += ip; ok(entry(ip, "no", null, 0)) },
        )
        lookup.resolve("secret-node-name.example.com")
        assertEquals(listOf("1.2.3.4"), seen)
    }

    @Test fun aVerdictIsCachedAndForceRefreshReQueries() = runTest {
        var calls = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { "1.2.3.4" },
            fetch = { ip -> calls++; ok(entry(ip, "no", null, 0)) },
        )
        assertEquals(FlagStatus.CLEAN, lookup.resolve("1.2.3.4")?.status)
        assertEquals(FlagStatus.CLEAN, lookup.resolve("1.2.3.4")?.status)
        assertEquals(1, calls)

        // A manual refresh must hit the provider again, not replay a verdict up to a day old.
        lookup.resolve("1.2.3.4", forceRefresh = true)
        assertEquals(2, calls)
    }

    @Test fun anExpiredVerdictIsFetchedAgain() = runTest {
        var now = 1_000L
        var calls = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { "1.2.3.4" },
            fetch = { ip -> calls++; ok(entry(ip, "no", null, 0)) },
            nowMillis = { now },
        )
        lookup.resolve("1.2.3.4")
        assertEquals(1, calls)
        now += 86_400_001L
        lookup.resolve("1.2.3.4")
        assertEquals(2, calls)
    }

    @Test fun aFailedLookupIsRetriedAfterTheShortTtl() = runTest {
        var now = 1_000L
        var calls = 0
        val lookup = ServerFlaggedLookup(
            publicIpOf = { "1.2.3.4" },
            fetch = { calls++; if (calls == 1) null else ok(entry(it, "no", null, 0)) },
            nowMillis = { now },
        )
        assertNull(lookup.resolve("1.2.3.4"))
        now += 299_000L
        assertNull(lookup.resolve("1.2.3.4"))
        assertEquals(1, calls)
        now += 2_000L
        assertEquals(FlagStatus.CLEAN, lookup.resolve("1.2.3.4")?.status)
        assertEquals(2, calls)
    }

    @Test fun aProviderFailureFailsClosedRatherThanClaimingClean() = runTest {
        val lookup = ServerFlaggedLookup(
            publicIpOf = { "1.2.3.4" },
            fetch = { """{"status":"denied"}""" },
        )
        assertNull(lookup.resolve("1.2.3.4"))
    }

    @Test fun theCacheStaysBounded() = runTest {
        val lookup = ServerFlaggedLookup(
            publicIpOf = { it },
            fetch = { ok(entry(it, "no", null, 0)) },
            cacheLimit = 4,
        )
        for (i in 1..12) lookup.resolve("1.2.3.$i")
        assertTrue("cache must not grow past its limit, was ${lookup.cacheSize}", lookup.cacheSize <= 4)
    }
}
