package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ServerFlaggedLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * The country flag stayed empty on a real page while the address itself was perfectly
 * geolocatable: an entry for the same address came back with `isocode=DE` from the provider.
 *
 * The verdict carried a country and the row still rendered nothing, so the value was being lost
 * between the lookup and the row. These pin that every step keeps it, starting from the provider's
 * real response shape rather than a hand-written one.
 */
class ServerFlagSlotCountryTest {

    /** The provider's actual payload for a flagged residential entry. */
    private val realResponse = """
        {
          "status": "ok",
          "5.180.82.45": {
            "ip": "5.180.82.45",
            "isocode": "DE",
            "country": "Germany",
            "city": "Frankfurt am Main",
            "proxy": "yes",
            "type": "Residential",
            "risk": 66,
            "asn": "AS24940"
          }
        }
    """.trimIndent()

    @Test
    fun theProviderPayloadIsTurnedIntoAVerdictThatCarriesTheCountry() {
        val verdict = ServerFlaggedLookup.parseVerdict(realResponse, "5.180.82.45")
        assertNotNull(verdict)
        assertEquals("DE", verdict!!.countryCode)
        assertEquals(FlagStatus.FLAGGED, verdict.status)
    }

    @Test
    fun aLowercaseCountryCodeStillProducesAFlagAsset() {
        val verdict = ServerFlaggedLookup.parseVerdict(
            realResponse.replace("\"DE\"", "\"de\""), "5.180.82.45",
        )
        assertEquals("DE",verdict?.countryCode)
    }

    @Test
    fun theFlagAssetExistsForTheCountryTheProviderNames() {
        val verdict = ServerFlaggedLookup.parseVerdict(realResponse, "5.180.82.45")
        // The slot renders nothing when there is no asset, so a missing asset is a blank flag
        // even though the lookup succeeded. This is the step that turns a country into a picture.
        assertNotNull(com.v2ray.ang.handler.ProfileCountry.flagAsset(verdict?.countryCode))
    }

    @Test
    fun theProvidersErrorShapeDoesNotInventACountry() {
        // What the provider returns for a hostname, and for the sinkhole range a fake-ip
        // resolver hands back: an error body with no per-address entry.
        val error = """{"status":"error","198.18.0.227":"Invalid IP address"}"""
        assertEquals(null, ServerFlaggedLookup.parseVerdict(error, "198.18.0.227")?.countryCode)
    }
}
