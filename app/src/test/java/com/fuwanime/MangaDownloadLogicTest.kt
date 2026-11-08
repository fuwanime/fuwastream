package com.fuwanime

import com.fuwanime.data.local.MangaDownloadEntity
import com.fuwanime.data.manga.DownloadedManga
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.chapterDir
import com.fuwanime.data.manga.chaptersToQueue
import com.fuwanime.data.manga.groupDownloads
import com.fuwanime.data.manga.offlineChapters
import com.fuwanime.data.manga.pageFileName
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaDownloadLogicTest {
    private fun row(id: String, state: String, n: String? = "1", m: String = "m1", bytes: Long = 0) =
        MangaDownloadEntity(id, m, "T", null, n, "Ch. $n", "id", 3, 3, bytes, state, 0)
    private fun ch(id: String) = MangaChapter(id, "1", null, "id", null, 5, null, 0)

    @Test fun safePathForPrefixedIds() =
        assertEquals(File("/r/manga/sh_abc/sh_c-1"), chapterDir(File("/r"), "sh:abc", "sh:c-1"))

    @Test fun pageFileNames() {
        assertEquals("007.webp", pageFileName(7, "https://x/a/b.webp?token=1"))
        assertEquals("012.png", pageFileName(12, "https://x/p.PNG"))
        assertEquals("001.jpg", pageFileName(1, "https://x/image"))
    }

    @Test fun queueSkipsActiveAndDone() {
        val rows = mapOf("a" to row("a", "QUEUED"), "b" to row("b", "DONE"), "c" to row("c", "FAILED"), "d" to row("d", "RUNNING"))
        assertEquals(listOf("c", "e"), chaptersToQueue(listOf(ch("a"), ch("b"), ch("c"), ch("d"), ch("e")), rows).map { it.id })
    }

    @Test fun offlineChaptersDoneOnlyOnePerNumberInOrder() {
        val r = offlineChapters(listOf(row("md12", "DONE", n = "12"), row("sh:12", "DONE", n = "12.0"), row("sh:2", "DONE", n = "2"), row("q", "QUEUED", n = "1")))
        assertEquals(listOf("sh:2", "sh:12"), r.map { it.id })
    }

    @Test fun groupDownloadsCountsDoneAndPending() {
        val g = groupDownloads(listOf(row("a", "DONE", m = "m1", bytes = 10), row("b", "QUEUED", m = "m1"), row("c", "DONE", m = "m2", bytes = 5)))
        assertEquals(setOf("m1", "m2"), g.map { it.mangaId }.toSet())
        assertEquals(DownloadedManga("m1", "T", null, 1, 1, 10), g.first { it.mangaId == "m1" })
    }
}
