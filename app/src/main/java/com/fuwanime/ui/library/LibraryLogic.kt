package com.fuwanime.ui.library

import com.fuwanime.data.DownloadItem
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchHistoryEntity

enum class FavoriteSort(val label: String) { ADDED("Ditambahkan"), LAST_WATCHED("Terakhir ditonton"), TITLE("A–Z") }

fun sortFavorites(favs: List<FavoriteEntity>, lastWatched: Map<String, Long>, sort: FavoriteSort): List<FavoriteEntity> = favs

fun newEpisodeCount(latestKnown: Double?, watchedNumbers: List<String>): Int = -1

fun newBadgeLabel(count: Int): String? = "stub"

fun watchedDownloads(downloads: List<DownloadItem>, history: Map<String, WatchHistoryEntity>): List<DownloadItem> = downloads
