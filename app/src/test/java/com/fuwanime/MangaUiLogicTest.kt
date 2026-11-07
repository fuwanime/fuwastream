package com.fuwanime

import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.MangaChapter
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
}
