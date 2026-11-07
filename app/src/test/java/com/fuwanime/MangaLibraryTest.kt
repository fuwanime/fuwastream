package com.fuwanime

import com.fuwanime.data.backup.mergeMangaHistory
import com.fuwanime.data.backup.newMangaFavorites
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.continueReading
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaLibraryTest {
    private fun h(chapter: String, manga: String, at: Long) = MangaHistoryEntity(chapter, manga, "T-$manga", null, "Ch $chapter", 3, 20, at)
    private fun fav(id: String, title: String) = MangaFavoriteEntity(id, title, null, 0)

    @Test fun continueReadingLatestPerManga() {
        val rows = listOf(h("a1", "A", 10), h("a2", "A", 20), h("b1", "B", 15))
        assertEquals(listOf(h("a2", "A", 20), h("b1", "B", 15)), continueReading(rows))
    }

    @Test fun mergeMangaHistoryNewerWins() {
        assertEquals(listOf(h("c1", "A", 200)), mergeMangaHistory(listOf(h("c1", "A", 100)), listOf(h("c1", "A", 200))))
        assertEquals(emptyList<MangaHistoryEntity>(), mergeMangaHistory(listOf(h("c1", "A", 300)), listOf(h("c1", "A", 200))))
        assertEquals(listOf(h("c2", "A", 50)), mergeMangaHistory(emptyList(), listOf(h("c2", "A", 50))))
    }

    @Test fun newMangaFavoritesKeepsLocal() =
        assertEquals(listOf(fav("b", "B")), newMangaFavorites(listOf(fav("a", "Lokal")), listOf(fav("a", "Lama"), fav("b", "B"))))
}
