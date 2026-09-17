package com.v2ray.ang.dto

import com.v2ray.ang.handler.ProfileCountry

data class IPAPIInfo(
    var success: Boolean? = null,
    var ip: String? = null,
    var clientIp: String? = null,
    var ip_addr: String? = null,
    var query: String? = null,
    var country: String? = null,
    var country_name: String? = null,
    var country_code: String? = null,
    var countryCode: String? = null,
    var location: LocationBean? = null
) {
    /** Accept all existing provider formats, but only return a supported country. */
    fun normalizedCountry(): String? = sequenceOf(
        country_code, countryCode, location?.country_code, country, country_name
    ).mapNotNull { ProfileCountry.normalize(it) }.firstOrNull()

    data class LocationBean(
        var country_code: String? = null
    )
}