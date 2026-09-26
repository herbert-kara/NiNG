package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ProfileCountry

/** Resolve again by GUID and address: an old lookup must not annotate an edited/replaced row. */
internal fun applyServerCountry(
    rows: List<ServerRowUiModel>,
    guid: String,
    address: String,
    country: String?,
): List<ServerRowUiModel> {
    val code = ProfileCountry.normalize(country) ?: return rows
    return rows.map {
        if (it.guid == guid && it.profile.server == address) it.copy(serverCountryCode = code) else it
    }
}

/**
 * Attach a reputation verdict to one row, resolved again by GUID and address: a verdict for an
 * edited or replaced address must not annotate the new row that took its place.
 */
internal fun applyServerFlag(
    rows: List<ServerRowUiModel>,
    guid: String,
    address: String,
    status: FlagStatus,
): List<ServerRowUiModel> = rows.map {
    if (it.guid == guid && it.profile.server == address) it.copy(flagStatus = status) else it
}

/** Exit results belong to the running connection, never to a profile's ingress or label. */
internal fun exitCountryCode(status: MainStatus): String? =
    (status as? MainStatus.ConnectionTest)?.result?.takeIf { it.delayMillis >= 0 }
        ?.country?.let(ProfileCountry::normalize)
