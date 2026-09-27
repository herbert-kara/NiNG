package com.v2ray.ang.handler

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A country probe must never answer from the connection the user is already running.
 *
 * The probe stands a profile up on a port of its own and asks an IP service from inside it. The
 * failure this rules out is quiet and wrong rather than loud: a probe bound to the port the running
 * connection listens on does not fail, it answers -- with the country of whichever server the user
 * is connected to. Every row in the list then shows that one country, and each of them looks
 * correct, because it really is a country and really is a flag.
 *
 * So the probe takes a free port and the config builder takes an override, and the override is
 * never the port the app itself listens on. A probe that shared the port would not be caught by
 * any test about what it returns, because what it returns would look right.
 */
class ExitCountryProbePortTest {

    private val probe = File("src/main/java/com/v2ray/ang/handler/ExitCountryProbe.kt")
    private val config = File("src/main/java/com/v2ray/ang/core/CoreConfigManager.kt")

    @Test
    fun `the probe takes a free port of its own rather than a fixed one`() {
        assertTrue(
            "the probe source must be readable from the module the test runs in: " + probe,
            probe.exists(),
        )
        val text = probe.readText()
        assertTrue(
            "a probe bound to a fixed port collides with whatever already holds it, and the row " +
                "then gets no answer at all; a free port per probe is what keeps concurrent probes " +
                "from taking each other ports",
            text.contains("Utils.findRandomFreePort()"),
        )
        assertTrue(
            "no probe may listen on a hard-coded port: every listener the probe opens has to come " +
                "from the free-port search",
            !Regex("""InetSocketAddress\(LOOPBACK,\s*\d""").containsMatchIn(text),
        )
    }

    @Test
    fun `the config builder takes the port from the override when one is set`() {
        assertTrue("the config source must be readable: " + config, config.exists())
        val text = config.readText()
        assertTrue(
            "the SOCKS inbound must take the override first and the app setting only when there is " +
                "none, otherwise a probe is served by the port the running connection owns",
            text.contains("socksPortOverride ?: SettingsManager.getSocksPort()"),
        )
        assertTrue(
            "the override has to be internal so a probe can set it and nothing else has to",
            text.contains("internal var socksPortOverride: Int? = null"),
        )
    }

    @Test
    fun `the override is cleared however the probe ends`() {
        val text = probe.readText()
        assertTrue(
            "a probe that clears its override only on the success path leaves the next config " +
                "built on a port that belongs to a core which has already gone away",
            text.contains("} finally {"),
        )
        val cleared = text.split("socksPortOverride = null").size - 1
        assertTrue(
            "the override has to be cleared in the finally, and that is the only place that counts: " +
                "found $cleared clearings",
            cleared == 1,
        )
    }

    @Test
    fun `a probe never claims the device network`() {
        val text = probe.readText()
        assertTrue(
            "the probe starts the loop with a zero tun descriptor so it comes up on its SOCKS " +
                "listener alone; a real descriptor would make it fight the running connection for " +
                "the device network and both would fail",
            text.contains("startLoop(result.content, 0)"),
        )
    }

    @Test
    fun `the probe logs no address, port, name or answer`() {
        val text = probe.readText()
        for leak in listOf("profile.server", "profile.serverPort", "profile.remark", "guid") {
            // guid is the handle the profile is stored under and is not a credential, but it is
            // not needed for a diagnosis either, so it stays out of the log.
            val inLog = text.lines().any { line ->
                line.contains("LogUtil") && line.contains(leak)
            }
            assertTrue(
                "the probe must not put $leak in a log line: a probe runs per row, so one line " +
                    "leaks every server in the list",
                !inLog,
            )
        }
    }
}
