package com.fuwanime

import com.fuwanime.data.manga.*
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaMappingTest {
    private val moshi = Moshi.Builder().build()

    private fun <T> load(name: String, type: Class<T>): T {
        val text = checkNotNull(javaClass.classLoader?.getResource("mangadex/$name")) { "missing $name" }.readText()
        return checkNotNull(moshi.adapter(type).fromJson(text))
    }

    private fun ch(id: String, n: String?, lang: String = "en", at: Long = 0, pages: Int = 5, ext: String? = null) =
        MangaChapter(id, n, null, lang, null, pages, ext, at)

    @Test fun mapsTitleFallbacks() {
        val list = load("manga_list.json", MangaListDto::class.java).data
        assertEquals("Sousou no Frieren", list[0].toManga("en", 256).title)
        assertEquals("Chainsaw Man", list[1].toManga("en", 256).title)
    }

    @Test fun emptyArrayLocalizedMapDoesNotBreakList() {
        val list = load("manga_list.json", MangaListDto::class.java).data
        assertEquals(2, list.size)
        assertEquals("", list[1].toManga("id", 256).description)
    }

    @Test fun mapsCoverUrl() {
        val m = load("manga_list.json", MangaListDto::class.java).data[0].toManga("en", 256)
        assertEquals("https://uploads.mangadex.org/covers/m1/abc.jpg.256.jpg", m.coverUrl)
        assertEquals("Kanehito Yamada", m.author)
        assertEquals(listOf("Fantasy"), m.tags)
        assertEquals(MangaStatus.ONGOING, m.status)
    }

    @Test fun descriptionPrefersAppLanguage() {
        val d = load("manga_list.json", MangaListDto::class.java).data[0]
        assertEquals("I", d.toManga("id", 256).description)
        assertEquals("E", d.toManga("zh", 256).description)
    }

    @Test fun parsesDetailFeedAtHome() {
        assertEquals(MangaStatus.HIATUS, load("manga_detail.json", MangaDetailDto::class.java).data!!.toManga("en", 512).status)
        val chapters = load("feed.json", FeedDto::class.java).data.map { it.toChapter() }
        assertEquals("Grup A", chapters[0].group)
        assertEquals(1695988800000L, chapters[0].publishAt)
        assertEquals("https://mangaplus.shueisha.co.jp/viewer/1", chapters[1].externalUrl)
        assertNull(chapters[1].number)
        val h = load("at_home.json", AtHomeDto::class.java)
        assertEquals("h1", h.chapter.hash)
        assertEquals(2, h.chapter.dataSaver.size)
    }

    @Test fun pickChaptersPrefersLanguageThenNewest() {
        val r = pickChapters(listOf(ch("a", "1", "id", 10), ch("b", "1", "en", 20), ch("c", "1", "id", 30)), "id")
        assertEquals(listOf("c"), r.map { it.id })
    }

    @Test fun pickChaptersFallsBackToOtherLanguage() {
        val r = pickChapters(listOf(ch("a", "1", "id", 10), ch("b", "2", "en", 20)), "id")
        assertEquals(listOf("a", "b"), r.map { it.id })
    }

    @Test fun pickChaptersOrdersNumerically() {
        val r = pickChapters(listOf(ch("a", "10"), ch("b", "2"), ch("c", "10.5"), ch("d", "1"), ch("o", null, at = 5)), "en")
        assertEquals(listOf("1", "2", "10", "10.5", null), r.map { it.number })
    }

    @Test fun pickChaptersKeepsExternal() {
        val r = pickChapters(listOf(ch("a", "1", ext = "https://x"), ch("b", "2", pages = 3)), "en")
        assertEquals(2, r.size)
        assertEquals("b", firstReadable(r)?.id)
        assertNull(firstReadable(listOf(ch("a", "1", ext = "https://x"), ch("z", "2", pages = 0))))
    }

    @Test fun nextChapterAtEnd() {
        val l = listOf(ch("a", "1"), ch("b", "2"))
        assertNull(nextChapter(l, "b"))
        assertNull(previousChapter(l, "a"))
        assertEquals("b", nextChapter(l, "a")?.id)
        assertEquals("a", previousChapter(l, "b")?.id)
    }

    @Test fun pageUrlsDataSaver() {
        val d = listOf("F1.png"); val s = listOf("f1.jpg")
        assertEquals(listOf("B/data-saver/H/f1.jpg"), pageUrls("B", "H", d, s, true))
        assertEquals(listOf("B/data/H/F1.png"), pageUrls("B", "H", d, s, false))
    }

    @Test fun chapterLanguage() {
        assertEquals("id", chapterLanguageFor("id"))
        listOf("ms", "en", "zh").forEach { assertEquals("en", chapterLanguageFor(it)) }
    }
}
