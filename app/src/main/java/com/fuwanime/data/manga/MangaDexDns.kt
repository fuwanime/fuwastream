package com.fuwanime.data.manga

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
data class DohResponse(@Json(name = "Status") val status: Int = -1, @Json(name = "Answer") val answer: List<DohAnswer> = emptyList())

@JsonClass(generateAdapter = true)
data class DohAnswer(@Json(name = "type") val type: Int = 0, @Json(name = "TTL") val ttl: Int = 0, @Json(name = "data") val data: String = "")

fun isMangaDexHost(host: String): Boolean =
    host == "mangadex.org" || host.endsWith(".mangadex.org") || host.endsWith(".mangadex.network")

/** IPv4/IPv6 addresses (A = 1, AAAA = 28) from a DNS-over-HTTPS JSON answer; CNAME hops are skipped. */
fun dohAddresses(r: DohResponse): List<String> =
    if (r.status != 0) emptyList() else r.answer.filter { it.type == 1 || it.type == 28 }.map { it.data }

/**
 * Indonesian ISP resolvers (Internet Positif) answer MangaDex hosts with a block page, so the app can't reach
 * the API or images. MangaDex hosts are resolved over HTTPS at Google's resolver instead; every other host,
 * and any DoH failure, uses the system resolver.
 */
class MangaDexDns(
    private val fallback: Dns = Dns.SYSTEM,
    private val now: () -> Long = System::currentTimeMillis,
    private val query: (String) -> DohResponse? = ::googleDoh,
) : Dns {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isMangaDexHost(hostname)) return fallback.lookup(hostname)
        cache[hostname]?.takeIf { now() < it.first }?.let { return it.second }
        val response = runCatching { query(hostname) }.getOrNull()
        val ips = response?.let(::dohAddresses).orEmpty().mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
        if (ips.isEmpty()) return fallback.lookup(hostname)
        val ttl = response!!.answer.filter { it.type == 1 || it.type == 28 }.minOf { it.ttl }.coerceIn(60, 3600)
        cache[hostname] = now() + ttl * 1000L to ips
        return ips
    }

    private companion object {
        // dns.google by IP, so the resolver itself never depends on the ISP's DNS.
        val GOOGLE = listOf("8.8.8.8", "8.8.4.4").map { InetAddress.getByName(it) }
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String) = if (hostname == "dns.google") GOOGLE else Dns.SYSTEM.lookup(hostname)
                })
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
        val adapter by lazy { Moshi.Builder().build().adapter(DohResponse::class.java) }

        fun googleDoh(host: String): DohResponse? {
            val req = Request.Builder().url("https://dns.google/resolve?name=$host&type=A").header("Accept", "application/dns-json").build()
            return client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string()?.let(adapter::fromJson) else null }
        }
    }
}
