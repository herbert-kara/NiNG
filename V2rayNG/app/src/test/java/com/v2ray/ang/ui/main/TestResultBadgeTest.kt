package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The row used to show one number, the delay. That number cannot distinguish a server that answers
 * in 150ms every time from one that answers in 60ms when it feels like it, and the second is the
 * one that breaks a call.
 */
class TestResultBadgeTest {

    /**
     * What goes after the delay in the row. Mirrors testResultSuffix in MainServerPager.kt, which
     * the guard in verify_brand.py checks still agrees with this.
     *
     * Returns the jitter and the share of samples that answered, or nulls when the run did not
     * measure them.
     */
    fun parts(
        delayMillis: Long,
        jitterMillis: Long,
        lossPercent: Int,
    ): Pair<String?, String?> {
        if (delayMillis == 0L) return null to null
        if (jitterMillis < 0L) return null to null
        val jitter = if (jitterMillis > 0L) "±$jitterMillis" else null
        val loss = if (lossPercent > 0) "$lossPercent%" else "100%"
        return jitter to loss
    }

    @Test
    fun `an untested row shows nothing at all`() {
        assertEquals(null to null, parts(0L, -1L, -1))
    }

    @Test
    fun `a row from an old single-sample run shows the delay alone`() {
        // the record has no jitter and no loss, so claiming ±0 and 100% would be inventing a
        // measurement that never happened
        assertEquals(null to null, parts(142L, -1L, -1))
    }

    @Test
    fun `a steady server shows the spread and full delivery`() {
        val (jitter, loss) = parts(142L, 8L, 0)
        assertEquals("±8", jitter)
        assertEquals("100%", loss)
    }

    @Test
    fun `a lossy server keeps its loss visible`() {
        val (jitter, loss) = parts(150L, 10L, 33)
        assertEquals("33%", loss)
    }

    @Test
    fun `a dead server is still reported as a loss, not as a healthy row`() {
        val (jitter, loss) = parts(-1L, 0L, 100)
        assertEquals("100%", loss)
    }
}
