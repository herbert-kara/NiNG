package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
