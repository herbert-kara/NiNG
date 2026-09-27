package com.v2ray.ang.ui.main

import java.util.UUID

/** Main-thread request ownership; a late reply must not complete a newer test. */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private var current: String? = null

    /**
     * The server the current single test is running on, kept so a late reply can be attributed.
     *
     * The request id is a random UUID and identifies the test, not the row. A result carries the
     * country the connection exited in -- the only location reading that is about the server
     * actually reached rather than about whatever a name resolves to -- and putting that on a row
     * is impossible without knowing which row the test was started for.
     */
    var currentServerGuid: String? = null
        private set
    var bulk: Bulk? = null
        private set
    val isTesting: Boolean get() = current != null || bulk != null

    fun beginCurrent(serverGuid: String? = null): String =
        UUID.randomUUID().toString().also {
            current = it
            currentServerGuid = serverGuid
        }

    /**
     * Completes the current test and returns the server it ran on, or null when the reply belongs
     * to a test that has already been superseded.
     *
     * The two are not the same answer and were once collapsed: a superseded reply and a test that
     * had no selected server both came back as null, so a caller could not tell a dropped result
     * from one with nothing to annotate. [Completed] carries the distinction.
     */
    fun completeCurrent(id: String): Completed? {
        if (id != current) return null
        val guid = currentServerGuid
        current = null
        currentServerGuid = null
        return Completed(guid)
    }

    data class Completed(val serverGuid: String?)

    fun invalidateCurrent() {
        current = null
        currentServerGuid = null
    }

    fun beginBulk(groupId: String): Bulk = Bulk(UUID.randomUUID().toString(), groupId).also { bulk = it }

    fun completeBulk(id: String): Bulk? {
        val request = bulk?.takeIf { it.id == id } ?: return null
        bulk = null
        return request
    }

    fun cancelBulk() {
        bulk = null
    }
}
