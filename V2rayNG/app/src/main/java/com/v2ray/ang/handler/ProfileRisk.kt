package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType

/** Severity of a profile's security finding. The worst finding decides the row's flag. */
enum class RiskLevel(val severity: Int) {
    SAFE(0),
    UNKNOWN(1),
    CAUTION(2),
    UNSAFE(3),
}

enum class RiskReason {
    INSECURE_TLS,
    WEAK_CIPHER,
    MISSING_REALITY_PUBLIC_KEY,
    MISSING_SERVER_NAME,
    NO_TRANSPORT_ENCRYPTION,
}

data class RiskFinding(val level: RiskLevel, val reason: RiskReason)

data class RiskReport(val level: RiskLevel, val findings: List<RiskFinding> = emptyList()) {
    /** The finding that produced the row's level, so the badge names the actual problem. */
    val primary: RiskFinding? = findings.firstOrNull { it.level == level }
}

/**
 * Static inspection of a profile's own fields. Nothing here contacts the server: every verdict
 * must stay reproducible offline and identical for the same profile.
 */
object ProfileRisk {

    /**
     * Algorithm names as they appear inside hyphen-delimited suite names such as
     * `ECDHE-RSA-AES256-GCM-SHA384` or `3DES-CBC-SHA`, so a full suite string can be split into
     * tokens instead of being compared whole.
     */
    private val brokenCiphers = setOf(
        "rc2", "rc4", "des", "3des", "des3", "des40", "des56",
        "md2", "md4", "md5", "null", "export", "anon",
    )

    /** Handshake still succeeds, but the suite is deprecated and no longer worth trusting. */
    private const val LEGACY_CIPHER_SUFFIX = "-sha"

    fun evaluate(profile: ProfileItem): RiskReport {
        // A custom/policy/profile-chain blob carries its own TLS block that these fields do not
        // describe, so any verdict here would be a guess about content this resolver never reads.
        if (profile.configType.isComplexType()) return RiskReport(RiskLevel.UNKNOWN)

        val findings = mutableListOf<RiskFinding>()
        findings += certificateFinding(profile)
        findings += cipherFinding(profile)
        findings += realityFinding(profile)
        findings += transportFinding(profile)
        if (findings.isEmpty()) return RiskReport(RiskLevel.SAFE)
        val worst = findings.maxByOrNull { it.level.severity } ?: return RiskReport(RiskLevel.SAFE)
        return RiskReport(worst.level, findings)
    }

    /** `insecure` disables server certificate validation, which defeats TLS authentication. */
    private fun certificateFinding(profile: ProfileItem): List<RiskFinding> = when {
        profile.insecure != true -> emptyList()
        hasSecureHandshake(profile) ->
            listOf(RiskFinding(RiskLevel.UNSAFE, RiskReason.INSECURE_TLS))
        // Shadowsocks/WireGuard/Hysteria carry their own authenticated channel; the field is unused.
        else -> emptyList()
    }

    private fun cipherFinding(profile: ProfileItem): List<RiskFinding> {
        if (!hasSecureHandshake(profile)) return emptyList()
        val suites = profile.cipherSuites?.split(',').orEmpty()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return when {
            suites.any { suite -> suite.split('-').any { it in brokenCiphers } } ->
                listOf(RiskFinding(RiskLevel.UNSAFE, RiskReason.WEAK_CIPHER))
            suites.any { it.endsWith(LEGACY_CIPHER_SUFFIX) } ->
                listOf(RiskFinding(RiskLevel.CAUTION, RiskReason.WEAK_CIPHER))
            else -> emptyList()
        }
    }

    /** Reality without a public key cannot authenticate the peer, so the handshake is unusable. */
    private fun realityFinding(profile: ProfileItem): List<RiskFinding> {
        if (!profile.security.equals("reality", ignoreCase = true)) return emptyList()
        if (!profile.publicKey.isNullOrBlank()) return emptyList()
        return listOf(RiskFinding(RiskLevel.UNSAFE, RiskReason.MISSING_REALITY_PUBLIC_KEY))
    }

    private fun transportFinding(profile: ProfileItem): List<RiskFinding> {
        val security = profile.security?.trim().orEmpty()
        if (security.isNotEmpty()) {
            // Reality derives its SNI from the server address, so a blank `sni` is the normal case.
            if (security.equals("reality", ignoreCase = true)) return emptyList()
            // Any other named layer this resolver does not model is left unjudged rather than
            // guessed at; a "no encryption" claim about it would not be evidence.
            if (!security.equals("tls", ignoreCase = true)) return emptyList()
            // TLS is present but unnamed: no SNI, no host override and no pin to verify against.
            val unnamed = profile.sni.isNullOrBlank() && profile.host.isNullOrBlank() &&
                profile.pinnedCA256.isNullOrBlank()
            return if (unnamed) {
                listOf(RiskFinding(RiskLevel.CAUTION, RiskReason.MISSING_SERVER_NAME))
            } else {
                emptyList()
            }
        }
        return when (profile.configType) {
            // Trojan sends its password in the handshake, so a missing TLS layer exposes it.
            EConfigType.TROJAN ->
                listOf(RiskFinding(RiskLevel.UNSAFE, RiskReason.NO_TRANSPORT_ENCRYPTION))
            // The payload is encrypted, but the channel to the server is not wrapped at all.
            EConfigType.VMESS, EConfigType.VLESS ->
                listOf(RiskFinding(RiskLevel.CAUTION, RiskReason.NO_TRANSPORT_ENCRYPTION))
            // SOCKS and HTTP carry the credential itself with no encryption of their own.
            EConfigType.SOCKS, EConfigType.HTTP ->
                listOf(RiskFinding(RiskLevel.CAUTION, RiskReason.NO_TRANSPORT_ENCRYPTION))
            else -> emptyList()
        }
    }

    private fun hasSecureHandshake(profile: ProfileItem): Boolean =
        profile.security.equals("tls", ignoreCase = true) ||
            profile.security.equals("reality", ignoreCase = true)
}
