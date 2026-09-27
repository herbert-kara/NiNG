package com.v2ray.ang.handler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * A rows country must not depend on whether the tunnel happens to be listening.
 *
 * This was routed through the apps own loopback HTTP port, to match the connection panels (DE)
 * line. That was the wrong thing to copy. The panel asks the provider *without* an address, so it
 * can only learn the answer by leaving through the tunnel the user is connected to. This lookup
 * asks *with* the address -- the {ip} of the endpoint of the row -- so the answer is that endpoint's
 * country whichever route carries the question.
 *
 * Copying the panels route made every lookup fail with ConnectException whenever the service was
 * not listening, which is to say whenever the user had not connected yet: the one moment a flag is
 * most wanted. The flag did not fail on a blocked network. It failed on a disconnected one, and no
 * amount of provider work would have changed that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CountryRouteTest {

    private fun lookup(
        resolveDns: (suspend (String) -> List<InetAddress>)? = null,
        fetch: (suspend (String) -> String?) = { null },
    ) = ServerCountryLookup(resolveDns = resolveDns, fetch = fetch)

    @Test
    fun `a row resolves with no tunnel and no connection`() = runTest {
        val seen = mutableListOf<String>()
        val subject = lookup(
            resolveDns = { listOf(InetAddress.getByName("5.180.82.45")) },
            fetch = { url -> seen.add(url); "DE" },
        )
        subject.use {
            assertEquals("DE", it.resolve("server.example"))
        }
        assertEquals(1, seen.size)
    }

    /**
     * The provider is asked about the address of the row, not about the caller.
     *
     * This is what makes the direct route correct rather than merely working: the question carries
     * the address, so the answer belongs to the row whichever network carried the request. A
     * lookup that dropped the address would return the callers country, which is the panel's
     * question, not this one's.
     */
    @Test
    fun `the provider is asked about the address of the row`() = runTest {
        val asked = mutableListOf<String>()
        val subject = lookup(
            resolveDns = { listOf(InetAddress.getByName("5.180.82.45")) },
            fetch = { url -> asked.add(url); "DE" },
        )
        subject.use { it.resolve("server.example") }
        assertEquals(1, asked.size)
        assertTrue(
            "the provider must be asked about the resolved address, not left to guess: " + asked[0],
            asked[0].contains("5.180.82.45"),
        )
    }

    /**
     * There is no route seam left, so this cannot be reintroduced by accident.
     *
     * The tunnel arrived as three settings-backed lambdas on the constructor, which is the shape
     * that made it look like a routing choice rather than a dependency on a running service. If the
     * constructor ever grows a port, a credential or a proxy again, the flag will go back to
     * failing while the app is disconnected -- the state a user is in when they look for a flag.
     */
    @Test
    fun `the lookup carries no tunnel seam to reintroduce the dependency`() {
        val constructor = ServerCountryLookup::class.java.constructors
            .maxByOrNull { it.parameterCount }!!
        val parameters: List<String> = constructor.parameters.map { it.name }
        val forbidden: List<String> =
            listOf("tunnelPort", "tunnelUser", "tunnelPassword", "proxy", "tunnel")
        for (name in forbidden) {
            val taken = parameters.filter { it.contains(name, ignoreCase = true) }
            assertTrue(
                "the lookup must not take a " + name + ": it made every lookup fail while the "
                    + "service was not listening, which is exactly when a flag is wanted. "
                    + "Current parameters: " + parameters,
                taken.isEmpty(),
            )
        }
    }
}
