package com.v2ray.ang.handler

import android.content.Context
import com.google.gson.JsonParser
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import libv2ray.CoreCallbackHandler
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Reads the country a profile actually exits from, by standing the profile up and asking from inside it.
 *
 * The name-based lookup this sits beside asks an IP service what country the address behind a
 * hostname is. That answer is true of the address and often false of the server: a profile whose
 * hostname sits behind a CDN resolves to a CDN edge, so a user in Germany reaching a German server
 * through a Cloudflare front is shown the flag of whichever country that edge is in. The row says
 * the United States while the traffic goes to Germany, and no reader of that answer can tell the
 * two apart, because the answer is a real country for a machine that is not the server.
 *
 * So the question is asked the only way it can be asked correctly: bring this profile up on a port
 * of its own, ask an IP service from inside it, and read where the traffic emerged. That is the
 * same reading the connection panel shows, and it holds for a CDN front, a relay, or a bare address.
 *
 * The cost is a whole core per probe, so this does not fill the list. The list keeps the fast
 * name-based answer and this replaces a row with the measured one once a connection exists. A probe
 * never shares a port with the running connection, so it cannot answer on its behalf.
 */
internal object ExitCountryProbe {

    /** How long one profile gets, core start and request together. */
    private const val PROBE_BUDGET_MS = 6_000L

    /** Where the probe asks from inside the tunnel. Asked without an address, on purpose. */
    private const val ENDPOINT = "https://ipwho.is/"

    private const val LOOPBACK = "127.0.0.1"
    /** How long the core is given to open its SOCKS listener, inside the overall budget. */
    private const val READY_BUDGET_MS = 2_500L

    /** How often the listener is checked while waiting for it. */
    private const val READY_POLL_MS = 100L

    private const val TAG = "NiNG-country-probe"

    /**
     * The country the given profile exits from, or null when the profile cannot be stood up, the
     * service cannot be reached through it, or the answer carries no country.
     *
     * The profile is never logged: its address, port and name are the credential half of a
     * connection, and only the answer is of any use to a reader.
     */
    suspend fun countryOf(context: Context, guid: String): String? = runInterruptible(Dispatchers.IO) {
        // Every step of a probe says so. A probe costs a core and the user is waiting on a row,
        // so a failure that leaves no trace is indistinguishable from a button that does nothing.
        LogUtil.w(TAG, "country probe: asked")
        val port = Utils.findRandomFreePort()
        LogUtil.w(TAG, "country probe: free port $port")
        val answer = askThrough(context, guid, port)
        if (answer == null) {
            LogUtil.w(TAG, "country probe: no answer")
            null
        } else {
            val code = parseCountry(answer)
            LogUtil.w(TAG, "country probe: read a country, normalized=${code != null}")
            code
        }
    }

    /**
     * Stands the profile up on [port], reads one answer through it, and takes it back down.
     *
     * The core belongs to this call alone. A probe that outlived its port, or answered from the
     * port the running connection owned, would put the country of one profile on the row of
     * another -- so the core is stopped in a finally and the port override cleared with it.
     */
    private fun askThrough(context: Context, guid: String, port: Int): String? {
        val controller = try {
            CoreNativeManager.newCoreController(ExitProbeCallback())
        } catch (e: Exception) {
            LogUtil.w(TAG, "country probe: no controller, ${e.javaClass.simpleName}")
            return null
        }
        // The override is set for the build and cleared with the core, so a config built for a
        // normal connection can never pick it up.
        CoreConfigManager.socksPortOverride = port
        var started = false
        return try {
            val result = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
            if (!result.status || result.content.isEmpty()) {
                LogUtil.w(TAG, "country probe: no config for this profile")
                return null
            }
            // A zero tun file descriptor keeps the tunnel on its SOCKS listener alone, so the
            // probe never claims the device network the way a VPN run would.
            controller.startLoop(result.content, 0)
            started = true
            awaitReady(port, READY_BUDGET_MS)
            val answer = requestThrough(port)
            LogUtil.w(TAG, "country probe: through the tunnel, got=${answer != null}")
            answer
        } catch (e: Exception) {
            LogUtil.w(TAG, "country probe failed, ${e.javaClass.simpleName}")
            null
        } finally {
            if (started) {
                try {
                    controller.stopLoop()
                } catch (_: Exception) {
                    // The core is discarded either way; a failure here must not mask the answer.
                }
            }
            CoreConfigManager.socksPortOverride = null
        }
    }

    /**
     * Waits for the core to have its SOCKS listener open, up to [budgetMs].
     *
     * startLoop returns before the core is listening, so a request made straight after it is
     * refused: the answer then says nothing about the country, only that the question was asked
     * too early. This is the same wait CoreServiceManager does after a real start, and it is
     * bounded, so a core that never comes up costs the budget once rather than hanging a row.
     */
    private fun awaitReady(port: Int, budgetMs: Long) {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (canConnect(port)) return
            Thread.sleep(READY_POLL_MS)
        }
    }

    private fun canConnect(port: Int): Boolean = try {
        // use returns whatever the block returns and connect returns Unit, so the block has to
        // answer the question itself rather than leaning on the value that falls out.
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOOPBACK, port), READY_POLL_MS.toInt())
            true
        }
    } catch (_: Exception) {
        false
    }

    /**
     * One request through the SOCKS listener the profile was just brought up on.
     *
     * The client is built per probe and shut down with it, so a probe cannot leave a connection
     * pool holding a socket to a core that has since gone away.
     */
    private fun requestThrough(port: Int): String? {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOOPBACK, port)))
            .connectTimeout(PROBE_BUDGET_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_BUDGET_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_BUDGET_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        return try {
            val request = Request.Builder().url(ENDPOINT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: IOException) {
            // The failure is named because the difference between a refused connection, a timeout
            // and a TLS failure is the difference between a core that is not listening yet, a
            // server that needs a moment, and a profile that cannot reach the service at all.
            // None of them names the server, so none of them puts a credential in the log.
            LogUtil.w(TAG, "country probe: request failed, ${e.javaClass.simpleName}")
            null
        } catch (_: IllegalArgumentException) {
            // The endpoint was not accepted: nothing to read and nothing worth reporting.
            null
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /**
     * The country out of the answer, or null when it carries none.
     *
     * Read through the same normalisation the list lookup uses, so both readings of a country come
     * from one place and a service that changes its field names cannot make one path fail while
     * the other silently succeeds.
     */
    private fun parseCountry(content: String): String? = try {
        val json = JsonParser.parseString(content)
        if (!json.isJsonObject) return null
        sequenceOf("country_code", "countryCode", "country")
            .mapNotNull { key -> json.asJsonObject.get(key) }
            .filter { it.isJsonPrimitive }
            .map { it.asString }
            .mapNotNull(ProfileCountry::normalize)
            .firstOrNull()
    } catch (_: Exception) {
        null
    }

    /**
     * The core speaks through this, and a probe wants no more than to know the core came up.
     *
     * Status output is dropped rather than logged: it carries the server address, so a probe that
     * logged it would put a profile credential in the log once per row.
     */
    private class ExitProbeCallback : CoreCallbackHandler {
        override fun startup(): Long = 0L

        override fun shutdown(): Long = 0L

        override fun onEmitStatus(l: Long, s: String?): Long = 0L
    }
}
