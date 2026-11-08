package com.fuwanime.ui.stats

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.util.epochDay
import java.util.Calendar
import java.util.TimeZone

/** [month] is 0-11; the screen names it in the app language. */
data class MonthBar(val month: Int, val minutes: Int)

data class WatchStats(
    val totalWatchedMs: Long,
    val isEstimated: Boolean,
    val episodesFinished: Int,
    val animeWatched: Int,
    val topGenres: List<String>,
    val monthly: List<MonthBar>,
    val activeWeeks: Int,
    /** False until anything has been watched; drives the empty state. */
    val hasData: Boolean,
)

/** Monday-based week number; epoch day 4 (1970-01-05) is the first Monday. */
fun weekIndex(epochDay: Int): Int = Math.floorDiv(epochDay + 3, 7)

/** (year, month 0-11) of an epoch day. */
private fun yearMonth(day: Int): Pair<Int, Int> {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * 86_400_000L }
    return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
}

/**
 * Watch time comes from [daily] (recorded since this version); history older than the first recorded day
 * adds its saved positions as an estimate.
 */
fun computeStats(
    history: List<WatchHistoryEntity>,
    daily: List<WatchDailyEntity>,
    favorites: List<FavoriteEntity>,
    todayEpochDay: Int,
): WatchStats {
    val firstDay = daily.minOfOrNull { it.day }
    val estimate = history
        .filter { firstDay == null || epochDay(it.updatedAt) < firstDay }
        .sumOf { if (it.durationMs > 0) it.positionMs.coerceAtMost(it.durationMs) else it.positionMs }

    val byMonth = daily.groupBy { yearMonth(it.day) }.mapValues { (_, days) -> days.sumOf { it.watchedMs } }
    val (year, month) = yearMonth(todayEpochDay)
    val monthly = (5 downTo 0).map { back ->
        val total = year * 12 + month - back
        val key = total / 12 to total % 12
        MonthBar(key.second, ((byMonth[key] ?: 0L) / 60_000).toInt())
    }

    val activeSet = daily.filter { it.watchedMs > 0 }.map { weekIndex(it.day) }.toSet()
    var week = weekIndex(todayEpochDay)
    if (week !in activeSet) week--
    var activeWeeks = 0
    while (week in activeSet) { activeWeeks++; week-- }

    val genreCounts = LinkedHashMap<String, Pair<String, Int>>()
    favorites.flatMap { it.genres.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.forEach { g ->
        val key = g.lowercase()
        val (name, n) = genreCounts[key] ?: (g to 0)
        genreCounts[key] = name to n + 1
    }
    val topGenres = genreCounts.values.sortedByDescending { it.second }.take(3).map { it.first }

    return WatchStats(
        totalWatchedMs = daily.sumOf { it.watchedMs } + estimate,
        isEstimated = estimate > 0,
        episodesFinished = history.count { it.isFinished },
        animeWatched = history.map { it.animeId }.distinct().size,
        topGenres = topGenres,
        monthly = monthly,
        activeWeeks = activeWeeks,
        hasData = history.isNotEmpty() || daily.any { it.watchedMs > 0 },
    )
}

data class DayBar(val day: Int, val minutes: Int)

/** The last [n] days ending [today], oldest first; days with nothing recorded are 0. */
fun lastNDays(daily: List<WatchDailyEntity>, today: Int, n: Int = 7): List<DayBar> {
    val byDay = daily.associate { it.day to it.watchedMs }
    return (today - n + 1..today).map { DayBar(it, ((byDay[it] ?: 0L) / 60_000).toInt()) }
}

data class WeekSummary(val watchedMs: Long, val episodes: Int, val days: List<DayBar>)

fun weekSummary(history: List<WatchHistoryEntity>, daily: List<WatchDailyEntity>, today: Int): WeekSummary {
    val window = today - 6..today
    return WeekSummary(
        watchedMs = daily.filter { it.day in window }.sumOf { it.watchedMs },
        episodes = history.count { it.isFinished && epochDay(it.updatedAt) in window },
        days = lastNDays(daily, today),
    )
}

/** Stat cards stack into one column when large text or a narrow screen would squeeze two side by side. */
fun statColumns(fontScale: Float, widthDp: Int): Int = if (fontScale >= 1.5f || widthDp < 360) 1 else 2

/**
 * The Detail episode header needs ~440dp at 1x for its title plus "Unduh semua", search and "Terbaru";
 * below that (in font-scaled dp) the actions get their own row so the title isn't squeezed.
 */
fun episodeHeaderStacked(fontScale: Float, widthDp: Int): Boolean = widthDp / fontScale < 440f
