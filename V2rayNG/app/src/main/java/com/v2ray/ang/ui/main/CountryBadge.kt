package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
// Upstream migrated from Coil 2 to Coil 3; the artifact and the package both changed.
import coil3.compose.AsyncImage
import com.v2ray.ang.R
import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.ProfileCountry

/** Asset decoding is handled asynchronously by Coil; the adjacent text names the source. */
@Composable
internal fun CountryBadge(code: String?, @StringRes label: Int) {
    val asset = ProfileCountry.flagAsset(code) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(model = asset, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp, 14.dp))
        Text(stringResource(label, code.orEmpty()), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * The single flag a row is read by: the location of the main server.
 *
 * It occupies one slot and fills in place. Until the lookup has answered there is no flag to
 * show, so the slot stays in its plain unresolved state; the moment the provider reports a country
 * the real flag takes that exact place. Showing a grey placeholder *and* a second flag for the
 * same fact is what left a row reading "unchecked" next to a flag it already had.
 *
 * The verdict is a separate question, so it only appears when it has something to say: a flagged
 * server is worth interrupting for, a clean one is not, and an unresolved one is the same plain
 * state this slot already shows.
 */
@Composable
internal fun ServerFlagSlot(
    code: String?,
    status: FlagStatus,
    onMeasure: (() -> Unit)? = null,
    measuring: Boolean = false,
) {
    val asset = ProfileCountry.flagAsset(code)
    if (asset != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AsyncImage(model = asset, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(20.dp, 14.dp))
            Text(
                text = stringResource(R.string.country_server, code.orEmpty()),
                style = MaterialTheme.typography.labelSmall,
            )
            if (onMeasure != null) {
                MeasureCountryButton(onMeasure = onMeasure, measuring = measuring)
            }
        }
        return
    }
    when (status) {
        FlagStatus.FLAGGED -> FlaggedBadge(FlagStatus.FLAGGED)
        else -> if (onMeasure != null) MeasureCountryButton(onMeasure = onMeasure, measuring = measuring)
    }
}

/**
 * Re-reads the country of this row by standing the profile up and asking from inside it.
 *
 * The list fills a flag by asking about the address behind a hostname, which is cheap and wrong
 * behind a CDN: the answer names the edge, not the server. This asks the only way that can be
 * answered correctly, and it costs a whole core, so it is one row at a time on purpose.
 *
 * The button is a separate node from the flag and the label, because reading a row and measuring a
 * row are different actions, and merging them would leave a tap on the flag doing something no label
 * says it does.
 */
@Composable
private fun MeasureCountryButton(onMeasure: () -> Unit, measuring: Boolean) {
    val label = if (measuring) {
        stringResource(R.string.country_measuring)
    } else {
        stringResource(R.string.country_measure_exact)
    }
    TextButton(
        onClick = onMeasure,
        enabled = !measuring,
        modifier = Modifier.height(24.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
