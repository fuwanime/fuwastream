package com.fuwanime

import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaDexApi
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaType
import com.fuwanime.data.manga.MangaOrder
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.RateLimitInterceptor
import com.fuwanime.util.UiTextException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class MangaRepositoryTest {
    private val server = MockWebServer()
    private var clock = 0L
    private lateinit var repo: MangaRepository

    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResource("mangadex/$name")).readText()
    private fun json(body: String) = MockResponse().setBody(body)

    private fun repoWith(client: OkHttpClient = OkHttpClient()) =
        MangaRepository(MangaDexApi.create(client, server.url("/").toString()), appLanguage = { "id" }, now = { clock })

    @Before fun setUp() {
        server.start()
        repo = repoWith()
    }

    @After fun tearDown() = server.shutdown()

    @Test fun browseSendsLanguageAndRatingFilters() = runBlocking {
        server.enqueue(json(fixture("manga_list.json")))
        val page = repo.browse(MangaOrder.POPULAR)
        val path = server.takeRequest().path!!
        listOf(
            "availableTranslatedLanguage%5B%5D=id", "availableTranslatedLanguage%5B%5D=en",
            "contentRating%5B%5D=safe", "contentRating%5B%5D=suggestive", "order%5BfollowedCount%5D=desc",
        ).forEach { assertTrue("$it in $path", path.contains(it)) }
        assertEquals(2, page.items.size)
        assertEquals("Sousou no Frieren", page.items[0].title)
    }

    @Test fun searchQueryCanOrderByRelevance() = runBlocking {
        server.enqueue(json(fixture("manga_list.json")))
        repo.browse(MangaOrder.RELEVANCE, query = "frieren")
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("order%5Brelevance%5D=desc"))
        assertTrue(path, path.contains("title=frieren"))
        assertFalse(path, path.contains("followedCount"))
    }

    @Test fun chaptersRequestRawLanguages() = runBlocking {
        server.enqueue(json(fixture("feed.json")))
        repo.chapters("m1")
        val path = server.takeRequest().path!!
        listOf("id", "en", "ja", "ko", "zh", "zh-hk").forEach { assertTrue(path, path.contains("translatedLanguage%5B%5D=$it")) }
    }

    @Test fun mergedChaptersKeepEveryLanguageAndCache() = runBlocking {
        server.enqueue(json(
            """{"total":2,"data":[{"id":"i1","attributes":{"chapter":"1","translatedLanguage":"id","pages":5}},{"id":"e1","attributes":{"chapter":"1","translatedLanguage":"en","pages":5}}]}""",
        ))
        val m = Manga("m1", "T", null, "", emptyList(), MangaStatus.ONGOING, null, null)
        assertEquals(2, repo.mergedChapters(m).size)
        repo.mergedChapters(m)
        assertEquals(1, server.requestCount)
    }

    @Test fun browseSendsOriginalLanguageForType() = runBlocking {
        server.enqueue(json(fixture("manga_list.json")))
        repo.browse(MangaOrder.POPULAR, type = MangaType.MANHWA)
        assertTrue(server.takeRequest().path!!.contains("originalLanguage%5B%5D=ko"))
    }

    @Test fun chaptersPagesThroughFeed() = runBlocking {
        fun feed(total: Int, id: String) = json(
            """{"total":$total,"data":[{"id":"$id","attributes":{"chapter":"${if (id == "a") 1 else 2}","translatedLanguage":"id","pages":5,"publishAt":"2023-09-29T12:00:00+00:00"}}]}""",
        )
        server.enqueue(feed(600, "a"))
        server.enqueue(feed(600, "b"))
        val chapters = repo.chapters("m1")
        assertEquals(listOf("a", "b"), chapters.map { it.id })
        assertTrue(server.takeRequest().path!!.contains("offset=0"))
        assertTrue(server.takeRequest().path!!.contains("offset=500"))
        assertEquals(2, server.requestCount)
    }

    @Test fun pagesCachesBaseUrl() = runBlocking {
        server.enqueue(json(fixture("at_home.json")))
        server.enqueue(json(fixture("at_home.json")))
        val first = repo.pages("c", false)
        assertEquals("https://s.example.network/data/h1/F1.png", first[0])
        repo.pages("c", false)
        assertEquals(1, server.requestCount)
        clock += 15 * 60_000L
        repo.pages("c", false)
        assertEquals(2, server.requestCount)
    }

    @Test fun pagesRefetchedAfterExpiry() = runBlocking {
        server.enqueue(json(fixture("at_home.json")))
        server.enqueue(json(fixture("at_home.json")))
        repo.pages("c", true)
        repo.pages("c", true, forceRefresh = true)
        assertEquals(2, server.requestCount)
    }

    private fun limited() = OkHttpClient.Builder()
        .addInterceptor(RateLimitInterceptor(clock = { 0L }, sleep = {}))
        .build()

    @Test fun retriesOnceOn429() = runBlocking {
        // The mock server's host is not api.mangadex.org, so rewrite it via a host-forcing interceptor.
        val client = hostForced(limited())
        val r = repoWith(client)
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(json(fixture("manga_list.json")))
        assertEquals(2, r.browse(MangaOrder.LATEST).items.size)
        assertEquals(2, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        try {
            r.browse(MangaOrder.LATEST)
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(429, e.code())
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun notFoundIsUiText() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        try {
            repo.detail("nope")
            fail("expected UiTextException")
        } catch (e: UiTextException) {
            assertEquals(R.string.manga_error_not_found, e.text.id)
        }
    }

    /** Wraps [inner] so the rate limiter sees host api.mangadex.org while the request still reaches the mock server. */
    private fun hostForced(inner: OkHttpClient): OkHttpClient {
        val mock = server.url("/")
        val spoof = okhttp3.Interceptor { chain ->
            val spoofed = chain.request().newBuilder()
                .url(chain.request().url.newBuilder().scheme("http").host("api.mangadex.org").port(80).build())
                .build()
            chain.proceed(spoofed)
        }
        val redirect = okhttp3.Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .url(chain.request().url.newBuilder().host(mock.host).port(mock.port).build()).build())
        }
        return OkHttpClient.Builder().addInterceptor(spoof)
            .addInterceptor(inner.interceptors.first()).addInterceptor(redirect).build()
    }
}
