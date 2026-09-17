package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.*
import org.junit.Test

class MainCountryRowsTest {
    private fun row(guid: String = "a", server: String = "8.8.8.8") = ServerRowUiModel(
        guid = guid, profile = ProfileItem(configType = EConfigType.VLESS, server = server),
        remarks = "FR-01", statistics = "", typeDescription = "VLESS", testDelayMillis = 0,
        subscriptionBadge = "", labelCountryCode = "FR"
    )

    @Test fun ingressUpdatePreservesIndependentLabelAndOtherRows() {
        val rows = listOf(row(), row("b"))
        val updated = applyServerCountry(rows, "a", "8.8.8.8", "JP")
        assertEquals("FR", updated[0].labelCountryCode)
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
        assertNull(row().copy(labelCountryCode = null).labelCountryCode)
        assertEquals(listOf(row()), applyServerCountry(listOf(row()), "a", "8.8.8.8", "ZZ"))
    }

    @Test fun exitFlagRequiresSuccessfulCurrentConnectionTest() {
        assertEquals("DE", exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(10, country = "DE"))))
        assertNull(exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(-1, country = "DE"))))
        assertNull(exitCountryCode(MainStatus.ConnectionTest(ConnectionTestResult(10, country = "ZZ"))))
        assertNull(exitCountryCode(MainStatus.Connected))
        assertNull(exitCountryCode(MainStatus.Disconnected))
    }
}
