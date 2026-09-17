package com.v2ray.ang.handler

import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.*
import org.junit.Test

class ProfileCountryTest {
    @Test fun labelHintsAreLocalAndUnknownStaysBlank() {
        assertEquals("DE", ProfileCountry.fromLabel("DE-01"))
        assertEquals("FR", ProfileCountry.fromLabel("France-02"))
        assertEquals("JP", ProfileCountry.fromLabel("🇯🇵 fast"))
        assertEquals("IR", ProfileCountry.fromLabel("ایران 01"))
        assertEquals("GB", ProfileCountry.fromLabel("UK-2"))
        listOf("fast connection", "in the cloud", "node42", "DEsign", "ZZ-1", "", "   ").forEach {
            assertNull(it, ProfileCountry.fromLabel(it))
        }
    }

    @Test fun codesAreValidatedAgainstBundledFlags() {
        assertEquals("GB", ProfileCountry.normalize(" uk "))
        assertEquals("US", ProfileCountry.normalize("United States"))
        assertNull(ProfileCountry.normalize("ZZ"))
        assertNull(ProfileCountry.normalize("../../us"))
        assertNull(ProfileCountry.normalize(null))
    }

    @Test fun existingIpApiFormatsShareCountryValidation() {
        assertEquals("DE", IPAPIInfo(country = "Germany").normalizedCountry())
        assertEquals("JP", IPAPIInfo(country_code = "invalid", countryCode = "JP").normalizedCountry())
        assertEquals("FR", IPAPIInfo(country_name = "France").normalizedCountry())
        assertEquals("CA", IPAPIInfo(location = IPAPIInfo.LocationBean("ca")).normalizedCountry())
        assertNull(IPAPIInfo(country_code = "ZZ").normalizedCountry())
    }

    @Test fun legacyCountryMetadataDoesNotChangeDuplicateIdentity() {
        val profile = ProfileItem(configType = EConfigType.VLESS, server = "example.com")
        assertEquals(profile.duplicateIdentity(), profile.copy(countryCode = "DE").duplicateIdentity())
        assertNotEquals(profile.duplicateIdentity(), profile.copy(server = "other.example").duplicateIdentity())
    }
}
