package com.v2ray.ang.handler

import com.v2ray.ang.dto.RealPingSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch test used to take one sample per server and sort by it. One sample is a coin toss: a
 * server that was briefly busy sorts above a clean one, and a server that missed its single
 * attempt is reported as dead when it is merely slow to start.
 */
class RealPingSampleTest {

    @Test
    fun `median of three samples is the middle one, not the mean`() {
        // mean is 100; the middle sample is 20. Averaging hides a bad sample behind a good one.
        val stats = RealPingSample.summarize(listOf(10L, 20L, 180L))
        assertEquals(20L, stats.delayMillis)
    }

    @Test
    fun `a single slow sample does not move the median`() {
        val stats = RealPingSample.summarize(listOf(50L, 52L, 900L))
        assertEquals(52L, stats.delayMillis)
    }

    @Test
    fun `jitter is the spread between the fastest and the slowest sample`() {
        val stats = RealPingSample.summarize(listOf(100L, 110L, 160L))
        assertEquals(60L, stats.jitterMillis)
    }

    @Test
    fun `loss counts the samples that never answered`() {
        // -1 is how a failed measurement is reported by the core
        val stats = RealPingSample.summarize(listOf(100L, -1L, -1L, 120L))
        assertEquals(50, stats.lossPercent)
    }

    @Test
    fun `every sample failing is a failure, not a median of zero`() {
        val stats = RealPingSample.summarize(listOf(-1L, -1L, -1L))
        assertEquals(-1L, stats.delayMillis)
        assertEquals(100, stats.lossPercent)
    }

    @Test
    fun `no samples at all is a failure`() {
        val stats = RealPingSample.summarize(emptyList())
        assertEquals(-1L, stats.delayMillis)
        assertEquals(100, stats.lossPercent)
    }

    @Test
    fun `an even number of samples still reports a real sample as the delay`() {
        val stats = RealPingSample.summarize(listOf(10L, 20L, 30L, 40L))
        assertTrue("delay must come from a sample, not an average of two", stats.delayMillis in listOf(20L, 30L))
    }

    @Test
    fun `a stable server outranks a faster but jumpy one`() {
        val stable = RealPingSample.summarize(listOf(150L, 152L, 148L))
        val jumpy = RealPingSample.summarize(listOf(60L, 700L, 60L))
        assertTrue(
            "stable 150ms should beat jumpy 60ms, got ${stable.score} vs ${jumpy.score}",
            stable.score > jumpy.score
        )
    }

    @Test
    fun `score is zero for a server that never answered`() {
        val dead = RealPingSample.summarize(listOf(-1L, -1L, -1L))
        assertEquals(0, dead.score)
    }

    @Test
    fun `loss outweighs a good delay`() {
        val lossy = RealPingSample.summarize(listOf(100L, -1L, -1L))
        val clean = RealPingSample.summarize(listOf(200L, 210L, 205L))
        assertTrue("a 33% lossy 100ms should not beat a clean 200ms", clean.score > lossy.score)
    }

    @Test
    fun `score falls as jitter rises at the same delay`() {
        val calm = RealPingSample.summarize(listOf(100L, 101L, 99L))
        val rough = RealPingSample.summarize(listOf(50L, 150L, 100L))
        assertTrue(calm.score > rough.score)
    }
}
