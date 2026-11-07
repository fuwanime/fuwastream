package com.fuwanime

import com.fuwanime.data.manga.RateLimitInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitInterceptorTest {
    private val sleeps = mutableListOf<Long>()
    private var calls = 0
    private val replies = ArrayDeque<Pair<Int, Map<String, String>>>()

    private fun client(perSecond: Int = 4, epoch: Long = 1000) = OkHttpClient.Builder()
        .addInterceptor(RateLimitInterceptor(perSecond, clock = { 0L }, sleep = { sleeps += it }, epochSeconds = { epoch }))
        .addInterceptor(Interceptor { chain ->
            calls++
            val (code, headers) = replies.removeFirstOrNull() ?: (200 to emptyMap())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("m").body("".toResponseBody())
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        }).build()

    private fun get(c: OkHttpClient, host: String = "api.mangadex.org") =
        c.newCall(Request.Builder().url("https://$host/x").build()).execute().close()

    @Test fun sixthCallsWaitForNextSecond() {
        val c = client()
        repeat(6) { get(c) }
        assertEquals(2, sleeps.size)
        assertTrue(sleeps.all { it in 1000..1001 })
    }

    @Test fun retryAfterCappedAndRetriedOnce() {
        replies += 429 to mapOf("Retry-After" to "30")
        replies += 429 to mapOf("Retry-After" to "30")
        get(client())
        assertEquals(2, calls)
        assertEquals(listOf(10_000L), sleeps)
    }

    @Test fun epochRetryAfterHonoured() {
        replies += 429 to mapOf("X-RateLimit-Retry-After" to "1003")
        get(client(epoch = 1000))
        assertEquals(2, calls)
        assertEquals(listOf(3000L), sleeps)
    }

    @Test fun otherHostsPassThrough() {
        val c = client(perSecond = 1)
        repeat(3) { get(c, "uploads.example.org") }
        assertTrue(sleeps.isEmpty())
        assertEquals(3, calls)
    }
}
