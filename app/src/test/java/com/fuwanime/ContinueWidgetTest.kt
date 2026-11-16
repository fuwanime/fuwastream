package com.fuwanime

import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.widget.ContinueEntry
import com.fuwanime.widget.mergeContinue
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWidgetTest {
    private fun watch(animeId: String, at: Long, pos: Long = 30, dur: Long = 100) =
        WatchHistoryEntity("e-$animeId-$at", animeId, "A $animeId", "poster", "cover", "1", "", "", pos, dur, at)

    private fun read(mangaId: String, at: Long, page: Int = 4, count: Int = 10) =
        MangaHistoryEntity("c-$mangaId-$at", mangaId, "M $mangaId", "cover", "Ch. 1", page, count, at)

    @Test fun newestFirstAcrossAnimeAndManga() {
        val merged = mergeContinue(listOf(watch("a1", 10), watch("a2", 30)), listOf(read("m1", 20), read("m2", 40)), limit = 3)
        assertEquals(listOf("m:m2", "a:a2", "m:m1"), merged.map { it.key })
    }

    @Test fun mangaProgressCountsTheCurrentPage() {
        assertEquals(0.5f, ContinueEntry.Manga(read("m1", 1, page = 4, count = 10)).progress)
        assertEquals(1f, ContinueEntry.Manga(read("m1", 1, page = 9, count = 10)).progress)
        assertEquals(0f, ContinueEntry.Manga(read("m1", 1, page = 0, count = 0)).progress)
    }

    @Test fun emptyWhenNothingWatchedOrRead() = assertEquals(emptyList<ContinueEntry>(), mergeContinue(emptyList(), emptyList(), 3))
}
