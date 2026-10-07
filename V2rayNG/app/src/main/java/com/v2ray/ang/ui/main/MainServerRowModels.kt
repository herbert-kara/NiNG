package com.v2ray.ang.ui.main

import com.v2ray.ang.core.AetherCore
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.FlagStatus
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
    // Only the main server's own location, from the reputation verdict for that address. A
    // name-based hint is deliberately absent: it describes the label, not the server reached.
    val serverCountryCode: String? = null,
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,
    val riskReason: RiskReason? = null,
    val flagStatus: FlagStatus = FlagStatus.UNKNOWN,
    // What the last batch test learned beyond the delay. Absent (-1) when the run predates
    // multi-sample testing, so a row can say it knows nothing rather than claiming zero jitter.
    val testJitterMillis: Long = -1L,
    val testLossPercent: Int = -1,
    val testScore: Int = -1,
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
    aetherProtocolLabel: (AetherProtocol) -> String = AetherProtocol::name,
): ServerRowUiModel {
    val profile = server.profile
    val risk = ProfileRisk.evaluate(profile)
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile, aetherProtocolLabel),
        testDelayMillis = server.testDelayMillis,
        testJitterMillis = server.testJitterMillis,
        testLossPercent = server.testLossPercent,
        testScore = server.testScore,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        riskLevel = risk.level,
        riskReason = risk.primary?.reason,
    )
}

/**
 * The type of [profile] as its row names it; an Aether profile's WARP protocol is named by [aetherProtocolLabel], as
 * the editor names it, see [aetherProtocolLabels].
 */
internal fun serverProtocolDescription(
    profile: ProfileItem,
    aetherProtocolLabel: (AetherProtocol) -> String = AetherProtocol::name,
): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    if (profile.configType == EConfigType.AETHER) {
        // The tunnel from the outside in, so a profile that runs Psiphon or Tor alone is not named after a WARP protocol it never opens.
        parts.add(AetherCore.of(profile).path(aetherProtocolLabel).joinToString(" → "))
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

/**
 * The names the editor's protocol list gives the WARP protocols, [labels] beside the [values] a profile stores, as a
 * lookup for [serverProtocolDescription]; a protocol the list lacks keeps its own name.
 */
internal fun aetherProtocolLabels(labels: List<String>, values: List<String>): (AetherProtocol) -> String =
    { protocol -> labels.getOrNull(values.indexOf(protocol.type)) ?: protocol.name }
