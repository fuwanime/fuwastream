package com.fuwanime

import com.fuwanime.data.manga.isWebtoonId
import com.fuwanime.data.manga.isWebtoonImage
import com.fuwanime.data.manga.parseWebtoonDetail
import com.fuwanime.data.manga.parseWebtoonEpisodes
import com.fuwanime.data.manga.parseWebtoonPages
import com.fuwanime.data.manga.parseWebtoonSearch
import com.fuwanime.data.manga.pickChapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("webtoon/$name")!!.readText()

    @Test fun searchParsesOriginalsAndCanvas() {
        val list = parseWebtoonSearch(res("search.html"))
        assertTrue(list.any { it.id == "wt:w:7321" && it.title == "Pasutri Kentang" && it.author == "GAMZA" })
        assertTrue(list.any { it.id == "wt:c:1120026" })
        assertTrue(list.none { it.title.isBlank() })
        assertTrue(list.first().coverUrl!!.startsWith("https://webtoon-phinf.pstatic.net/"))
    }

    @Test fun detailReadsMetaTags() {
        val m = parseWebtoonDetail(res("detail.html"), "wt:w:7321", "webtoon/slice-of-life/pasutri-kentang")
        assertEquals("wt:w:7321", m.id)
        assertEquals("Pasutri Kentang", m.title)
        assertEquals("GAMZA", m.author)
        assertTrue(m.description.contains("kentang menikah"))
        assertEquals("Manhwa", m.type)
        assertTrue(m.coverUrl!!.startsWith("https://swebtoon-phinf.pstatic.net/") || m.coverUrl!!.startsWith("https://webtoon-phinf.pstatic.net/"))
    }

    @Test fun episodesFromMobileApi() {
        val chapters = pickChapters(parseWebtoonEpisodes(res("episodes.json"), "id"), "id")
        assertEquals("wt:/id/slice-of-life/pasutri-kentang/prolog/viewer?title_no=7321&episode_no=1/", chapters.first().id)
        assertEquals("1", chapters.first().number)
        assertEquals("PROLOG", chapters.first().title)
        assertEquals("id", chapters.first().language)
        assertEquals("WEBTOON", chapters.first().group)
        assertTrue(chapters.first().publishAt > 0)
        assertTrue(chapters.first().pages > 0)
    }

    @Test fun canvasEpisodesParse() {
        val chapters = parseWebtoonEpisodes(res("canvas_episodes.json"), "id")
        assertTrue(chapters.isNotEmpty())
        assertTrue(chapters.first().id.startsWith("wt:/en/canvas/") || chapters.first().id.startsWith("wt:/id/canvas/"))
    }

    @Test fun pagesFromViewerHtml() {
        val pages = parseWebtoonPages(res("viewer.html"))
        assertTrue(pages.size > 100)
        assertTrue(pages.first().contains("webtoon-phinf.pstatic.net/"))
        assertTrue(pages.none { it.contains("webtoons-static.pstatic.net") })
    }

    @Test fun idHelpers() {
        assertTrue(isWebtoonId("wt:w:7321"))
        assertTrue(isWebtoonId("wt:/id/x/y/viewer?title_no=1&episode_no=2/"))
        assertTrue(isWebtoonImage("https://webtoon-phinf.pstatic.net/2025/x.jpg"))
        assertTrue(isWebtoonImage("https://swebtoon-phinf.pstatic.net/2025/x.jpg"))
        assertEquals(false, isWebtoonImage("https://uploads.mangadex.org/x.jpg"))
        assertEquals(false, isWebtoonId("km:one-piece"))
    }
}
