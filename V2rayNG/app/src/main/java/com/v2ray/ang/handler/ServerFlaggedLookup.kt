package com.v2ray.ang.handler

import com.v2ray.ang.dto.ProxyCheckInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Verdict of an anti-fraud / IP-reputation check for one configuration. */
internal enum class FlagStatus { UNKNOWN, CLEAN, FLAGGED }

internal data class FlagVerdict(
    val status: FlagStatus,
    val risk: Int,
    val type: String?,
    val countryCode: String?,
)

/**
 * Whether a configuration's public IP is listed as proxy/VPN/hosting by https://proxycheck.io
 * (HTTPS, no API key). Mirrors [ServerCountryLookup]: private and reserved addresses are
 * refused with no network call, verdicts are cached (24h success, 5min failure), and outbound
 * requests are serialized with a 1.1s gap to stay friendly to the public endpoint.
 *
 * Only the public IP is ever sent. Profile names, labels, credentials and config content are
 * not part of the request and must never be.
 */
internal class ServerFlaggedLookup(
    private val publicIpOf: suspend (String?) -> String?,
    private val fetch: (suspend (String) -> String?)? = null,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cacheLimit: Int = 256,
) : Closeable {
    private data class Entry(val verdict: FlagVerdict?, val expires: Long)
    private val cache = linkedMapOf<String, Entry>()
    // Two locks, deliberately separate: the cache is held only for map access, and the
    // rate limiter only for the gap between request starts. One lock around the whole
    // lookup serialized the network calls and defeated any concurrent walk.
    private val cacheLock = Mutex()
    private val pacing = Mutex()
    // One lock per address: equal addresses share a round trip, different ones do not wait.
    private val perAddress = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private var lastStart: Long? = null

    private val clientHolder = lazy {
        OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(8, TimeUnit.SECONDS).connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS).build()
    }

    private val client by clientHolder

    /** Visible for the cache-bound regression test; the map itself stays private. */
    internal val cacheSize: Int get() = cache.size

    /**
     * [forceRefresh] must actually re-query the provider rather than replay a verdict up to a
     * day old, so the cached entry is dropped before the lookup.
     */
    suspend fun resolve(address: String?, forceRefresh: Boolean = false): FlagVerdict? {
        val key = ServerCountryLookup.canonicalTarget(address) ?: return null
        if (forceRefresh) cacheLock.withLock { cache.remove(key) }
        cacheLock.withLock {
            cache[key]?.takeIf { nowMillis() < it.expires }?.let { return it.verdict }
        }
        val verdict = try {
            val ip = publicIpOf(key)
            if (ip == null) null else {
                // Only the pacing is serialized. Holding one lock across the whole lookup made
                // every request wait for the previous one's full timeout, so a concurrent walk
                // queued up behind the rate limiter it was meant to overlap and a page still
                // took minutes. The gap between request starts is what the provider needs.
                fetchOnce(ip) { host ->
                    rateLimit()
                    parseVerdict(fetch?.invoke(host) ?: if (fetch == null) defaultFetch(host) else null, host)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Reputation is optional enrichment and fails closed. Never log the address.
            null
        }
        cacheLock.withLock {
            cache[key] = Entry(verdict, nowMillis() + if (verdict == null) FAILURE_TTL_MS else SUCCESS_TTL_MS)
            while (cache.size > cacheLimit.coerceAtLeast(1)) cache.remove(cache.keys.first())
        }
        return verdict
    }

    /** Serialises request *starts* only, so concurrent lookups still overlap their round trips. */

    /**
     * One provider round trip per address at a time, however many callers want the answer.
     *
     * A lock per address, not one lock for the whole lookup: the second caller for an address waits
     * on that address's lock and then reads the answer the first one cached, while callers for
     * *different* addresses never wait for each other. A shared Deferred was the obvious
     * alternative and the wrong one, because it has to be started in a scope that is not the
     * caller's, and a detached coroutine is cancelled out from under runTest.
     */
    private suspend fun fetchOnce(ip: String, request: suspend (String) -> FlagVerdict?): FlagVerdict? {
        val keyLock = perAddress.computeIfAbsent(ip) { Mutex() }
        try {
            return keyLock.withLock { request(ip) }
        } finally {
            // Drop the lock once the request settles, so the map does not grow with every address.
            perAddress.remove(ip, keyLock)
        }
    }

    private suspend fun rateLimit() = pacing.withLock {
        val elapsed = lastStart?.let { nowMillis() - it }
        if (elapsed != null && elapsed < REQUEST_GAP_MS) delay(REQUEST_GAP_MS - elapsed)
        lastStart = nowMillis()
    }

    private suspend fun defaultFetch(ip: String): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("$ENDPOINT_BASE$ip$QUERY").get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = try {
                    response.use {
                        if (!it.isSuccessful) null else {
                            // Bound memory even when Content-Length is absent or dishonest.
                            val source = it.body?.source()
                            if (source == null || !source.request(16_385)) null else source.readUtf8()
                        }
                    }
                } catch (_: Exception) { null }
                continuation.resume(body)
            }
        })
    }

    /**
     * Drop every cached verdict so the next batch re-queries the provider. A manual refresh must
     * actually reach the service, not replay a verdict that is up to a day old.
     */
    suspend fun invalidate() {
        cacheLock.withLock { cache.clear() }
    }

    override fun close() {
        if (clientHolder.isInitialized()) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        private const val ENDPOINT_BASE = "https://proxycheck.io/v2/"
        private const val QUERY = "?vpn=1&asn=1&risk=1"
        private const val SUCCESS_TTL_MS = 86_400_000L
        private const val FAILURE_TTL_MS = 300_000L
        private const val REQUEST_GAP_MS = 1100L

        /** proxycheck.io scores 0-100; above this the IP is reported flagged on its own. */
        internal const val RISK_FLAG_THRESHOLD = 60

        /**
         * The payload is keyed by the IP that was queried. A non-ok status (rate limit, denied
         * quota, bad input) is not a verdict, and a missing key is never guessed at: a verdict
         * must belong to the address that was actually asked about.
         */
        internal fun parseVerdict(body: String?, queriedIp: String?): FlagVerdict? {
            val info = ProxyCheckInfo.parse(body) ?: return null
            if (!info.status.equals("ok", ignoreCase = true)) return null
            val entry = queriedIp?.let { info.entries[it] } ?: return null
            val risk = (entry.risk ?: 0).coerceIn(0, 100)
            val isProxy = entry.proxy.equals("yes", ignoreCase = true)
            val isVpn = entry.type.equals("VPN", ignoreCase = true)
            val status = if (isProxy || isVpn || risk >= RISK_FLAG_THRESHOLD) {
                FlagStatus.FLAGGED
            } else {
                FlagStatus.CLEAN
            }
            return FlagVerdict(status, risk, entry.type?.takeIf { it.isNotBlank() }, ProfileCountry.normalize(entry.isocode))
        }
    }
}
