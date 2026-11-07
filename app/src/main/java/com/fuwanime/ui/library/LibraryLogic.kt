package com.fuwanime.ui.library

import android.content.res.Resources
import androidx.annotation.StringRes
import com.fuwanime.R
import com.fuwanime.data.DownloadItem
import com.fuwanime.data.DownloadState
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchHistoryEntity
import kotlin.math.floor

enum class FavoriteSort(@StringRes val label: Int) {
    ADDED(R.string.library_sort_added),
    LAST_WATCHED(R.string.library_sort_last_watched),
    TITLE(R.string.library_sort_title),
}

/** [lastWatched] maps animeId to its newest history timestamp; unwatched favourites go last, newest added first. */
fun sortFavorites(favs: List<FavoriteEntity>, lastWatched: Map<String, Long>, sort: FavoriteSort): List<FavoriteEntity> = when (sort) {
    FavoriteSort.ADDED -> favs.sortedByDescending { it.addedAt }
    FavoriteSort.TITLE -> favs.sortedBy { it.title.lowercase() }
    FavoriteSort.LAST_WATCHED -> favs.sortedWith(
        compareByDescending<FavoriteEntity> { lastWatched[it.animeId] ?: Long.MIN_VALUE }.thenByDescending { it.addedAt },
    )
}

/**
 * Episodes released after the highest one watched. [latestKnown] is the newest episode the notification worker
 * has seen; without it, or without any watched episode, nothing counts as new.
 */
fun newEpisodeCount(latestKnown: Double?, watchedNumbers: List<String>): Int {
    val latest = latestKnown ?: return 0
    val watched = watchedNumbers.mapNotNull { it.trim().toDoubleOrNull() }.maxOrNull() ?: return 0
    return (floor(latest) - floor(watched)).toInt().coerceAtLeast(0)
}

fun newBadgeLabel(res: Resources, count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> res.getString(R.string.common_new_count_max)
    else -> res.getString(R.string.common_new_count, count)
}

/** Finished downloads of episodes the user has watched to the end; [history] is keyed by episodeId. */
fun watchedDownloads(downloads: List<DownloadItem>, history: Map<String, WatchHistoryEntity>): List<DownloadItem> =
    downloads.filter { it.state == DownloadState.DONE && history[it.entity.episodeId]?.isFinished == true }
