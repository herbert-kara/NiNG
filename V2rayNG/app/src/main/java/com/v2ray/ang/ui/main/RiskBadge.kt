package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.RiskLevel
import com.v2ray.ang.handler.RiskReason

/**
 * Flag colors are fixed rather than theme-derived: each one has to keep its own meaning, and a
 * palette that shifts with dynamic color would let a "safe" row render in the error color.
 */
internal fun riskFlagColor(level: RiskLevel): Color = when (level) {
    RiskLevel.UNSAFE -> Color(0xFFD32F2F)
    RiskLevel.CAUTION -> Color(0xFFEF6C00)
    RiskLevel.SAFE -> Color(0xFF2E7D32)
    RiskLevel.UNKNOWN -> Color(0xFF757575)
}

@StringRes
private fun RiskReason.labelRes(): Int = when (this) {
    RiskReason.INSECURE_TLS -> R.string.risk_reason_insecure_tls
    RiskReason.WEAK_CIPHER -> R.string.risk_reason_weak_cipher
    RiskReason.MISSING_REALITY_PUBLIC_KEY -> R.string.risk_reason_missing_public_key
    RiskReason.MISSING_SERVER_NAME -> R.string.risk_reason_missing_sni
    RiskReason.NO_TRANSPORT_ENCRYPTION -> R.string.risk_reason_no_tls
}

/** The adjacent text states the verdict, so the flag itself needs no accessibility label. */
@Composable
internal fun RiskFlag(level: RiskLevel, reason: RiskReason?) {
    val tint = riskFlagColor(level)
    val text = when (level) {
        RiskLevel.SAFE -> stringResource(R.string.risk_not_flagged)
        RiskLevel.UNKNOWN -> stringResource(R.string.risk_unknown)
        else -> stringResource(R.string.risk_flagged, stringResource(reason?.labelRes() ?: R.string.risk_unknown))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FlagGlyph(tint, Modifier.size(12.dp, 16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A pole and a banner drawn in one color; no icon dependency for a two-shape glyph. */
@Composable
private fun FlagGlyph(tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val poleWidth = size.width * 0.18f
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(poleWidth, size.height))
        val bannerHeight = size.height * 0.6f
        drawPath(
            color = tint,
            path = Path().apply {
                moveTo(poleWidth, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, bannerHeight)
                lineTo(poleWidth, bannerHeight)
                close()
            },
        )
    }
}
