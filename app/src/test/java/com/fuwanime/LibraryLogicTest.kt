package com.fuwanime

import com.fuwanime.data.DownloadItem
import com.fuwanime.data.DownloadState
import com.fuwanime.data.local.DownloadEntity
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.library.FavoriteSort
import com.fuwanime.ui.library.newBadgeLabel
import com.fuwanime.ui.library.newEpisodeCount
import com.fuwanime.ui.library.sortFavorites
import com.fuwanime.ui.library.watchedDownloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryLogicTest {

    private fun fav(id: String, title: String = id, addedAt: Long = 0) =
        FavoriteEntity(id, title, "", "", "TV", "ONGOING", "2026", "", addedAt)

    private fun hist(episodeId: String, position: Long, duration: Long = 1_000) = WatchHistoryEntity(
        episodeId = episodeId, animeId = "a", animeTitle = "", animePoster = "", animeCover = "",
        episodeNumber = "1", episodeTitle = "", episodeThumbnail = "",
        positionMs = position, durationMs = duration, updatedAt = 0,
    )

    private fun dl(episodeId: String, state: DownloadState) = DownloadItem(
        DownloadEntity(episodeId, "a", "", "", "1", "", "", "720p", 0, "", 0),
        state, 100, 100,
    )

    @Test fun sortAdded_newestFirst() {
        val out = sortFavorites(listOf(fav("a", addedAt = 1), fav("b", addedAt = 3), fav("c", addedAt = 2)), emptyMap(), FavoriteSort.ADDED)
        assertEquals(listOf("b", "c", "a"), out.map { it.animeId })
    }

    @Test fun sortLastWatched_unwatchedLastByAdded() {
        val favs = listOf(fav("a", addedAt = 1), fav("b", addedAt = 5), fav("c", addedAt = 2), fav("d", addedAt = 9))
        val out = sortFavorites(favs, mapOf("a" to 10L, "c" to 20L), FavoriteSort.LAST_WATCHED)
        assertEquals(listOf("c", "a", "d", "b"), out.map { it.animeId })
    }

    @Test fun sortTitle_caseInsensitive() {
        val out = sortFavorites(listOf(fav("1", "Naruto"), fav("2", "bleach"), fav("3", "One Piece")), emptyMap(), FavoriteSort.TITLE)
        assertEquals(listOf("bleach", "Naruto", "One Piece"), out.map { it.title })
    }

    @Test fun newCount_baseline12watched10_is2() = assertEquals(2, newEpisodeCount(12.0, listOf("9", "10")))
    @Test fun newCount_noBaseline_is0() = assertEquals(0, newEpisodeCount(null, listOf("3")))
    @Test fun newCount_noHistory_is0() = assertEquals(0, newEpisodeCount(12.0, emptyList()))
    @Test fun newCount_nonNumericIgnored() = assertEquals(2, newEpisodeCount(11.0, listOf("OVA", "1-2", "", "9")))
    @Test fun newCount_fractionalFloored() = assertEquals(0, newEpisodeCount(12.5, listOf("12")))
    @Test fun newCount_watchedBeyondBaseline_is0() = assertEquals(0, newEpisodeCount(10.0, listOf("12")))

    @Test fun badge_zeroNull() = assertNull(newBadgeLabel(0))
    @Test fun badge_nine() = assertEquals("9 baru", newBadgeLabel(9))
    @Test fun badge_ten() = assertEquals("9+ baru", newBadgeLabel(10))

    @Test fun watchedDownloads_onlyDoneAndFinished() {
        val downloads = listOf(dl("done-fin", DownloadState.DONE), dl("done-half", DownloadState.DONE), dl("run-fin", DownloadState.RUNNING), dl("done-none", DownloadState.DONE))
        val history = mapOf("done-fin" to hist("done-fin", 950), "done-half" to hist("done-half", 400), "run-fin" to hist("run-fin", 999))
        assertEquals(listOf("done-fin"), watchedDownloads(downloads, history).map { it.entity.episodeId })
    }
}
