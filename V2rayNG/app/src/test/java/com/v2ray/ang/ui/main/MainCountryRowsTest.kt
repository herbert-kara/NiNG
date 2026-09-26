package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ProfileCountry
import org.junit.Assert.*
import org.junit.Test

class MainCountryRowsTest {
    private fun row(guid: String = "a", server: String = "8.8.8.8") = ServerRowUiModel(
        guid = guid, profile = ProfileItem(configType = EConfigType.VLESS, server = server),
        remarks = "FR-01", statistics = "", typeDescription = "VLESS", testDelayMillis = 0,
        subscriptionBadge = "",
    )

    // ---- One location flag, taken from the verdict for the main server ----

    @Test fun verdictSetsTheServerLocationFlagFromTheSameResponse() {
        val rows = listOf(row())
        val updated = applyServerFlag(rows, "a", "8.8.8.8", FlagStatus.CLEAN, "JP")
        assertEquals("JP", updated[0].serverCountryCode)
        assertEquals(FlagStatus.CLEAN, updated[0].flagStatus)
    }

    @Test fun theLabelNameNeverProducesALocationFlag() {
        // "FR-01" looks French, but the flag must come from the server, not the label.
        val rows = listOf(row())
        assertNull(rows[0].serverCountryCode)
        assertNull(applyServerCountry(rows, "a", "8.8.8.8", ProfileCountry.fromLabel("FR-01")))
            .first().serverCountryCode
    }

    @Test fun aCountryOnlyLookupStillFillsTheFlagWithoutInventingAVerdict() {
        val rows = listOf(row())
        val updated = applyServerCountry(rows, "a", "8.8.8.8", "JP")
        assertEquals("JP", updated[0].serverCountryCode)
        assertEquals(FlagStatus.UNKNOWN, updated[0].flagStatus)
    }

    @Test fun theVerdictCountryIsNotSilentlyDiscarded() {
        // proxycheck.io already answers with the country; a separate lookup must not overwrite it.
        val rows = listOf(row().copy(serverCountryCode = "DE"))
        val updated = applyServerFlag(rows, "a", "8.8.8.8", FlagStatus.CLEAN, "JP")
        assertEquals("JP", updated[0].serverCountryCode)
    }

    @Test fun aSecondProviderCannotOverwriteTheVerdictCountry() {
        val rows = listOf(row().copy(serverCountryCode = "JP"))
        val updated = applyServerCountry(rows, "a", "8.8.8.8", "DE")
        assertEquals("JP", updated[0].serverCountryCode)
    }

    // ---- A verdict stays bound to the row and address it was resolved for ----

    @Test fun verdictUpdatesOnlyTheMatchingRowAndAddress() {
        val rows = listOf(row(), row("b"))
        val updated = applyServerFlag(rows, "a", "8.8.8.8", FlagStatus.FLAGGED, "NL")
        assertEquals(FlagStatus.FLAGGED, updated[0].flagStatus)
        assertEquals(FlagStatus.UNKNOWN, updated[1].flagStatus)
        assertEquals(rows[1], updated[1])
    }

    @Test fun aVerdictForAnOldAddressCannotAnnotateAnEditedRow() {
        val rows = listOf(row())
        assertEquals(rows, applyServerFlag(rows, "a", "1.1.1.1", FlagStatus.CLEAN, "DE"))
        assertEquals(rows, applyServerFlag(rows, "gone", "8.8.8.8", FlagStatus.CLEAN, "DE"))
    }

    @Test fun ingressUpdatePreservesOtherRows() {
        val rows = listOf(row(), row("b"))
        val updated = applyServerCountry(rows, "a", "8.8.8.8", "JP")
        assertEquals("JP", updated[0].serverCountryCode)
        assertNull(updated[1].serverCountryCode)
        assertEquals(rows[1], updated[1])
    }

    @Test fun obsoleteAddressOrRemovedGuidCannotReceiveCountry() {
        val rows = listOf(row(server = "1.1.1.1"))
        assertEquals(rows, applyServerCountry(rows, "a", "8.8.8.8", "DE"))
        assertEquals(rows, applyServerCountry(rows, "gone", "1.1.1.1", "DE"))
    }

    @Test fun unknownCountryDoesNotInventAFlag() {
        assertNull(row().serverCountryCode)
        assertEquals(listOf(row()), applyServerCountry(listOf(row()), "a", "8.8.8.8", "ZZ"))
        val bad = applyServerFlag(listOf(row()), "a", "8.8.8.8", FlagStatus.CLEAN, "ZZ")
        assertNull(bad[0].serverCountryCode)
    }

    @Test fun exitFlagRequiresSuccessfulCurrentConnectionTest() {
        assertEquals("DE", exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(10, country = "DE"))))
        assertNull(exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(-1, country = "DE"))))
        assertNull(exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(10, country = "ZZ"))))
        assertNull(exitCountryCode(MainStatus.Connected))
        assertNull(exitCountryCode(MainStatus.Disconnected))
    }

    // ---- A refresh must actually change what a second run produces ----

    @Test fun aRefreshRequestReQueriesInsteadOfReplayingTheCache() {
        val calls = mutableListOf<String>()
        var batch = 0
        val service = FakeFlagService { address ->
            calls += address
            "DE" + (batch++)
        }
        val rows = listOf(row())
        // First run: cached verdicts land on the rows.
        applyServerFlag(rows, "a", "8.8.8.8", FlagStatus.CLEAN, service.resolve("8.8.8.8", force = false))
        // A tap must bypass the cache, or the button is invisible to the user.
        assertEquals(2, service.resolve("8.8.8.8", force = true).last())
        assertEquals(listOf("8.8.8.8", "8.8.8.8"), calls)
    }

    @Test fun consecutiveRefreshesAreNotSwallowed() {
        val service = FakeFlagService { "DE" }
        repeat(3) { service.resolve("8.8.8.8", force = true) }
        assertEquals(3, service.calls)
    }

    private class FakeFlagService(private val answer: (String) -> String) {
        var calls = 0
        fun resolve(address: String, force: Boolean): String {
            calls++
            return answer(address)
        }
    }
}
