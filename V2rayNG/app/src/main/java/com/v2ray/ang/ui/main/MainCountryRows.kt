package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ProfileCountry

/**
 * Fill the location flag for a row the verdict did not cover. The verdict is authoritative: it
 * was asked about this exact address, so a second provider must not overwrite its answer with a
 * different country for the same server.
 */
internal fun applyServerCountry(
    rows: List<ServerRowUiModel>,
    guid: String,
    address: String,
    country: String?,
): List<ServerRowUiModel> {
    val code = ProfileCountry.normalize(country) ?: return rows
    return rows.map {
        if (it.guid == guid && it.profile.server == address && it.serverCountryCode == null) {
            it.copy(serverCountryCode = code)
        } else {
            it
        }
    }
}

/**
 * Attach a reputation verdict and the country it reported to one row, resolved again by GUID and
 * address: a verdict for an edited or replaced address must not annotate the new row that took
 * its place.
 *
 * [country] comes from the same verdict response rather than from a separate lookup. The location
 * flag has to describe the main server the profile actually connects to, which is exactly the
 * address the reputation service was asked about; a name-based hint is not that address.
 */
internal fun applyServerFlag(
    rows: List<ServerRowUiModel>,
    guid: String,
    address: String,
    status: FlagStatus,
    country: String? = null,
): List<ServerRowUiModel> {
    val code = ProfileCountry.normalize(country)
    return rows.map {
        if (it.guid == guid && it.profile.server == address) {
            it.copy(flagStatus = status, serverCountryCode = code ?: it.serverCountryCode)
        } else {
            it
        }
    }
}

/** Exit results belong to the running connection, never to a profile's ingress or label. */
internal fun exitCountryCode(status: MainStatus): String? =
    (status as? MainStatus.ConnectionTest)?.result?.takeIf { it.delayMillis >= 0 }
        ?.country?.let(ProfileCountry::normalize)
