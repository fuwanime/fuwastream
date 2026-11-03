package com.fuwanime.data.remote

import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/** The API client: [base] plus a disk cache that stands in when the API cannot be reached. */
fun apiCacheClient(base: OkHttpClient, cache: Cache?, status: NetworkStatus): OkHttpClient = base.newBuilder()
    .cache(cache)
    .addInterceptor(OfflineFallback(status))
    .addNetworkInterceptor(StoreAll)
    .build()

/**
 * Stores every API response whatever the server says. `max-age=0` (not `no-cache`) keeps normal requests going to the
 * network while still letting FORCE_CACHE serve the entry. Stream URLs carry expiring tokens, so they are never stored.
 */
object StoreAll : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val stream = "episode/streamnew/" in chain.request().url.encodedPath
        return response.newBuilder()
            .header("Cache-Control", if (stream) "no-store" else "public, max-age=0")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
    }
}

/** For the cache-first read: answers from the cache only (504 when there is nothing stored). */
object ForceCache : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
}

/**
 * When the API is unreachable (no network, 503/52x, a Cloudflare challenge), serves the last stored response instead.
 * Other HTTP errors pass through untouched; with nothing stored the original failure is kept.
 */
class OfflineFallback(private val status: NetworkStatus) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            return fromCache(chain) ?: throw e
        }
        if (!unreachable(response)) {
            if (response.networkResponse != null && response.isSuccessful) status.markNetworkOk()
            return response
        }
        // OkHttp refuses a second request while this body is open; error pages are small, so keep a copy in memory.
        val copy = response.newBuilder().body(response.body?.let { it.bytes().toResponseBody(it.contentType()) }).build()
        return fromCache(chain) ?: copy
    }

    private fun fromCache(chain: Interceptor.Chain): Response? {
        val cached = chain.proceed(chain.request().newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
        if (cached.code == 504) {
            cached.close()
            return null
        }
        status.markCacheServed(cached.receivedResponseAtMillis)
        return cached
    }

    private fun unreachable(response: Response) =
        response.code == 503 || response.code in 520..529 || response.header("cf-mitigated") != null
}
