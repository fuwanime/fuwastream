package com.fuwanime

import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.isKomikuId
import com.fuwanime.data.manga.parseKomikuChapters
import com.fuwanime.data.manga.parseKomikuDetail
import com.fuwanime.data.manga.parseKomikuList
import com.fuwanime.data.manga.parseKomikuPages
import com.fuwanime.data.manga.htmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KomikuTest {
    private fun html(name: String) = javaClass.classLoader!!.getResource("komiku/$name")!!.readText()

    @Test fun popularListHasCoversAndPrefixedIds() {
        val list = parseKomikuList(html("list.html"))
        assertEquals(10, list.size)
        assertEquals("km:komik-one-piece-indo", list.first().id)
        assertEquals("One Piece", list.first().title)
        assertTrue(list.first().coverUrl!!.startsWith("https://thumbnail.komiku.org/"))
    }

    @Test fun searchListWithRelativeLinksAndEntities() {
        val list = parseKomikuList(html("search.html"))
        assertTrue(list.any { it.id == "km:berserk" })
        assertTrue(list.none { it.title.contains("&#") || it.coverUrl.orEmpty().contains("&#038;") })
    }

    @Test fun detailMapsInfoTable() {
        val m = parseKomikuDetail(html("detail.html"), "berserk")
        assertEquals("km:berserk", m.id)
        assertEquals("Berserk", m.title)
        assertEquals("Kentaro Miura", m.author)
        assertEquals("Manga", m.type)
        assertEquals(MangaStatus.ONGOING, m.status)
        assertTrue(m.tags.containsAll(listOf("Action", "Horror")))
        assertTrue(m.description.startsWith("Berserk mengisahkan"))
        assertTrue(m.coverUrl!!.startsWith("https://thumbnail.komiku"))
    }

    @Test fun chaptersFromTable() {
        val chapters = parseKomikuChapters(html("detail.html"))
        assertEquals(4, chapters.size)
        val first = chapters.first()
        assertEquals("km:/berserk-chapter-386/", first.id)
        assertEquals("386", first.number)
        assertEquals("id", first.language)
        assertEquals("Komiku", first.group)
        assertTrue(first.pages > 0)
        assertEquals("1", chapters.last().number)
    }

    @Test fun pagesSkipPromo() {
        val pages = parseKomikuPages(html("chapter.html"))
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.none { it.contains("promosi") })
        assertEquals("https://img.komiku.org/upload5/berserk/386/2026-08-23/1.webp", pages.first())
    }

    @Test fun ids() {
        assertTrue(isKomikuId("km:berserk"))
        assertFalse(isKomikuId("sh:x"))
    }

    @Test fun entities() = assertEquals("Berserker’s & \"Test\"", htmlText("Berserker&#8217;s &amp; &quot;Test&quot;"))
}
