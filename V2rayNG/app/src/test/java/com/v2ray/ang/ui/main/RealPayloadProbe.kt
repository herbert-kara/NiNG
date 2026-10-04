package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.ProfileCountry
import com.v2ray.ang.handler.ServerCountryLookup
import com.v2ray.ang.handler.ServerFlaggedLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A probe over the real parse path with the payload the live provider actually returns, kept as a
 * test so a change to the parser cannot silently stop reading the field the row depends on.
 */
class RealPayloadProbe {

    private val live = """
    {
        "status": "ok",
        "188.114.97.6": {
            "asn": "AS13335",
            "provider": "CLOUDFLARENET - Cloudflare, Inc., US",
            "continent": "North America",
            "country": "Canada",
            "isocode": "CA",
            "proxy": "no",
            "type": "Business",
            "risk": 0
        }
    }
    """.trimIndent()

    @Test
    fun theRowGetsACountryCodeFromTheLivePayload() {
        val verdict = ServerFlaggedLookup.parseVerdict(live, "188.114.97.6")
        assertNotNull(verdict)
        assertEquals("CA", verdict!!.countryCode)
        assertNotNull(ProfileCountry.flagAsset(verdict.countryCode))
    }

    @Test
    fun aCloudflareEdgeAddressIsAcceptedAsALookupTarget() {
        // The address stored on the profile is what the guard has to accept; a Cloudflare edge
        // literal is the common case and must not be refused as a non-public target.
        assertEquals("188.114.97.6", ServerCountryLookup.canonicalTarget("188.114.97.6"))
        assertEquals("104.21.29.250", ServerCountryLookup.canonicalTarget("104.21.29.250"))
    }

    @Test
    fun theProviderFieldNamesTheRowReadsAreTheOnesTheProviderSends() {
        // ipwho.is spells the code country_code; if a rename ever lands, the flag silently
        // disappears on device while every other test still passes.
        val body = """{"ip":"1.1.1.1","success":true,"country_code":"US"}"""
        assertEquals("US", ServerCountryLookup.parseResponse(body))
    }

    @Test
    fun verdictSurvivesTheRowUpdateThatAttachesIt() {
        val profile = com.v2ray.ang.dto.entities.ProfileItem(
            configType = com.v2ray.ang.enums.EConfigType.VLESS,
            server = "188.114.97.6",
            remarks = "x",
        )
        val row = ServerRowUiModel(
            guid = "g1",
            profile = profile,
            remarks = "x",
            statistics = "",
            typeDescription = "",
            testDelayMillis = -1,
            subscriptionBadge = "",
        )
        val updated = applyServerFlag(listOf(row), "g1", "188.114.97.6",
            com.v2ray.ang.handler.FlagStatus.CLEAN, "CA")
        assertEquals(1, updated.size)
        assertEquals("CA", updated[0].serverCountryCode)
        assertTrue(ProfileCountry.flagAsset(updated[0].serverCountryCode) != null)
    }
}
