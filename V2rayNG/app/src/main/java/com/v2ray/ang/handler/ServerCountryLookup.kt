package com.v2ray.ang.handler

import com.google.gson.Gson
import com.v2ray.ang.dto.IPAPIInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    private val gate = Mutex()
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

    suspend fun resolve(address: String?): String? {
        val key = canonicalTarget(address) ?: return null
        val literal = literalIp(key)
        if (literal != null && !isPublicIp(literal)) return null
        // Cache check lives inside the same gate as requests: simultaneous identical requests
        // share the first result without unbounded in-flight maps or detached coroutine scopes.
        return gate.withLock {
            cache[key]?.takeIf { nowMillis() < it.expires }?.let { return@withLock it.code }
            val result = try {
                val ip = literal ?: withTimeoutOrNull(3500) {
                    (resolveDns?.invoke(key) ?: defaultDns(key)).firstOrNull(::isPublicIp)
                }
                if (ip == null) null else {
                    val elapsed = lastStart?.let { nowMillis() - it }
                    if (elapsed != null && elapsed < 1100) delay(1100 - elapsed)
                    lastStart = nowMillis()
                    withTimeoutOrNull(5500) {
                        ProfileCountry.normalize(fetch?.invoke(ip.hostAddress!!) ?: if (fetch == null) defaultFetch(ip.hostAddress!!) else null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional enrichment fails closed. Do not log hostnames or network exception URLs.
                null
            }
            cache.remove(key)
            cache[key] = Entry(result, nowMillis() + if (result == null) 300_000 else 86_400_000)
            while (cache.size > cacheLimit.coerceAtLeast(1)) cache.remove(cache.keys.first())
            result
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

    private suspend fun defaultFetch(ip: String): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("https://ipwho.is/$ip").get().build()
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
                            if (source == null || source.request(16_385)) null
                            else parseResponse(source.readUtf8())
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
        internal fun parseResponse(body: String): String? = try {
            val info = Gson().fromJson(body, IPAPIInfo::class.java)
            if (info?.success == true) ProfileCountry.normalize(info.country_code) else null
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
