package com.fuwanime

import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.pickChapters
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaTag
import com.fuwanime.ui.manga.dedupeByTitle
import com.fuwanime.data.manga.withChapter
import com.fuwanime.data.manga.ChapterLang
import com.fuwanime.ui.manga.readKeys
import com.fuwanime.ui.manga.historyLanguage
import com.fuwanime.ui.manga.chapterRanges
import com.fuwanime.ui.manga.genreTagId
import com.fuwanime.ui.manga.rangeLabel
import com.fuwanime.ui.manga.readerChapter
import com.fuwanime.ui.manga.readerStartIndex
import com.fuwanime.ui.manga.resumeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaUiLogicTest {
    private fun ch(id: String, number: String, pages: Int = 10, external: String? = null) =
        MangaChapter(id, number, null, "en", null, pages, external, 0)
    private fun h(chapterId: String, at: Long) = MangaHistoryEntity(chapterId, "m", "M", null, "Ch. 1", 3, 10, at)

    private val chapters = listOf(ch("ext", "1", external = "https://mangaplus.example"), ch("c2", "2"), ch("c3", "3"))

    @Test fun resumeTargetPrefersHistory() =
        assertEquals("c3", resumeTarget(chapters, listOf(h("c2", 10), h("c3", 20)))?.id)

    @Test fun resumeTargetFallsBackWhenChapterGone() =
        assertEquals("c2", resumeTarget(chapters, listOf(h("removed", 30)))?.id)

    @Test fun resumeTargetFindsSameNumberFromAnotherSource() {
        val shown = listOf(ch("sh:1", "1"), ch("sh:50", "50"))
        val all = shown + ch("md50", "50.0")
        assertEquals("sh:50", resumeTarget(shown, listOf(h("md50", 10)), all)?.id)
    }

    @Test fun readKeysMarkSameNumberFromAnotherSource() {
        val all = listOf(ch("md50", "50"), ch("sh:50", "50.0"), ch("sh:51", "51"))
        assertEquals(setOf("50"), readKeys(listOf(h("md50", 1)), all))
    }

    @Test fun historyLanguageIsThatOfLatestReadChapter() {
        val all = listOf(MangaChapter("e150", "150", null, "en", null, 5, null, 0), MangaChapter("i1", "1", null, "id", null, 5, null, 0))
        assertEquals(ChapterLang.EN, historyLanguage(listOf(h("i1", 1), h("e150", 9)), all))
        assertNull(historyLanguage(listOf(h("gone", 9)), all))
    }

    @Test fun withChapterSwapsInTheOpenedSource() {
        val list = listOf(ch("sh:1", "1"), ch("sh:2", "2"), ch("sh:3", "3"))
        assertEquals(listOf("sh:1", "md2", "sh:3"), withChapter(list, ch("md2", "2")).map { it.id })
        assertEquals(listOf("sh:1", "sh:2", "sh:3"), withChapter(list, ch("x", "9")).map { it.id })
    }

    @Test fun resumeTargetWithoutHistoryIsFirstReadable() = assertEquals("c2", resumeTarget(chapters, emptyList())?.id)

    @Test fun resumeTargetNullWhenNothingReadable() = assertNull(resumeTarget(listOf(ch("ext", "1", external = "x")), emptyList()))

    @Test fun readerStartIndexClamps() {
        assertEquals(0, readerStartIndex(null, 10))
        assertEquals(4, readerStartIndex(4, 10))
        assertEquals(9, readerStartIndex(25, 10))
        assertEquals(0, readerStartIndex(-3, 10))
        assertEquals(0, readerStartIndex(5, 0))
    }

    @Test fun pickChaptersPrefersReadableOverNewerExternal() {
        val scan = MangaChapter("scan", "5", null, "en", "G", 20, null, 10)
        val official = MangaChapter("mp", "5", null, "en", null, 0, "https://mangaplus.example", 99)
        assertEquals(listOf("scan"), pickChapters(listOf(scan, official), "en").map { it.id })
    }

    @Test fun readerChapterUsesListedChapter() =
        assertEquals("c2", readerChapter(chapters, "c2", savedLabel = null).id)

    @Test fun readerChapterSurvivesChapterMissingFromList() {
        val c = readerChapter(chapters, "gone", savedLabel = "Ch. 7")
        assertEquals("gone", c.id)
        assertEquals("Ch. 7", c.title)
        assertNull(c.number)
        assertNull(c.externalUrl)
    }

    private fun many(n: Int) = (1..n).map { ch("c$it", it.toString()) }

    @Test fun noRangesUpTo100Chapters() = assertEquals(emptyList<IntRange>(), chapterRanges(many(100)))

    @Test fun rangesOf100() {
        val r = chapterRanges(many(250))
        assertEquals(listOf(0..99, 100..199, 200..249), r)
        assertEquals("1–100", rangeLabel(many(250), r[0]))
        assertEquals("201–250", rangeLabel(many(250), r[2]))
    }

    @Test fun rangeLabelUsesChapterNumbers() {
        val list = listOf(ch("a", "0.5"), ch("b", "1")) + (2..101).map { ch("c$it", it.toString()) }
        assertEquals("0.5–99", rangeLabel(list, chapterRanges(list)[0]))
    }

    private val tags = listOf(MangaTag("t1", "Action", "genre"), MangaTag("t2", "Slice of Life", "genre"))

    @Test fun genreTagIdMatchesIgnoringCase() = assertEquals("t2", genreTagId("slice of life", tags))

    @Test fun genreTagIdNullWhenUnknown() = assertNull(genreTagId("Aksi", tags))

    private fun m(id: String, title: String, alt: List<String> = emptyList()) =
        Manga(id, title, null, "", emptyList(), MangaStatus.ONGOING, null, null, alt)

    @Test fun dedupeKeepsFirstOfSameTitle() =
        assertEquals(listOf("sh:1", "x"), dedupeByTitle(listOf(m("sh:1", "Solo Leveling"), m("km:1", "solo leveling"), m("x", "Berserk"))).map { it.id })

    @Test fun dedupeMatchesAltTitles() =
        assertEquals(listOf("a"), dedupeByTitle(listOf(m("a", "Ore dake Level Up na Ken"), m("b", "Solo Leveling", listOf("Ore dake Level Up na Ken")))).map { it.id })
}
