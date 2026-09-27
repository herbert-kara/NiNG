package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
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
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
    // Default: no tunnel. A tunnel is only meaningful while the service is listening, and reading
    // the port costs a settings-store read that throws before initialize() in a unit test, so a
    // default that reached for it would make every construction depend on Android -- which is
    // what broke twelve tests twice. The one production caller wires the real settings; a
    // construction that does not is asking for the direct route, which is a valid answer.
    private val tunnelPort: () -> Int? = { null },
    private val tunnelUser: () -> String? = { null },
    private val tunnelPassword: () -> String? = { null },
    // Who records the outcome, if anyone. Default: nobody. LogUtil reads the log level from the
    // settings store, so a LogUtil call anywhere on this path makes every unit test that constructs
    // the lookup fail on MMKV.initialize() -- which is how a diagnostic added to find this bug
    // became the reason eleven tests could not run. A caller that wants the line supplies it; the
    // default is silence, and a seam here is testable where a log line is not.
    private val onOutcome: ((CountryOutcome) -> Unit)? = null,
) : Closeable {

    /**
     * What one lookup produced, and over which route. No hostname: the root guide forbids logging
     * hosts and URLs, and the shape is the part that diagnoses the failure.
     */
    data class CountryOutcome(
        val hit: Boolean,
        val viaTunnel: Boolean,
        val keyLength: Int,
        val literal: Boolean,
        val resolvedIp: String?,
        /** Transport-level failure reasons in provider order, empty when one answered. */
        val reasons: List<String> = emptyList(),
    )
    private data class Entry(val code: String?, val expires: Long)
    private val cache = linkedMapOf<String, Entry>()
    // Separate from the cache lock: one lock across the whole lookup serialised the
    // network calls and defeated any concurrent walk.
    private val cacheLock = Mutex()
    private val pacing = Mutex()
    // One lock per address: equal addresses share a round trip, different ones do not wait.
    private val perAddress = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private var lastStart: Long? = null
    // Android's platform DNS can ignore interruption. Bound its workers and queue, never spawn
    // a replacement thread per timeout. close() cancels pending work when the ViewModel ends.
    private val dnsExecutorHolder = lazy {
        ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1)) { task ->
            Thread(task, "NiNG-country-dns").apply { isDaemon = true }
        }
    }
    private val dnsExecutor by dnsExecutorHolder
    /**
     * Where the provider request goes.
     *
     * Through the app's own loopback HTTP port when the tunnel is up, exactly as the connection
     * panel's (DE) line does. That panel is the one place the app already shows a country, and it
     * works because SpeedtestManager.getRemoteIPInfo() asks through the tunnel, so the answer
     * describes the exit the user actually has. This lookup was pinned to Proxy.NO_PROXY, which
     * asks the provider from outside the tunnel, and from a network where the providers are
     * blocked that is a request to a black hole: it returned null for every row, so no row got a
     * flag, while the panel two inches above it showed DE. When no tunnel is running there is no
     * exit to describe, so it falls back to the direct path.
     */
    /**
     * Where the request goes when the tunnel is up.
     *
     * A seam like [resolveDns] and [fetch], and for the same reason: reading the app's HTTP port
     * goes through the settings store, which throws before initialize() in a unit test, so the
     * lookup tests could not construct the class at all once the route depended on it. Reading the
     * port is the only settings read on this path, so it is the only thing injected -- the proxy it
     * builds, the loopback address and the credentials all stay here.
     */
    private fun tunnelProxy(): Proxy? {
        val port = tunnelPort() ?: return null
        if (port == 0) return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, port))
    }

    /**
     * Rebuilt when the route changes, so a row asked while disconnected is asked again once the
     * tunnel is up. A builder, not a client: the proxy is chosen per build, and a `Lazy` cannot
     * delegate a read-write property, so a client held behind one can only be swapped by
     * reassignment.
     */
    private fun newClientBuilder() = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)

    private var client: OkHttpClient = newClientBuilder().proxy(Proxy.NO_PROXY).build()
    private var routedThroughTunnel = false

    /** Set by close(); a closed lookup must not rebuild a client on a dead executor. */
    private var closed = false

    private fun routeThroughTunnelIfUp() {
        if (closed) return
        val proxy = tunnelProxy()
        if ((proxy != null) == routedThroughTunnel) return
        client = if (proxy == null) {
            newClientBuilder().proxy(Proxy.NO_PROXY).build()
        } else {
            newClientBuilder().proxy(proxy)
                .proxyAuthenticator { _, response ->
                    val user = tunnelUser()
                    val pass = tunnelPassword()
                    if (user.isNullOrBlank() || pass.isNullOrBlank()) null
                    else if (response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(user, pass)).build()
                }
                .build()
        }
        routedThroughTunnel = proxy != null
    }

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
        routeThroughTunnelIfUp()
        val key = canonicalTarget(address) ?: return null
        val literal = literalIp(key)
        if (literal != null && !isPublicIp(literal)) return null
        // Cache check lives in its own lock, separate from the request pacing, so concurrent
        // lookups for different addresses are not serialised behind one another's network call.
        cacheLock.withLock {
            cache[key]?.takeIf { nowMillis() < it.expires }?.let { return it.code }
        }
        var ipForOutcome: String? = null
        // Per-lookup, not per-class: the walk runs eight lookups at once, so a shared list
        // would let one address's provider failures reach another address's outcome.
        var reasonsForOutcome: List<String> = emptyList()
        val result = try {
            // The literal is already known public here, or absent.
            val ip = literal?.hostAddress ?: resolvePublicIp(key)
            ipForOutcome = ip
            if (ip == null) {
                null
            } else {
                fetchOnce(key, ip) { host ->
                    rateLimit()
                    // Collected here rather than pushed through the seam the tests drive, and held
                    // in a local rather than a field: a walk runs eight of these at once, and a
                    // field would let one lookup's reasons reach another lookup's outcome.
                    val reasons = mutableListOf<String>()
                    val body = fetch?.invoke(host)
                        ?: if (fetch == null) defaultFetch(host, reasons) else null
                    reasonsForOutcome = reasons
                    ProfileCountry.normalize(body)
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
        // The row flag has been silently absent across several releases, and this path had no
        // recording at all, so a lookup that never ran and one that ran and got nothing were the
        // same thing from adb. The outcome goes to a seam rather than a log call, because LogUtil
        // reads the log level from the settings store and would make every test that constructs
        // this lookup fail before reaching its own assertion. No hostname: the root guide forbids
        // logging hosts and URLs, and the shape is the part that diagnoses the failure.
        onOutcome?.invoke(
            CountryOutcome(
                hit = result != null,
                viaTunnel = routedThroughTunnel,
                keyLength = key.length,
                literal = literal != null,
                resolvedIp = ipForOutcome,
                reasons = if (result != null) emptyList() else reasonsForOutcome,
            )
        )
        return result
    }

    /** Serialises request *starts* only, so concurrent lookups still overlap their round trips. */
    private suspend fun rateLimit() = pacing.withLock {
        val elapsed = lastStart?.let { nowMillis() - it }
        if (elapsed != null && elapsed < REQUEST_GAP_MS) delay(REQUEST_GAP_MS - elapsed)
        lastStart = nowMillis()
    }


    /**
     * One provider round trip per address at a time, however many callers want the answer.
     *
     * A lock per address, not one lock for the whole lookup: the second caller for an address waits
     * on that address's lock and then reads the answer the first one cached, while callers for
     * *different* addresses never wait for each other. A shared Deferred was the obvious
     * alternative and the wrong one, because it has to be started in a scope that is not the
     * caller's, and a detached coroutine is cancelled out from under runTest.
     */
    private suspend fun fetchOnce(key: String, ip: String, request: suspend (String) -> String?): String? {
        val keyLock = perAddress.computeIfAbsent(key) { Mutex() }
        try {
            return keyLock.withLock {
                // Re-read under the lock: a caller that queued behind this one arrives before the
                // result was cached, and would otherwise repeat the request this lock just made.
                cacheLock.withLock {
                    cache[key]?.takeIf { nowMillis() < it.expires }?.let { return it.code }
                }
                request(ip)
            }
        } finally {
            // Drop the lock once the request settles, so the map does not grow with every address.
            perAddress.remove(key, keyLock)
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

    /**
     * Tries every provider in turn and keeps the reasons the ones that failed gave.
     *
     * The order is a preference, not a health check, so the reasons are collected in the order the
     * endpoints were tried. That list is what distinguishes a proxy that rejects the request from
     * a provider that is down from a provider that answers with something unparseable, which are
     * three different bugs that all look like "no flag" on a device.
     */
    private suspend fun defaultFetch(ip: String, reasons: MutableList<String>): String? {
        // A single blocked or rate-limited endpoint must not leave the row without a flag, so
        // each provider is tried in turn and the first usable answer wins.
        for (endpoint in COUNTRY_ENDPOINTS) {
            val attempt = fetchFrom(endpoint.replace("{ip}", ip))
            if (attempt.reason != "ok") {
                reasons.add(attempt.reason)
                continue
            }
            val code = parseResponse(attempt.body ?: continue)
            if (code == null) {
                reasons.add("unparsed")
                continue
            }
            return code
        }
        return null
    }

    /**
     * A body, or null with the reason attached.
     *
     * Every failure used to become the same null: a rejected proxy, a refused connection, a
     * timeout and an unparseable answer were indistinguishable at the call site, so the only thing
     * a device could report was that nothing came back. The reason is a transport fact -- an
     * exception type or a status code -- and never a host, an address or a body, so it can be
     * logged without leaking what was asked about.
     */
    private data class Attempt(val body: String?, val reason: String)

    private suspend fun fetchFrom(url: String): Attempt = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // The class name says which failure: a rejected proxy and a refused port look
                // identical from here otherwise.
                continuation.resume(Attempt(null, e.javaClass.simpleName))
            }

            override fun onResponse(call: Call, response: Response) {
                val attempt = try {
                    response.use {
                        val source = it.body?.source()
                        when {
                            !it.isSuccessful -> Attempt(null, "http" + it.code)
                            source == null -> Attempt(null, "noBody")
                            !source.request(16_385) -> Attempt(null, "tooLarge")
                            else -> Attempt(source.readUtf8(), "ok")
                        }
                    }
                } catch (e: Exception) {
                    Attempt(null, e.javaClass.simpleName)
                }
                continuation.resume(attempt)
            }
        })
    }

    override fun close() {
        closed = true
        if (dnsExecutorHolder.isInitialized()) dnsExecutor.shutdownNow()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        /**
         * Gap between request *starts*. The provider's free tier is 1000 queries a day, so this is
         * politeness rather than a quota: 250ms still allows eight requests in two seconds instead
         * of eight and a half, and it is what lets a page of flags finish while the user watches.
         */
        private const val REQUEST_GAP_MS = 250L

        /**
         * Tried in order, first usable answer wins. Each returns a shape that [parseResponse] reads,
         * so a provider being blocked or rate-limited costs one round trip instead of the whole
         * flag.
         *
         * ip-api leads: measured at 0.6s for 5.180.82.45, the fastest of the five, and it answers
         * from networks where the HTTPS providers are blackholed. Plain HTTP is acceptable for a
         * two-letter country code, and the manifest permits cleartext. The four HTTPS providers
         * stay behind it as fallbacks, so no single host decides whether a row gets a flag.
         */
        internal val COUNTRY_ENDPOINTS = listOf(
            // HTTPS first. ipwho.is answers 5.180.82.45 in 0.3s and is the one that answers from
            // networks where the others are blocked, so it leads.
            "https://ipwho.is/{ip}",
            "https://ipapi.co/{ip}/json/",
            "https://ipinfo.io/{ip}/json",
            "https://api.ip.sb/geoip/{ip}",
            // Plain HTTP only as a last resort, after every TLS provider has been tried: measured
            // at 0.6s, the second fastest, and it answers from networks where the TLS ones are
            // refused. It is last because the request carries no address of the user's -- only the
            // provider's own IP is in the path -- but the answer still travels unencrypted and can
            // be altered in transit, so a row's country could be rewritten by whoever is on the
            // path. Two letters is all that is asked, and a forged one is a wrong flag, not a
            // secret: that is what the ordering buys, not a guarantee.
            "http://ip-api.com/json/{ip}?fields=status,message,countryCode",
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