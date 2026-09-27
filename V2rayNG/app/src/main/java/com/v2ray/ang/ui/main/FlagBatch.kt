package com.v2ray.ang.ui.main

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Walk a batch of targets concurrently and publish each result as it arrives.
 *
 * The walk used to be one row at a time. The lookups are rate limited, so a serial walk of a full
 * page came to roughly two hours of requests and the last row of the first screen was still hours
 * away, which is why the flags never appeared. A bounded number of lookups now run at the same
 * time: a full page resolves in seconds while the provider still sees a polite request rate.
 *
 * [force] makes every target a fresh query. That is what a latency test means: the user has just
 * proved the address is reachable, so its verdict is asked again instead of being replayed from a
 * cache filled before the test.
 */
internal suspend fun <K, V> runFlagBatch(
    targets: List<Pair<K, String>>,
    force: Boolean,
    concurrency: Int = FLAG_LOOKUP_CONCURRENCY,
    lookup: suspend (address: String, force: Boolean) -> V?,
    publish: suspend (key: K, address: String, verdict: V) -> Unit,
    // A walk that was never started and a walk whose lookups all came back empty were both
    // silent, which is why a missing flag could not be told apart from a missing run. The count is
    // reported to a seam rather than logged here: LogUtil reads the log level from the settings
    // store, so a log call in the walk makes every test that runs a walk fail on
    // MMKV.initialize() instead of reaching its own assertion. A caller that wants the line
    // supplies it; the default is silence.
    onWalk: ((started: Boolean, targets: Int, force: Boolean) -> Unit)? = null,
) {
    onWalk?.invoke(true, targets.size, force)
    if (targets.isEmpty()) {
        onWalk?.invoke(false, 0, force)
        return
    }
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    coroutineScope {
        targets.map { (key, address) ->
            async {
                gate.withPermit {
                    currentCoroutineContext().ensureActive()
                    // A row that cannot be resolved is skipped rather than aborting the pass:
                    // one bad address must not cost every other row its flag.
                    val verdict = lookup(address, force) ?: return@withPermit
                    publish(key, address, verdict)
                }
            }
        }.awaitAll()
    }
    onWalk?.invoke(false, targets.size, force)
}

/** Enough lookups in flight to finish a page in seconds, few enough to stay polite. */
internal const val FLAG_LOOKUP_CONCURRENCY = 8
