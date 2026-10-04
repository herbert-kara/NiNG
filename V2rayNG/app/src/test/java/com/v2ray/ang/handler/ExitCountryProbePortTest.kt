package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        assertTrue(probe.exists())
        val text = probe.readText()
        assertTrue(text.contains("Utils.findRandomFreePort()"))
        assertTrue(!Regex("""InetSocketAddress\(LOOPBACK,\s*\d""").containsMatchIn(text))
    }

    @Test
    fun `the config builder takes the port from the override when one is set`() {
        assertTrue(config.exists())
        val text = config.readText()
        assertTrue(text.contains("socksPortOverride ?: SettingsManager.getSocksPort()"))
        assertTrue(text.contains("internal var socksPortOverride: Int? = null"))
    }

    @Test
    fun `the override is cleared however the probe ends`() {
        val text = probe.readText()
        assertTrue(text.contains("} finally {"))
        val cleared = text.split("socksPortOverride = null").size - 1
        assertTrue(cleared == 1)
    }

    @Test
    fun `a probe never claims the device network`() {
        val text = probe.readText()
        assertTrue(text.contains("startLoop(result.content, 0)"))
    }

    @Test
    fun `the probe logs no address, port, name or answer`() {
        val text = probe.readText()
        for (secretPart in listOf("profile.server", "profile.serverPort", "profile.remark", "guid")) {
            // guid is the handle the profile is stored under and is not a credential, but it is
            // not needed for a diagnosis either, so it stays out of the log.
            val inLog = text.lines().any { line ->
                line.contains("LogUtil") && line.contains(secretPart)
            }
            assertTrue(!inLog)
        }
    }
}
