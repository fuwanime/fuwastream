package com.fuwanime

import com.fuwanime.data.isMatureAnimeGenre
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaType
import com.fuwanime.data.manga.cleanGenreName
import com.fuwanime.data.manga.filterManga
import com.fuwanime.data.manga.htmlText
import com.fuwanime.data.manga.unescapeJsonText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaExploreFilterTest {
    private fun m(id: String, status: MangaStatus = MangaStatus.UNKNOWN, type: String? = null, tags: List<String> = emptyList()) =
        Manga(id, id, null, "", tags, status, null, null, type = type)

    private val items = listOf(
        m("ongoing", MangaStatus.ONGOING, "Manhwa", listOf("Action", "Yuri")),
        m("done", MangaStatus.COMPLETED, "Manga", listOf("Romance")),
        m("unknown", MangaStatus.UNKNOWN, null, emptyList()),
    )

    @Test fun statusKeepsMatchesAndTitlesWithNoStatus() =
        assertEquals(listOf("ongoing", "unknown"), filterManga(items, MangaStatus.ONGOING, null, emptySet()).map { it.id })

    @Test fun typeKeepsMatchesAndUntypedTitles() =
        assertEquals(listOf("done", "unknown"), filterManga(items, null, MangaType.MANGA, emptySet()).map { it.id })

    @Test fun genresMustAllBePresentAfterAliasing() {
        // "Girls Love" asks for the title a source tagged "Yuri".
        assertEquals(listOf("ongoing"), filterManga(items, null, null, setOf("Girls Love")).map { it.id })
        assertEquals(emptyList<String>(), filterManga(items, null, null, setOf("Girls Love", "Romance")).map { it.id })
    }

    @Test fun aliasesUnifySourceSpellings() {
        assertEquals("Boys Love", cleanGenreName("Shounen Ai"))
        assertEquals("Boys Love", cleanGenreName("YAOI"))
        assertEquals("School Life", cleanGenreName("school"))
        assertEquals("Action", cleanGenreName(" \"Action\" "))
    }

    @Test fun explicitMangaGenresAreMature() {
        listOf("Loli", "Shota", "Incest", "Hentai", "Smut").forEach { assertTrue(it, isMatureAnimeGenre(it)) }
        listOf("Romance", "Yuri", "Harem").forEach { assertFalse(it, isMatureAnimeGenre(it)) }
    }

    @Test fun hexAndAstralEntitiesDecode() {
        assertEquals("Berserker's", htmlText("Berserker&#x27;s"))
        assertEquals("😀", htmlText("&#x1F600;"))
        assertEquals("Hi… ’x’", htmlText("Hi&hellip; &rsquo;x&rsquo;"))
        assertEquals("bad &#xZZ; stays", htmlText("bad &#xZZ; stays"))
    }

    @Test fun jsonUnicodeEscapesBecomeCharacters() {
        assertEquals("Reader’s 全知 & ok", unescapeJsonText("Reader\\u2019s \\u5168\\u77e5 \\u0026 ok"))
        assertEquals("a/b \"q\" 'r'", unescapeJsonText("a\\/b \\\"q\\\" \\'r\\'"))
    }
}
