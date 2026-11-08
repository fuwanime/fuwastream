package com.fuwanime

import com.fuwanime.data.backup.mergeDaily
import com.fuwanime.data.backup.mergeHistory
import com.fuwanime.data.backup.mergeSearches
import com.fuwanime.data.backup.newFavorites
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeTest {
    private fun h(ep: String, at: Long) = WatchHistoryEntity(ep, "a", "", "", "", "1", "", "", 10, 100, at)
    private fun fav(id: String, title: String) = FavoriteEntity(id, title, "", "", "TV", "", "", "", 0)

    @Test fun historyNewerBackupWins() = assertEquals(listOf(h("e1", 200)), mergeHistory(listOf(h("e1", 100)), listOf(h("e1", 200))))

    @Test fun historyNewerLocalKept() = assertEquals(emptyList<WatchHistoryEntity>(), mergeHistory(listOf(h("e1", 300)), listOf(h("e1", 200))))

    @Test fun historyMissingLocallyAdded() = assertEquals(listOf(h("e2", 50)), mergeHistory(emptyList(), listOf(h("e2", 50))))

    @Test fun mergeDailyTakesMax() {
        assertEquals(
            listOf(WatchDailyEntity(11, 7000)),
            mergeDaily(listOf(WatchDailyEntity(10, 5000)), listOf(WatchDailyEntity(10, 3000), WatchDailyEntity(11, 7000))),
        )
        assertEquals(listOf(WatchDailyEntity(10, 3000)), mergeDaily(listOf(WatchDailyEntity(10, 1000)), listOf(WatchDailyEntity(10, 3000))))
    }

    @Test fun mergeDailyIsIdempotent() {
        val local = listOf(WatchDailyEntity(10, 1000), WatchDailyEntity(12, 9000))
        val backup = listOf(WatchDailyEntity(10, 3000), WatchDailyEntity(11, 7000), WatchDailyEntity(12, 2000))
        val once = (local.associateBy { it.day } + mergeDaily(local, backup).associateBy { it.day }).values.toList()
        assertEquals(emptyList<WatchDailyEntity>(), mergeDaily(once, backup))
    }

    @Test fun searchesNewerWins() {
        val merged = mergeSearches(listOf(SearchHistoryEntity("frieren", 10)), listOf(SearchHistoryEntity("frieren", 20), SearchHistoryEntity("dandadan", 5)))
        assertEquals(setOf(SearchHistoryEntity("frieren", 20), SearchHistoryEntity("dandadan", 5)), merged.toSet())
    }

    @Test fun favoritesLocalNotOverwritten() =
        assertEquals(listOf(fav("b", "B")), newFavorites(listOf(fav("a", "Lokal")), listOf(fav("a", "Lama"), fav("b", "B"))))
}
