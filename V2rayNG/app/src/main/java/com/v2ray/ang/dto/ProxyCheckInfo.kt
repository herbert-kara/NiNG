package com.v2ray.ang.dto

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * proxycheck.io /v2 payload: a `status` field plus one entry keyed by each queried IP.
 * The entry map is read dynamically because the key is the IP that was sent, so a typed
 * DTO could only ever model one of them.
 */
data class ProxyCheckInfo(
    val status: String?,
    val entries: Map<String, Entry>,
) {
    data class Entry(
        val proxy: String?,
        val type: String?,
        val risk: Int?,
        val isocode: String?,
        val asn: String?,
    )

    companion object {
        fun parse(body: String?): ProxyCheckInfo? = try {
            val root = JsonParser.parseString(body ?: return null).asJsonObject
            // keySet() rather than entrySet(): only the key list is needed, and entrySet() is not
            // available on this Gson version's JsonObject.
            val entries = root.keySet()
                .filter { it != "status" }
                .associateWith { key ->
                    val node = root.get(key).takeIf { it.isJsonObject }?.asJsonObject
                    Entry(
                        proxy = node?.text("proxy"),
                        type = node?.text("type"),
                        risk = node?.int("risk"),
                        isocode = node?.text("isocode"),
                        asn = node?.text("asn"),
                    )
                }
            ProxyCheckInfo(root.text("status"), entries)
        } catch (_: Exception) { null }
    }
}

private fun JsonObject.text(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(name: String): Int? = try {
    get(name)?.takeIf { it.isJsonPrimitive }?.asInt
} catch (_: Exception) { null }
