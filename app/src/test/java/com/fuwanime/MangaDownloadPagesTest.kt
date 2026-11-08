package com.fuwanime

import com.fuwanime.data.manga.downloadPages
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MangaDownloadPagesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var dir: File

    @Before fun setUp() { server.start(); dir = File(tmp.root, "ch") }
    @After fun tearDown() = server.shutdown()

    private fun urls(n: Int) = (1..n).map { server.url("/p$it.jpg").toString() }

    @Test fun savesEveryPageAndReportsProgress() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("img$it")) }
        val done = mutableListOf<Int>()
        assertTrue(downloadPages(urls(3), dir, OkHttpClient()) { d, _ -> done += d })
        assertEquals(listOf("001.jpg", "002.jpg", "003.jpg"), dir.list()!!.sorted())
        assertEquals(3, done.last())
    }

    @Test fun resumesOnlyMissingPages() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("img$it")) }
        downloadPages(urls(3), dir, OkHttpClient()) { _, _ -> }
        File(dir, "002.jpg").delete()
        server.enqueue(MockResponse().setBody("again"))
        downloadPages(urls(3), dir, OkHttpClient()) { _, _ -> }
        assertEquals(4, server.requestCount)
        assertEquals("again", File(dir, "002.jpg").readText())
    }

    @Test fun failedPageLeavesNoPartFile() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(IOException::class.java) { runBlocking { downloadPages(urls(1), dir, OkHttpClient()) { _, _ -> } } }
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun stopsWhenDeleted() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("x")) }
        var n = 0
        assertFalse(downloadPages(urls(3), dir, OkHttpClient(), keepGoing = { n++ < 1 }) { _, _ -> })
        assertEquals(1, server.requestCount)
    }
}
