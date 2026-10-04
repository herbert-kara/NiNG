package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileRiskTest {

    private fun vless(
        security: String? = "tls",
        sni: String? = "example.com",
        insecure: Boolean? = null,
        cipherSuites: String? = null,
    ) = ProfileItem(
        configType = EConfigType.VLESS,
        server = "node.example.com",
        security = security,
        sni = sni,
        insecure = insecure,
        cipherSuites = cipherSuites,
    )

    @Test fun disabledCertificateCheckIsUnsafe() {
        val report = ProfileRisk.evaluate(vless(insecure = true))
        assertEquals(RiskLevel.UNSAFE, report.level)
        assertEquals(RiskReason.INSECURE_TLS, report.primary?.reason)
    }

    @Test fun disabledCertificateCheckIsIgnoredWhereTheFieldIsUnused() {
        // Shadowsocks authenticates and encrypts on its own; the TLS flag does not apply to it.
        val profile = ProfileItem(
            configType = EConfigType.SHADOWSOCKS, server = "node.example.com", insecure = true,
        )
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(profile).level)
    }

    @Test fun brokenCipherIsUnsafeAndLegacyCipherIsCaution() {
        // Real cipherSuites values are hyphen-delimited suite names, not bare algorithm names.
        assertEquals(
            RiskLevel.UNSAFE,
            ProfileRisk.evaluate(vless(cipherSuites = "ECDHE-RSA-AES256-GCM-SHA384,3DES-CBC-SHA")).level,
        )
        for (suite in listOf("3DES-CBC-SHA", "RC4-SHA", "DES-CBC-SHA", "EXP-RC2-CBC-SHA", "NULL-MD5")) {
            assertEquals(
                "$suite must be unsafe",
                RiskLevel.UNSAFE,
                ProfileRisk.evaluate(vless(cipherSuites = suite)).level,
            )
        }
        assertEquals(
            RiskReason.WEAK_CIPHER,
            ProfileRisk.evaluate(vless(cipherSuites = "3DES-CBC-SHA")).primary?.reason,
        )
        // A SHA-1 MAC suite still negotiates, so it is a caution rather than a break.
        for (suite in listOf("ECDHE-RSA-AES128-SHA", "AES256-SHA", "CAMELLIA128-SHA")) {
            assertEquals(
                "$suite must be a caution",
                RiskLevel.CAUTION,
                ProfileRisk.evaluate(vless(cipherSuites = suite)).level,
            )
        }
    }

    @Test fun modernCipherSuiteIsNotFlagged() {
        val profile = vless(cipherSuites = "TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384")
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(profile).level)
    }

    @Test fun realityWithoutPublicKeyIsUnsafe() {
        val profile = ProfileItem(
            configType = EConfigType.VLESS, security = "reality", publicKey = null,
        )
        val report = ProfileRisk.evaluate(profile)
        assertEquals(RiskLevel.UNSAFE, report.level)
        assertEquals(RiskReason.MISSING_REALITY_PUBLIC_KEY, report.primary?.reason)
    }

    @Test fun realityWithPublicKeyAndNoSniIsSafe() {
        // Reality takes its SNI from the server address, so a blank `sni` must not be flagged.
        val profile = ProfileItem(
            configType = EConfigType.VLESS, security = "reality", publicKey = "key",
        )
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(profile).level)
    }

    @Test fun unnamedTlsIsCaution() {
        val report = ProfileRisk.evaluate(vless(sni = null))
        assertEquals(RiskLevel.CAUTION, report.level)
        assertEquals(RiskReason.MISSING_SERVER_NAME, report.primary?.reason)
    }

    @Test fun hostOrPinSatisfiesTheServerNameCheck() {
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(vless(sni = null).copy(host = "cdn.example.com")).level)
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(vless(sni = null).copy(pinnedCA256 = "AA:BB")).level)
    }

    @Test fun unrecognizedSecurityLayerIsLeftUnjudged() {
        // A named layer this resolver does not model must not be reported as "no encryption".
        assertEquals(RiskLevel.SAFE, ProfileRisk.evaluate(vless(security = "xtls")).level)
    }

    @Test fun unencryptedTransportSeverityDependsOnProtocol() {
        fun level(type: EConfigType) = ProfileRisk.evaluate(
            ProfileItem(configType = type, server = "node.example.com", security = null),
        ).level
        assertEquals(RiskLevel.UNSAFE, level(EConfigType.TROJAN))
        assertEquals(RiskLevel.CAUTION, level(EConfigType.VMESS))
        assertEquals(RiskLevel.CAUTION, level(EConfigType.VLESS))
        assertEquals(RiskLevel.CAUTION, level(EConfigType.SOCKS))
        assertEquals(RiskLevel.CAUTION, level(EConfigType.HTTP))
    }

    @Test fun selfEncryptingProtocolsAreNotFlagged() {
        for (type in listOf(EConfigType.SHADOWSOCKS, EConfigType.WIREGUARD, EConfigType.HYSTERIA, EConfigType.HYSTERIA2)) {
            assertEquals(
                "$type must not be flagged for a missing TLS layer",
                RiskLevel.SAFE,
                ProfileRisk.evaluate(ProfileItem(configType = type, server = "node.example.com")).level,
            )
        }
    }

    @Test fun complexConfigsAreUnverifiableRatherThanFlagged() {
        for (type in listOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)) {
            val report = ProfileRisk.evaluate(ProfileItem(configType = type, insecure = true, security = null))
            assertEquals(RiskLevel.UNKNOWN, report.level)
            assertTrue("$type must produce no findings", report.findings.isEmpty())
        }
    }

    @Test fun worstFindingDecidesLevelAndPrimaryReason() {
        val report = ProfileRisk.evaluate(vless(insecure = true, cipherSuites = "3DES-CBC-SHA"))
        assertEquals(RiskLevel.UNSAFE, report.level)
        assertEquals(2, report.findings.size)
        assertEquals(RiskReason.INSECURE_TLS, report.primary?.reason)
    }

    @Test fun safeProfileHasNoFindings() {
        val report = ProfileRisk.evaluate(vless())
        assertEquals(RiskLevel.SAFE, report.level)
        assertTrue(report.findings.isEmpty())
        assertEquals(null, report.primary)
    }
}
