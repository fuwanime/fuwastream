package com.fuwanime.data.manga

import okhttp3.Interceptor
import okhttp3.Response

/** Keeps api.mangadex.org under [perSecond] requests/second and retries once after a 429. */
class RateLimitInterceptor(
    private val perSecond: Int = 4,
    private val clock: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : Interceptor {
    private val sent = ArrayDeque<Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host != HOST) return chain.proceed(chain.request())
        throttle()
        val first = chain.proceed(chain.request())
        if (first.code != 429) return first
        val wait = retryAfterMs(first)
        first.close()
        sleep(wait)
        throttle()
        return chain.proceed(chain.request())
    }

    private fun throttle() {
        val wait = synchronized(sent) {
            val now = clock()
            while (sent.isNotEmpty() && now - sent.first() >= SECOND_NS) sent.removeFirst()
            val w = if (sent.size >= perSecond) (SECOND_NS - (now - sent.first())) / 1_000_000 + 1 else 0L
            sent.addLast(now + w * 1_000_000)
            w
        }
        if (wait > 0) sleep(wait)
    }

    private fun retryAfterMs(r: Response): Long {
        val secs = r.header("Retry-After")?.toLongOrNull()
            ?: r.header("X-RateLimit-Retry-After")?.toLongOrNull()?.let { it - epochSeconds() }
            ?: 1L
        return secs.coerceIn(0L, 10L) * 1000
    }

    private companion object {
        const val HOST = "api.mangadex.org"
        const val SECOND_NS = 1_000_000_000L
    }
}
