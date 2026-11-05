package com.fuwanime

import com.fuwanime.data.meta.AniSkipApi
import com.fuwanime.data.meta.SegmentKind
import com.fuwanime.data.meta.SkipRepository
import com.fuwanime.data.meta.SkipSegment
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkipRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: SkipRepository

    @Before fun setUp() {
        server.start()
        repo = SkipRepository(AniSkipApi.create(OkHttpClient(), server.url("/").toString()))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun filtersAndCaches() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"found":true,"results":[{"interval":{"startTime":84.5,"endTime":174.5},"skipType":"op","episodeLength":1420.0},{"interval":{"startTime":1300,"endTime":1390},"skipType":"ed","episodeLength":1300.0}]}""",
            ),
        )
        val first = repo.segments(31964, 1, 1_420_000)
        val second = repo.segments(31964, 1, 1_420_000)
        assertEquals(listOf(SkipSegment(SegmentKind.OP, 84_500, 174_500)), first)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test fun failureIsEmpty() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo.segments(1, 1, 1_420_000).isEmpty())
    }

    @Test fun notFoundIsEmpty() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"found":false,"results":[]}"""))
        assertTrue(repo.segments(1, 2, 1_420_000).isEmpty())
    }
}
