package com.fuwanime

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.stats.computeStats
import com.fuwanime.ui.stats.weekIndex
import com.fuwanime.util.epochDay
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun day(y: Int, m: Int, d: Int): Int =
        epochDay(Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis, utc)
    private fun millisOf(day: Int) = day * 86_400_000L + 43_200_000L

    private val today = day(2026, 11, 4) // Wednesday

    private fun hist(anime: String, ep: String, position: Long, duration: Long, updatedAt: Long) = WatchHistoryEntity(
        episodeId = "$anime-$ep", animeId = anime, animeTitle = "", animePoster = "", animeCover = "",
        episodeNumber = ep, episodeTitle = "", episodeThumbnail = "",
        positionMs = position, durationMs = duration, updatedAt = updatedAt,
    )

    private fun fav(id: String, genres: String) = FavoriteEntity(id, id, "", "", "TV", "ONGOING", "2026", genres, 0)

    @Test fun totalAddsEstimateForOldHistory() {
        val d = day(2026, 10, 1)
        val daily = listOf(WatchDailyEntity(d, 1_000), WatchDailyEntity(d + 1, 2_000))
        val history = listOf(hist("a", "1", 600_000, 1_400_000, millisOf(d - 5)), hist("a", "2", 50_000, 1_400_000, millisOf(d + 1)))
        val s = computeStats(history, daily, emptyList(), today)
        assertEquals(603_000L, s.totalWatchedMs)
        assertTrue(s.isEstimated)
    }

    @Test fun noEstimateWhenAllHistoryAfterDaily() {
        val d = day(2026, 10, 1)
        val s = computeStats(listOf(hist("a", "1", 600_000, 1_400_000, millisOf(d))), listOf(WatchDailyEntity(d, 1_000)), emptyList(), today)
        assertEquals(1_000L, s.totalWatchedMs)
        assertFalse(s.isEstimated)
    }

    @Test fun emptyDailyEstimatesAll() {
        val s = computeStats(listOf(hist("a", "1", 600_000, 1_400_000, 0), hist("b", "1", 400_000, 1_400_000, 0)), emptyList(), emptyList(), today)
        assertEquals(1_000_000L, s.totalWatchedMs)
        assertTrue(s.isEstimated)
    }

    @Test fun monthlySixMonthsWithZeros() {
        val s = computeStats(emptyList(), listOf(WatchDailyEntity(day(2026, 9, 10), 120_000)), emptyList(), today)
        assertEquals(listOf("Jun", "Jul", "Agu", "Sep", "Okt", "Nov"), s.monthly.map { it.label })
        assertEquals(listOf(0, 0, 0, 2, 0, 0), s.monthly.map { it.minutes })
    }

    @Test fun activeWeeks_currentEmptyDoesNotBreak() {
        val daily = listOf(WatchDailyEntity(today - 7, 1_000), WatchDailyEntity(today - 14, 1_000))
        assertEquals(2, computeStats(emptyList(), daily, emptyList(), today).activeWeeks)
    }

    @Test fun activeWeeks_gapBreaks() {
        val daily = listOf(WatchDailyEntity(today, 1_000), WatchDailyEntity(today - 14, 1_000))
        assertEquals(1, computeStats(emptyList(), daily, emptyList(), today).activeWeeks)
    }

    @Test fun weekIndex_mondayStarts() {
        assertEquals(1, weekIndex(4))
        assertEquals(0, weekIndex(3))
    }

    @Test fun topGenresCaseInsensitiveTop3() {
        val favs = listOf(fav("1", "Action, Comedy"), fav("2", "action"), fav("3", "Drama"), fav("4", "comedy"), fav("5", "Action"))
        assertEquals(listOf("Action", "Comedy", "Drama"), computeStats(emptyList(), emptyList(), favs, today).topGenres)
    }

    @Test fun counts() {
        val history = listOf(hist("a", "1", 950, 1_000, 0), hist("a", "2", 100, 1_000, 0), hist("b", "1", 990, 1_000, 0))
        val s = computeStats(history, emptyList(), emptyList(), today)
        assertEquals(2, s.episodesFinished)
        assertEquals(2, s.animeWatched)
    }

    @Test fun hasData_dailyOnlyAfterHistoryCleared() =
        assertTrue(computeStats(emptyList(), listOf(WatchDailyEntity(today, 60_000)), emptyList(), today).hasData)

    @Test fun hasData_nothingRecorded() = assertFalse(computeStats(emptyList(), emptyList(), emptyList(), today).hasData)
}
