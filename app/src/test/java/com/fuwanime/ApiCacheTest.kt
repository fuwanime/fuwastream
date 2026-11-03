package com.fuwanime

import com.fuwanime.data.remote.ForceCache
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.remote.apiCacheClient
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.ConnectException

class ApiCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val status = NetworkStatus()
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        server.start()
        client = apiCacheClient(OkHttpClient(), Cache(tmp.newFolder("api"), 1L * 1024 * 1024), status)
    }

    @After fun tearDown() = runCatching { server.shutdown() }.let { }

    private fun get(path: String, c: OkHttpClient = client): Response =
        c.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test fun storedResponseServedWhenServerDies() {
        server.enqueue(MockResponse().setBody("A").setHeader("Cache-Control", "no-store"))
        assertEquals("A", get("/3/2/explore/movie").body!!.string())
        server.shutdown()
        assertEquals("A", get("/3/2/explore/movie").body!!.string())
        assertNotNull(status.state.value.cacheServedAt)
    }

    @Test fun streamResponseIsNeverStored() {
        server.enqueue(MockResponse().setBody("S"))
        get("/3/2/episode/streamnew/9").close()
        server.shutdown()
        assertThrows(IOException::class.java) { get("/3/2/episode/streamnew/9") }
    }

    @Test fun offlineCacheMissRethrowsOriginalIOException() {
        server.shutdown()
        assertThrows(ConnectException::class.java) { get("/3/2/explore/movie") }
    }

    @Test fun notFoundIsNotReplacedByCache() {
        server.enqueue(MockResponse().setBody("A"))
        server.enqueue(MockResponse().setResponseCode(404))
        get("/3/2/explore/movie").close()
        assertEquals(404, get("/3/2/explore/movie").use { it.code })
    }

    @Test fun serviceUnavailableFallsBackToCache() {
        server.enqueue(MockResponse().setBody("A"))
        server.enqueue(MockResponse().setResponseCode(503))
        get("/3/2/explore/movie").close()
        assertEquals("A", get("/3/2/explore/movie").body!!.string())
    }

    @Test fun cloudflareChallengeFallsBackToCache() {
        server.enqueue(MockResponse().setBody("A"))
        server.enqueue(MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge"))
        get("/3/2/explore/movie").close()
        assertEquals("A", get("/3/2/explore/movie").body!!.string())
    }

    @Test fun successAfterFallbackClearsCacheServedAt() {
        server.enqueue(MockResponse().setBody("A"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("B"))
        get("/3/2/explore/movie").close()
        get("/3/2/explore/movie").close()
        assertNotNull(status.state.value.cacheServedAt)
        assertEquals("B", get("/3/2/explore/movie").body!!.string())
        assertNull(status.state.value.cacheServedAt)
    }

    @Test fun forceCacheNeverHitsNetwork() {
        val cacheOnly = client.newBuilder().addInterceptor(ForceCache).build()
        assertEquals(504, get("/3/2/explore/movie", cacheOnly).use { it.code })
        assertEquals(0, server.requestCount)
    }
}
