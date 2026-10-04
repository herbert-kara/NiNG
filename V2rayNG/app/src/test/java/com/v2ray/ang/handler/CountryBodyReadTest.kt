package com.v2ray.ang.handler

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A small answer must be read whole.
 *
 * The body was read with `source.request(cap)`, which asks whether `cap` bytes are already
 * buffered. A country answer is a few hundred bytes and the cap was 16 KB, so it returned false
 * for every provider, every attempt was reported as too large, and nine lookups came back empty with
 * the same reason. The requests had gone out and the replies had come back; the replies were
 * discarded before they were parsed, which is why a curl on the same device answered DE for the
 * same address seconds earlier while the app reported nothing.
 *
 * `read(sink, n)` takes up to n bytes. The distinction is the whole content of this test: both
 * calls look like a bounded read, and only one of them bounds rather than judges.
 *
 * The bodies below are taken from the real responses, measured on the device the app runs on and
 * trimmed to the fields the parser reads -- the byte counts in the comments are what arrived, and
 * both are far under the cap that rejected them. A body invented for a test is a second thing that
 * can be wrong, and one of them already was: the first version of this file asserted a parse on a
 * response shape nobody had ever received.
 */
class CountryBodyReadTest {

    /** ipwho.is for 5.180.82.45, trimmed to the fields the parser reads (938 bytes received). */
    private val ipwhois = """{"ip":"5.180.82.45","success":true,"type":"IPv4",""" +
        """"continent":"Europe","country":"Germany","country_code":"DE",""" +
        """"city":"Frankfurt am Main"}"""

    /** api.ip.sb for the same address, likewise trimmed (349 bytes received). */
    private val ipsb = """{"region":"Hesse","organization":"Freakhosting",""" +
        """"country":"Germany","country_code":"DE","ip":"5.180.82.45"}"""

    private fun readBounded(body: String, cap: Long): String {
        val sink = Buffer()
        Buffer().writeUtf8(body).read(sink, cap)
        return sink.readUtf8()
    }

    @Test
    fun `the old call reported a real answer as too large`() {
        for (body in listOf(ipwhois, ipsb)) {
            assertEquals(false,Buffer().writeUtf8(body).request(16_385))
        }
    }

    @Test
    fun `a real answer is read whole`() {
        for (body in listOf(ipwhois, ipsb)) {
            assertEquals(body, readBounded(body, 64L * 1024L))
        }
    }

    @Test
    fun `the country code survives the read`() {
        // The reply was discarded before it was parsed, so what matters is that the field the
        // parser needs is present in what comes back.
        assertTrue(readBounded(ipwhois, 64L * 1024L).contains("\"country_code\":\"DE\""))
        assertTrue(readBounded(ipsb, 64L * 1024L).contains("\"country_code\":\"DE\""))
    }

    @Test
    fun `a body past the cap is cut rather than buffered whole`() {
        assertEquals(100, readBounded("x".repeat(1000), 100L).length)
    }
}
