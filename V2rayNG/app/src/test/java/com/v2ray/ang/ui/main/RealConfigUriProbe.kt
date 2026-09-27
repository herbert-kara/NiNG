package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ServerCountryLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Runs the app's own importer and the flag pipeline over the exact configuration URIs in use, so a
 * shape that produces no flag on a device fails here instead of on a screenshot.
 */
class RealConfigUriProbe {

    private fun decode(uri: String): String = URLDecoder.decode(uri, "UTF-8")

    private val realUris: List<Pair<String, (String) -> ProfileItem?>> = listOf(
        "ss://YWVzLTEyOC1nY206c2hhZG93c29ja3M%3D@158.173.20.208:443#US" to ShadowsocksFmt::parse,
        "trojan://humanity@104.19.229.21:443?path=%2F%2Fassignment&security=tls&insecure=0&host=www.calmlunch.com&type=ws&allowInsecure=0&sni=www.calmlunch.com#CA" to TrojanFmt::parse,
        "vless://0f0b7f69-78e1-4e9e-8b35-0986444613e4@188.114.97.6:443?ed=2048&eh=Sec-WebSocket-Protocol&encryption=none&host=xwrivr.pages.dev&path=%2F&security=tls&sni=xwrivr.pages.dev&type=ws#CA" to VlessFmt::parse,
        "vless://055a1ce8-2a16-4a0d-a2c2-22826c9b2413@47.253.226.114:443?encryption=none&flow=xtls-rprx-vision&pbk=Svl81isn16RPAFnjtmYw7A6TPnsEPLHuYYaJht65Rzc&security=reality&sni=www.cloudflare.com&type=tcp#US" to VlessFmt::parse,
    )

    @Test
    fun everyRealUriYieldsAnAddressTheFlagPipelineAccepts() {
        for ((uri, parse) in realUris) {
            val profile = parse(decode(uri))
            assertNotNull("import failed for $uri", profile)
            val address = profile!!.server
            assertNotNull("import produced no server address for $uri", address)
            assertTrue(
                "address '$address' is refused by the lookup guard, so the row can never get a flag",
                ServerCountryLookup.canonicalTarget(address) != null,
            )
        }
    }

    @Test
    fun everyRealUriProducesARowThatIsEligibleForTheFlagPass() {
        for ((uri, parse) in realUris) {
            val profile = parse(decode(uri))!!
            val eligible = profile.server?.takeIf { !profile.configType.isComplexType() }
            assertNotNull("configType=${profile.configType} is filtered out of the flag pass for $uri", eligible)
        }
    }

    @Test
    fun anImportedRowCarriesNoCountryUntilAVerdictArrives() {
        // The row starts blank: there is no label-derived country to fall back on, so a missing
        // verdict must show as an absent flag rather than a wrong one.
        val profile = VlessFmt.parse(decode(realUris[2].first))!!
        val row = buildServerRowUiModel(
            server = ServersCache(guid = "g1", profile = profile, testDelayMillis = -1L),
            subscriptionRemarks = "",
        )
        assertEquals(null, row.serverCountryCode)
        assertEquals(FlagStatus.UNKNOWN, row.flagStatus)
    }
}
