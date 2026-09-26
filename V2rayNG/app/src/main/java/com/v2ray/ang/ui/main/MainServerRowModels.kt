package com.v2ray.ang.ui.main

import com.v2ray.ang.core.AetherCore
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ProfileCountry
import com.v2ray.ang.handler.ProfileRisk
import com.v2ray.ang.handler.RiskLevel
import com.v2ray.ang.handler.RiskReason

internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
    val labelCountryCode: String? = null,
    val serverCountryCode: String? = null,
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,
    val riskReason: RiskReason? = null,
    val flagStatus: FlagStatus = FlagStatus.UNKNOWN,
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
): ServerRowUiModel {
    val profile = server.profile
    val risk = ProfileRisk.evaluate(profile)
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        labelCountryCode = ProfileCountry.fromLabel(profile.remarks),
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        riskLevel = risk.level,
        riskReason = risk.primary?.reason,
    )
}

internal fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    if (profile.configType == EConfigType.AETHER) {
        // The tunnel from the outside in, so a profile that runs Psiphon or Tor alone is not named after a WARP protocol it never opens.
        parts.add(AetherCore.of(profile).path.joinToString(" → "))
    }
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}
