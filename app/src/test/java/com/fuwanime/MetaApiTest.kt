package com.fuwanime

import com.fuwanime.data.meta.AiringEpisode
import com.fuwanime.data.meta.AniListApi
import com.fuwanime.data.meta.AniSkipApi
import com.fuwanime.data.meta.BY_ID_QUERY
import com.fuwanime.data.meta.GraphQlRequest
import com.fuwanime.data.meta.SEARCH_QUERY
import com.fuwanime.data.meta.SKIP_TYPES
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

fun fixture(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader!!.getResource(name)) { "missing fixture $name" }.readText()

class MetaApiTest {
    private val server = MockWebServer()
    private lateinit var api: AniListApi
    private lateinit var skip: AniSkipApi

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient()
        api = AniListApi.create(client, server.url("/").toString())
        skip = AniSkipApi.create(client, server.url("/").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parsesAniListSearchPage() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("anilist_search.json")))
        val r = api.query(GraphQlRequest(SEARCH_QUERY, mapOf("s" to "Boku no Hero Academia")))
        val m = r.data!!.page!!.media.single()
        assertEquals(21459, m.id)
        assertEquals(31964, m.idMal)
        assertEquals(2016, m.seasonYear)
        assertEquals(listOf("Super Power", "Twist"), m.tags.map { it.name })
        assertTrue(m.tags[1].isMediaSpoiler)
        assertEquals(AiringEpisode(6, 1762500000), m.nextAiringEpisode)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertTrue(sent.body.readUtf8().contains("\"s\":\"Boku no Hero Academia\""))
    }

    @Test fun parsesAniListById() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("anilist_by_id.json")))
        val r = api.query(GraphQlRequest(BY_ID_QUERY, mapOf("id" to 21459)))
        assertEquals(21459, r.data!!.media!!.id)
    }

    @Test fun parsesAniSkip() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"found":true,"results":[{"interval":{"startTime":84.5,"endTime":174.5},"skipType":"op","skipId":"x","episodeLength":1420.1}]}""",
            ),
        )
        val r = skip.skipTimes(31964, 1, SKIP_TYPES, 1420.0)
        assertTrue(r.found)
        assertEquals(84.5, r.results.single().interval.startTime, 0.0)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/v2/skip-times/31964/1?"))
        assertTrue(path, path.contains("types%5B%5D=mixed-op"))
        assertTrue(path, path.contains("episodeLength=1420.0"))
    }
}
