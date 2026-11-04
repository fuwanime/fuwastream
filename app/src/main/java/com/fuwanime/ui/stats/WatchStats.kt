package com.fuwanime.ui.stats

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity

data class MonthBar(val label: String, val minutes: Int)

data class WatchStats(
    val totalWatchedMs: Long,
    val isEstimated: Boolean,
    val episodesFinished: Int,
    val animeWatched: Int,
    val topGenres: List<String>,
    val monthly: List<MonthBar>,
    val activeWeeks: Int,
)

fun weekIndex(epochDay: Int): Int = -1

fun computeStats(
    history: List<WatchHistoryEntity>,
    daily: List<WatchDailyEntity>,
    favorites: List<FavoriteEntity>,
    todayEpochDay: Int,
): WatchStats = WatchStats(-1, false, -1, -1, emptyList(), emptyList(), -1)
