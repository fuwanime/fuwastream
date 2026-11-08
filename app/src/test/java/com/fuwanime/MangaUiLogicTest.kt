package com.fuwanime

import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.pickChapters
import com.fuwanime.ui.manga.chapterRanges
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
}
