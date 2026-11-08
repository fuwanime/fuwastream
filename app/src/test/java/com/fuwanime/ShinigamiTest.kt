package com.fuwanime

import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.ShChapterListDto
import com.fuwanime.data.manga.ShDetailDto
import com.fuwanime.data.manga.ShListDto
import com.fuwanime.data.manga.ShPagesDto
import com.fuwanime.data.manga.isShinigamiId
import com.fuwanime.data.manga.needsDoh
import com.fuwanime.data.manga.sameTitle
import com.fuwanime.data.manga.shChapterNumber
import com.fuwanime.data.manga.toChapter
import com.fuwanime.data.manga.toManga
import com.fuwanime.data.manga.toPageUrls
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShinigamiTest {
    private val moshi = Moshi.Builder().build()
    private fun json(name: String) = javaClass.classLoader!!.getResource("shinigami/$name")!!.readText()

    @Test fun listMapsToPrefixedManga() {
        val list = moshi.adapter(ShListDto::class.java).fromJson(json("list.json"))!!
        val m = list.data.first().toManga()
        assertEquals("sh:c0f1d049-ff7f-474d-8c6a-3a55e4c44147", m.id)
        assertTrue(m.title.isNotBlank())
        assertTrue(m.coverUrl!!.startsWith("https://assets.shngm.id/"))
        assertEquals(1000, list.meta.totalRecord)
    }

    @Test fun detailMapsGenresAuthorStatus() {
        val m = moshi.adapter(ShDetailDto::class.java).fromJson(json("detail.json"))!!.data!!.toManga()
        assertEquals(MangaStatus.ONGOING, m.status)
        assertEquals(2019, m.year)
        assertEquals(listOf("Action", "Adventure", "Fantasy"), m.tags.take(3))
        assertEquals("Wuer Manhua, Ye Xiao", m.author)
        assertEquals("Manhua", m.type)
    }

    @Test fun chapterMapsToPrefixedReadableChapter() {
        val c = moshi.adapter(ShChapterListDto::class.java).fromJson(json("chapters.json"))!!.data.first().toChapter()
        assertEquals("sh:5b7e9580-0a15-455e-b141-6cf918ae90f0", c.id)
        assertEquals("932", c.number)
        assertEquals("id", c.language)
        assertEquals("Shinigami", c.group)
        assertTrue(c.pages > 0)
        assertEquals(null, c.externalUrl)
    }

    @Test fun pageUrlsJoinBasePathFile() {
        val urls = moshi.adapter(ShPagesDto::class.java).fromJson(json("pages.json"))!!.data!!.toPageUrls()
        assertEquals(
            "https://assets.shngm.id/chapter/manga_c0f1d049-ff7f-474d-8c6a-3a55e4c44147/chapter_5b7e9580-0a15-455e-b141-6cf918ae90f0/00-979358.jpg",
            urls.first(),
        )
        assertEquals(3, urls.size)
    }

    @Test fun chapterNumbers() {
        assertEquals("932", shChapterNumber(932.0))
        assertEquals("10.5", shChapterNumber(10.5))
    }

    @Test fun idRouting() {
        assertTrue(isShinigamiId("sh:abc"))
        assertFalse(isShinigamiId("32d76d19-8a05-4db0-9fc2-e0b0648fe9d0"))
    }

    @Test fun titleMatchingIgnoresCaseAndPunctuation() {
        assertTrue(sameTitle("Solo Leveling", "Solo Leveling", emptyList()))
        assertTrue(sameTitle("Na Honjaman Level-Up", "Solo Leveling", listOf("Na Honjaman Level-Up", "Only I Level Up")))
        assertFalse(sameTitle("One Piece", "One Punch Man", emptyList()))
        // A short alt-title fragment (comma split) must not pull in a different title.
        assertFalse(sameTitle("Love", "Love Is War", listOf("Love")))
        assertTrue(sameTitle("Love", "Love", emptyList()))
    }

    @Test fun dohCoversShinigamiAndMangaDex() {
        assertTrue(needsDoh("api.shngm.io"))
        assertTrue(needsDoh("assets.shngm.id"))
        assertTrue(needsDoh("api.mangadex.org"))
        assertFalse(needsDoh("xyz-api.animein.net"))
        assertFalse(needsDoh("evilshngm.io"))
    }
}
