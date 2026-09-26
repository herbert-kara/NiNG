package com.v2ray.ang.ui.main

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Walk one batch of targets, publishing each result as it arrives, and report whether a newer
 * request arrived while it ran.
 *
 * A tap must never cancel the pass already under way. Each lookup is rate limited, so a full page
 * takes seconds; cancelling on every tap meant the batch restarted before it reached its last row,
 * no badge ever changed, and the button looked dead while the click itself worked. Instead the
 * batch finishes, and only a tap that arrived *after* it finished triggers another pass.
 */
internal suspend fun <K, V> runFlagBatch(
    targets: List<Pair<K, String>>,
    isNewerRequested: () -> Boolean,
    lookup: suspend (address: String, force: Boolean) -> V?,
    publish: suspend (key: K, address: String, verdict: V) -> Unit,
): Boolean {
    for ((key, address) in targets) {
        currentCoroutineContext().ensureActive()
        val verdict = lookup(address, true) ?: continue
        publish(key, address, verdict)
    }
    return isNewerRequested()
}
