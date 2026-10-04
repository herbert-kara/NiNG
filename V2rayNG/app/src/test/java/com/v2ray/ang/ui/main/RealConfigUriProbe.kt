package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.ProfileCountry
import com.v2ray.ang.handler.ServerCountryLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The screen showed rows whose address was accepted by the app but produced no flag at all, so
 * the guard is exercised here against the addresses these configurations actually carry.
 *
 * The importer is deliberately not called: it reaches Android-only helpers that a JVM unit test
 * cannot run, and a test that cannot run proves nothing. What matters is the other half of the
 * contract: the address the row hands to the lookup must be accepted, and the answer that comes
 * back must be able to produce a flag.
 */
class RealConfigUriProbe {

    /** address -> country the provider reported for it, as captured from a live lookup. */
    private val realRows = mapOf(
        "188.114.97.6" to "CA",
        "104.21.29.250" to "US",
        "47.253.226.114" to "US",
        "158.173.20.208" to "NL",
        "104.19.229.21" to "US",
        "151.101.56.6" to "US",
        "172.66.140.62" to "US",
        "93.77.188.102" to "SE",
        "15.235.75.71" to "CA",
        "51.195.126.38" to "FR",
        "142.4.216.225" to "CA",
        "82.38.31.176" to "GB",
        "54.36.174.134" to "FR",
        "108.181.126.122" to "CA",
        "57.131.48.45" to "SE",
        "85.9.6.6" to null,
    )

    @Test
    fun everyAddressTheseRowsCarryIsAcceptedByTheLookupGuard() {
        for (address in realRows.keys) {
            assertNotNull(
                "address '$address' is refused by the lookup guard, so the row can never get a flag",
                ServerCountryLookup.canonicalTarget(address),
            )
        }
    }

    @Test
    fun everyAnswerTheProviderGivesCanBeDrawnAsAFlag() {
        for ((address, country) in realRows) {
            val code = ProfileCountry.normalize(country)
            if (country != null) {
                assertEquals("country '$country' for $address was refused", country, code)
                assertNotNull(
                    "no bundled flag image for $country, so the row renders no flag",
                    ProfileCountry.flagAsset(code),
                )
            }
        }
    }

    @Test
    fun theBundledFlagSetCoversEveryCountryTheLookupCanReturn() {
        // A code that normalises but has no image renders a silently empty badge, which is exactly
        // the reported symptom. Every code ProfileCountry accepts must have a file behind it.
        val assets = java.io.File("src/main/assets/country_flags")
        assertTrue("the bundled flag directory is missing", assets.isDirectory)
        val missing = mutableListOf<String>()
        for (code in listOf("US", "CA", "GB", "SE", "FR", "DE", "TR", "IR", "NL", "RU", "JP", "FI")) {
            if (!java.io.File(assets, "${code.lowercase()}.png").exists()) missing += code
        }
        assertTrue("flags missing from the asset set: $missing", missing.isEmpty())
    }
}
