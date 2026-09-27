package com.v2ray.ang.handler

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
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
import java.net.InetAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Ingress geography, independent of the existing proxied exit-IP test in SpeedtestManager.
 * Owned by MainViewModel: one serialized lookup, bounded positive/negative cache, no global jobs.
 * Only public IPs go to a fixed HTTPS endpoint; profiles, labels and credentials never do.
 */
internal class ServerCountryLookup(
    private val resolveDns: (suspend (String) -> List<InetAddress>)? = null,
    private val fetch: (suspend (String) -> String?)? = null,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cacheLimit: Int = 256,
) : Closeable {
    private data class Entry(val code: String?, val expires: Long)
    private val cache = linkedMapOf<String, Entry>()
    // Separate from the cache lock: one lock across the whole lookup serialised the
    // network calls and defeated any concurrent walk.
    private val cacheLock = Mutex()
    private val pacing = Mutex()
    // Simultaneous requests for one address must cost one round trip, not one each.
    private val inFlight = mutableMapOf<String, Deferred<String?>>()
    private var lastStart: Long? = null
    // Android's platform DNS can ignore interruption. Bound its workers and queue, never spawn
    // a replacement thread per timeout. close() cancels pending work when the ViewModel ends.
    private val dnsExecutorHolder = lazy {
        ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1)) { task ->
            Thread(task, "NiNG-country-dns").apply { isDaemon = true }
        }
    }
    private val dnsExecutor by dnsExecutorHolder
    private val clientHolder = lazy {
        OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(5, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS).build()
    }

    private val client by clientHolder

    /**
     * Public IP behind a server address, or null when it is not public. The flagged-reputation
     * lookup shares this path so neither service owns a second DNS pool or a second set of
     * private-range rules.
     */
    suspend fun publicIpOf(target: String?): String? {
        val key = canonicalTarget(target) ?: return null
        val literal = literalIp(key)
        if (literal != null) return if (isPublicIp(literal)) literal.hostAddress else null
        return resolvePublicIp(key)
    }

    private suspend fun resolvePublicIp(key: String): String? = withTimeoutOrNull(3500) {
        (resolveDns?.invoke(key) ?: defaultDns(key)).firstOrNull(::isPublicIp)
    }?.hostAddress

    suspend fun resolve(address: String?): String? {
        val key = canonicalTarget(address) ?: return null
        val literal = literalIp(key)
        if (literal != null && !isPublicIp(literal)) return null
        // Cache check lives in its own lock, separate from the request pacing, so concurrent
        // lookups for different addresses are not serialised behind one another's network call.
        cacheLock.withLock {
            cache[key]?.takeIf { nowMillis() < it.expires }?.let { return it.code }
        }
        val result = try {
            // The literal is already known public here, or absent.
            val ip = literal?.hostAddress ?: resolvePublicIp(key)
            if (ip == null) {
                null
            } else {
                fetchOnce(ip, 5500) { host ->
                    rateLimit()
                    ProfileCountry.normalize(fetch?.invoke(host) ?: if (fetch == null) defaultFetch(host) else null)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional enrichment fails closed. Do not log hostnames or network exception URLs.
            null
        }
        cacheLock.withLock {
            cache.remove(key)
            cache[key] = Entry(result, nowMillis() + if (result == null) 300_000 else 86_400_000)
            while (cache.size > cacheLimit.coerceAtLeast(1)) cache.remove(cache.keys.first())
        }
        return result
    }

    /** Serialises request *starts* only, so concurrent lookups still overlap their round trips. */
    private suspend fun rateLimit() = pacing.withLock {
        val elapsed = lastStart?.let { nowMillis() - it }
        if (elapsed != null && elapsed < 1100) delay(1100 - elapsed)
        lastStart = nowMillis()
    }


    /**
     * One provider round trip per address at a time, however many callers want the answer.
     *
     * A [Deferred] is held rather than its value, so the second caller awaits the first request
     * instead of starting a competing one. Nothing here holds a lock across the network call, which
     * is what let a page of different addresses overlap in the first place.
     */
    private suspend fun fetchOnce(ip: String, timeoutMs: Long, request: suspend (String) -> String?): String? {
        val waiter = cacheLock.withLock { inFlight[ip] }
        if (waiter != null) return waiter.await()
        val deferred = CoroutineScope(currentCoroutineContext())
            .async(start = CoroutineStart.LAZY) { withTimeoutOrNull(timeoutMs) { request(ip) } }
        val mine = cacheLock.withLock { inFlight.putIfAbsent(ip, deferred) ?: deferred }
        try {
            if (mine === deferred) deferred.start()
            return deferred.await()
        } finally {
            cacheLock.withLock { if (inFlight[ip] === deferred) inFlight.remove(ip) }
        }
    }

    private suspend fun defaultDns(host: String): List<InetAddress> = runInterruptible(Dispatchers.IO) {
        val future = dnsExecutor.submit<List<InetAddress>> { InetAddress.getAllByName(host).toList() }
        try {
            future.get(3, TimeUnit.SECONDS)
        } finally {
            future.cancel(true)
        }
    }

    private suspend fun defaultFetch(ip: String): String? {
        // A single blocked or rate-limited endpoint must not leave the row without a flag, so
        // each provider is tried in turn and the first usable answer wins.
        for (endpoint in COUNTRY_ENDPOINTS) {
            val body = fetchFrom(endpoint.replace("{ip}", ip)) ?: continue
            val code = parseResponse(body) ?: continue
            return code
        }
        return null
    }

    private suspend fun fetchFrom(url: String): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val code = try {
                    response.use {
                        if (!it.isSuccessful) null else {
                            // Bound memory even when Content-Length is absent or dishonest.
                            val source = it.body?.source()
                            if (source == null || !source.request(16_385)) null else source.readUtf8()
                        }
                    }
                } catch (_: Exception) { null }
                continuation.resume(code)
            }
        })
    }

    override fun close() {
        if (dnsExecutorHolder.isInitialized()) dnsExecutor.shutdownNow()
        if (clientHolder.isInitialized()) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        /**
         * Tried in order. Each returns the same shape that [parseResponse] reads, so a provider
         * being blocked or rate-limited costs one round trip instead of the whole flag.
         */
        internal val COUNTRY_ENDPOINTS = listOf(
            "https://ipwho.is/{ip}",
            "https://ipapi.co/{ip}/json/",
            "https://ipinfo.io/{ip}/json",
        )

        /**
         * Every provider is parsed through the same tolerant reader: they disagree on the field
         * name, and none of them agree on the success wrapper, so a shape difference must not be
         * read as "this provider said no country".
         */
        internal fun parseResponse(body: String): String? = try {
            val json = JsonParser.parseString(body).asJsonObject
            if (json.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) return null
            // Read the three accepted spellings directly rather than iterating the map, which
            // keeps this independent of the Gson version's JsonObject API surface.
            val code = sequenceOf("country_code", "countryCode", "country")
                .mapNotNull { key -> json.get(key) }
                .filter { it.isJsonPrimitive }
                .map { it.asString }
                .mapNotNull(ProfileCountry::normalize)
                .firstOrNull()
            code
        } catch (_: Exception) { null }

        internal fun canonicalTarget(value: String?): String? {
            val input = value?.trim()?.removeSurrounding("[", "]")?.trimEnd('.')
                ?.lowercase(Locale.ROOT)?.takeIf { it.length in 1..253 } ?: return null
            literalIp(input)?.let { return it.hostAddress }
            if (input.contains(':') || input.all { it.isDigit() || it == '.' }) return null
            val labels = input.split('.')
            if (labels.size < 2 || labels.any { !it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) }) return null
            if (labels.last() in setOf("local", "localhost", "internal", "lan", "home", "test", "invalid", "example", "onion")) return null
            if (labels.last().all { it.isDigit() } || labels.all { it.matches(Regex("(?:0x[0-9a-f]+|[0-9]+)")) }) return null
            return input
        }

        private fun literalIp(value: String): InetAddress? = try {
            when {
                value.contains(':') && value.all { it in "0123456789abcdefABCDEF:." } -> InetAddress.getByName(value)
                value.matches(Regex("[0-9]+(?:\\.[0-9]+){3}")) -> {
                    val parts = value.split('.')
                    if (parts.any { it.length > 3 || (it.length > 1 && it.startsWith('0')) || it.toInt() > 255 }) null
                    else InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
                }
                else -> null
            }
        } catch (_: Exception) { null }

        internal fun isPublicIp(ip: InetAddress): Boolean {
            val b = ip.address.map { it.toInt() and 255 }
            if (b.size == 16 && b.take(10).all { it == 0 } && b[10] == 255 && b[11] == 255) {
                return isPublicIp(InetAddress.getByAddress(ip.address.takeLast(4).toByteArray()))
            }
            if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress || ip.isSiteLocalAddress || ip.isMulticastAddress) return false
            if (b.size == 4) return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
                (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
                (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
                (b[0] == 192 && b[1] == 0 && b[2] in listOf(0, 2)) ||
                (b[0] == 192 && b[1] == 88 && b[2] == 99) ||
                (b[0] == 198 && b[1] in 18..19) || (b[0] == 198 && b[1] == 51 && b[2] == 100) ||
                (b[0] == 203 && b[1] == 0 && b[2] == 113))
            // Global unicast only; exclude protocol assignments, 6to4 and documentation.
            return b.size == 16 && (b[0] and 0xe0) == 0x20 &&
                !(b[0] == 0x20 && b[1] == 0x01 && (b[2] < 2 || (b[2] == 0x0d && b[3] == 0xb8))) &&
                !(b[0] == 0x20 && b[1] == 0x02) && !(b[0] == 0x3f && b[1] == 0xff && b[2] < 16)
        }
    }
}
