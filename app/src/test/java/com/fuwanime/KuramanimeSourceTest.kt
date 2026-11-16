package com.fuwanime

import com.fuwanime.data.remote.KuramanimeSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KuramanimeSourceTest {
    private val server = MockWebServer()
    private lateinit var source: KuramanimeSource

    @Before fun setUp() {
        server.start()
        source = KuramanimeSource(OkHttpClient(), base = server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() = server.shutdown()

    private fun links(
        siteId: Int,
        slug: String,
        range: IntRange,
        singleQuoted: Boolean = false,
        relative: Boolean = false,
    ): String = range.joinToString("") {
        val href = (if (relative) "" else "https://v20.kuramanime.ing") + "/anime/$siteId/$slug/episode/$it"
        if (singleQuoted) "<a href='$href'>Ep $it</a>" else "<a href=\"$href\">Ep $it</a>"
    }

    @Test fun forwardsTokenCookiesToPlayer() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "__cflb=abc123; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "cf_clearance=xyz; Path=/")
                .setBody("token123"),
        )
        server.enqueue(MockResponse().setBody("""<video><source src="https://cdn/720.mp4" size="720" type="video/mp4"></video>"""))

        val stream = source.stream("kr:50:one-piece:1")

        assertEquals(listOf("720p"), stream.sources.map { it.quality })
        assertEquals("https://cdn/720.mp4", stream.sources.single().url)
        val token = server.takeRequest()
        assertEquals("/assets/Ks6sqSgloPTlHMl.txt", token.path)
        assertNotNull(token.getHeader("X-Fuck-ID"))
        val player = server.takeRequest()
        assertEquals("/anime/50/one-piece/episode/1?Ub3BzhijicHXZdv=token123&C2XAPerzX1BM7V9=kuramadrive&page=1", player.path)
        val cookie = player.getHeader("Cookie").orEmpty()
        assertTrue(cookie, cookie.contains("__cflb=abc123"))
        assertTrue(cookie, cookie.contains("cf_clearance=xyz"))
        assertEquals("authorization=kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur", player.body.readUtf8())
    }

    @Test fun readsEveryEpisodePage() = runBlocking {
        server.enqueue(MockResponse().setBody(links(4921, "avatar-the-last-airbender", 1..13)))
        server.enqueue(MockResponse().setBody(links(4921, "avatar-the-last-airbender", 14..20)))
        server.enqueue(MockResponse().setBody("<html></html>"))

        val episodes = source.episodes("kr:4921:avatar-the-last-airbender")

        assertEquals((1..20).map(Int::toString), episodes.map { it.number })
        assertEquals(3, server.requestCount)
        assertEquals("/anime/4921/avatar-the-last-airbender/episode/1?page=1", server.takeRequest().path)
        assertEquals("/anime/4921/avatar-the-last-airbender/episode/1?page=2", server.takeRequest().path)
        assertEquals("/anime/4921/avatar-the-last-airbender/episode/1?page=3", server.takeRequest().path)
    }

    @Test fun fallsBackToDetailPagesWhenEpisodeOneIsMissing() = runBlocking {
        // The live detail page lists episodes in a single-quoted popover, often with relative URLs.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(links(50, "one-piece", 989..1001, singleQuoted = true)))
        server.enqueue(MockResponse().setBody(links(50, "one-piece", 1002..1014, singleQuoted = true, relative = true)))
        server.enqueue(MockResponse().setBody("<html></html>"))

        val episodes = source.episodes("kr:50:one-piece")

        assertEquals((989..1014).map(Int::toString), episodes.map { it.number })
        assertEquals(4, server.requestCount)
        assertEquals("/anime/50/one-piece/episode/1?page=1", server.takeRequest().path)
        assertEquals("/anime/50/one-piece?page=1", server.takeRequest().path)
        assertEquals("/anime/50/one-piece?page=2", server.takeRequest().path)
        assertEquals("/anime/50/one-piece?page=3", server.takeRequest().path)
    }

    @Test fun detailDropsTrailingCommaFromGenres() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<div class="anime__details__title"><h3><span>Avatar: The Last Airbender</span>Avatar</h3></div>""" +
                    """<a href="https://v20.kuramanime.ing/properties/genre/action">Action,</a>""" +
                    """<a href="https://v20.kuramanime.ing/properties/genre/adventure"> Adventure , </a>""",
            ),
        )

        val anime = source.detail("kr:4921:avatar-the-last-airbender")

        assertEquals("Avatar", anime.title)
        assertEquals(listOf("Action", "Adventure"), anime.genres)
    }
}
