package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A measured exit country belongs to the row that was tested, and it replaces what DNS said.
 *
 * The row used to be filled by resolving the name of the server and asking a geoip service about the
 * address that came back. Behind a CDN, a relay or any other intermediary that address is the
 * one belonging to the intermediary, so the flag was a real flag for the wrong machine -- correct-looking and wrong.
 *
 * The measurement has no such ambiguity. The core was started on that profile, the request went
 * out through it, and the country came back from where the traffic actually emerged. That is the
 * same reading the connection panel shows, and it is the only one that describes the server the
 * user would connect to.
 */
class MeasuredCountryTest {

    @Test
    fun `the request queue remembers which server is under test`() {
        val queue = MainTestRequests()
        val id = queue.beginCurrent("guid-1")
        assertEquals("guid-1", queue.currentServerGuid)
        assertEquals("guid-1", queue.completeCurrent(id)?.serverGuid)
        assertNull("the queue is empty after completing, so nothing is left to attribute",
            queue.currentServerGuid)
    }

    @Test
    fun `a late reply does not take the server of the newer test`() {
        val queue = MainTestRequests()
        val stale = queue.beginCurrent("guid-1")
        val fresh = queue.beginCurrent("guid-2")
        assertNull("a reply for a superseded test must not complete the current one",
            queue.completeCurrent(stale))
        assertEquals("guid-2", queue.currentServerGuid)
        assertEquals("guid-2", queue.completeCurrent(fresh)?.serverGuid)
    }

    @Test
    fun `invalidating clears the server too`() {
        val queue = MainTestRequests()
        queue.beginCurrent("guid-1")
        queue.invalidateCurrent()
        assertNull("a cancelled test leaves nothing to attribute a late reply to",
            queue.currentServerGuid)
    }

    @Test
    fun `no selected server and a superseded reply are different answers`() {
        val queue = MainTestRequests()
        val id = queue.beginCurrent(null)
        // The test completed, so the delay is still published; only the country has no row to go
        // on. That is not the same as a reply that belongs to a test already replaced, which must
        // publish nothing at all -- so the two answers cannot both be null.
        val completed = queue.completeCurrent(id)
        assertEquals("a completed test is never a dropped reply", true, completed != null)
        assertNull(completed?.serverGuid)

        val other = MainTestRequests()
        val stale = other.beginCurrent("guid-1")
        other.beginCurrent("guid-2")
        assertNull("a superseded reply is dropped entirely",
            other.completeCurrent(stale))
    }
}
