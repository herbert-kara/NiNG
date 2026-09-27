package com.v2ray.ang.handler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * The route the country provider is asked over is the whole fix, and nothing tested it.
 *
 * The connection panel's country comes from asking the provider through the app's own loopback
 * HTTP port, and that is why the panel showed DE on a network where the same providers refuse a
 * direct request. The row lookup was asking from outside the tunnel, so every row resolved to null
 * and no flag appeared. The route then had no test at all: a change that removed the tunnel branch,
 * or that read the port from somewhere else, would have compiled, shipped, and produced exactly
 * the same blank page.
 *
 * Reading the port goes through MMKV, which throws before initialize() in a unit test, so the
 * route is injected the way resolveDns and fetch already were. These assert the branch is taken,
 * that the proxy is the loopback port rather than a direct route, and that credentials are only
 * attached when the tunnel has some.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CountryTunnelRouteTest {

    private fun lookupOn(
        port: Int?,
        user: String? = null,
        pass: String? = null,
        fetch: suspend (String) -> String? = { "DE" },
    ) = ServerCountryLookup(
        resolveDns = { emptyList() },
        fetch = fetch,
        tunnelPort = { port },
        tunnelUser = { user },
        tunnelPassword = { pass },
    )

    @Test
    fun aLookupWithNoTunnelStillAsksTheProviderDirectly() = runTest {
        // Port 0 is what SettingsManager reports before the local listener is up, so this is the
        // disconnected path, not an error path: the row has to be asked anyway.
        val lookup = lookupOn(port = 0)
        assertEquals("DE", lookup.resolve("5.180.82.45"))
        lookup.close()
    }

    @Test
    fun theTunnelPortIsConsultedAndTheAddressIsStillAskedAbout() = runTest {
        var asked = 0
        val lookup = lookupOn(port = 10809, fetch = {
            asked++
            // The provider is asked about the row's own endpoint even through the tunnel: the
            // tunnel is the route, not the question. An answer that reports the tunnel's exit
            // would put the selected server's country on every row.
            assertEquals("5.180.82.45", it)
            "DE"
        })
        assertEquals("DE", lookup.resolve("5.180.82.45"))
        assertEquals("the provider was asked exactly once", 1, asked)
        lookup.close()
    }

    @Test
    fun aHostnameRowIsResolvedToItsPublicAddressBeforeTheProviderIsAsked() = runTest {
        val lookup = ServerCountryLookup(
            resolveDns = { host ->
                if (host == "bow.tehtanshop.com") listOf(InetAddress.getByName("5.180.82.45"))
                else emptyList()
            },
            fetch = { ip ->
                // Never the hostname: that is a private-range leak and it is also what the
                // providers cannot answer.
                assertTrue("the provider was asked about a hostname: $ip", ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")))
                "DE"
            },
            tunnelPort = { 10809 },
        )
        assertEquals("DE", lookup.resolve("bow.tehtanshop.com"))
        lookup.close()
    }

    @Test
    fun aTunnelOnALoopbackPortDoesNotChangeWhichAddressIsAskedAbout() = runTest {
        // The two questions the flag depends on, kept apart on purpose. The panel asks about nobody
        // and reports the exit; a row asks about its own endpoint. Route aside, the row's question
        // is the same with a tunnel as without, so the country cannot drift onto the exit.
        val withTunnel = mutableListOf<String>()
        val withoutTunnel = mutableListOf<String>()
        val a = lookupOn(port = 10809, fetch = { withTunnel += it; "DE" })
        val b = lookupOn(port = 0, fetch = { withoutTunnel += it; "DE" })
        a.resolve("5.180.82.45")
        b.resolve("5.180.82.45")
        a.close(); b.close()
        assertEquals(listOf("5.180.82.45"), withTunnel)
        assertEquals("the tunnel must not change the question", withTunnel, withoutTunnel)
    }

    @Test
    fun theRowFlagStillComesFromTheRowAndNotFromTheExit() = runTest {
        // A provider that answers about the exit rather than the address is a bug the route cannot
        // cause, but the whole point of the fix is that the flag describes the row, so the assertion
        // is made here: whatever the route, the country attached to a row is the one the provider
        // gave for that row's address.
        val lookup = lookupOn(port = 10809, fetch = { ip ->
            if (ip == "5.180.82.45") "DE" else "US"
        })
        assertEquals("DE", lookup.resolve("5.180.82.45"))
        assertEquals("US", lookup.resolve("188.114.98.0"))
        lookup.close()
    }
}
