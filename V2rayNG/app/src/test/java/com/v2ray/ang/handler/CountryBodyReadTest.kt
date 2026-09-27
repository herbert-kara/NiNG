package com.v2ray.ang.handler

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A small answer must be read whole.
 *
 * The body was read with `source.request(cap)`, which asks whether `cap` bytes are already
 * buffered. A country answer is a few hundred bytes, so every one of them came back false and every
 * provider was reported as too large on arrival. That is why nine lookups produced nine misses
 * with the same reason while the same providers answered a curl on the same device moments earlier:
 * the request reached them, the reply came back, and the reply was discarded before it was parsed.
 *
 * `read(sink, n)` takes up to n bytes. The distinction is the whole content of this test: both
 * calls look like a bounded read, and only one of them bounds rather than judges.
 */
class CountryBodyReadTest {

    private fun readBounded(body: String, cap: Long): String {
        val buffer = Buffer()
        Buffer().writeUtf8(body).read(buffer, cap)
        return buffer.readUtf8()
    }

    @Test
    fun `an answer far smaller than the cap is read whole`() {
        // What ipwho.is actually returns for 5.180.82.45, trimmed to the parts that matter.
        val body = """{"ip":"5.180.82.45","success":true,"country":"Germany","country_code":"DE"}"""
        val read = readBounded(body, 64L * 1024L)
        assertEquals(body, read)
        assertEquals("DE", ProfileCountry.normalize(read))
    }

    @Test
    fun `the old call reported a small answer as too large`() {
        val body = """{"ip":"5.180.82.45","country_code":"DE"}"""
        // request() is the call that shipped: it answers "are N bytes buffered", so a short body
        // fails it. Kept here as the statement of what the bug was, not as a live dependency.
        val requestSatisfied = Buffer().writeUtf8(body).request(16_385)
        assertEquals(false, requestSatisfied)
        assertEquals("DE", ProfileCountry.normalize(readBounded(body, 64L * 1024L)))
    }

    @Test
    fun `a body past the cap is cut rather than buffered whole`() {
        val body = "x".repeat(1000)
        val read = readBounded(body, 100L)
        assertEquals(100, read.length)
    }
}
