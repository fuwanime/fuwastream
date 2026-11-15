package com.fuwanime.ui.navigation

import android.content.Intent
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.motion.sharedEpisodeKey

/** Where the app should land when opened from a notification, shortcut or widget. */
sealed interface LaunchTarget {
    data class Anime(val id: String) : LaunchTarget
    data class Manga(val id: String) : LaunchTarget
    data object Search : LaunchTarget
    data object Schedule : LaunchTarget
    data class Resume(val episodeId: String) : LaunchTarget
    data class Episode(val animeId: String, val episodeId: String, val number: String) : LaunchTarget
    data class MangaChapter(val mangaId: String, val chapterId: String) : LaunchTarget
}

const val TAB_EXPLORE_SEARCH = "explore_search"
const val TAB_SCHEDULE = "schedule"

/** Resume beats a specific title, which beats a tab; blank values count as absent. */
fun parseLaunchTarget(
    animeId: String?,
    tab: String?,
    resumeEpisodeId: String?,
    mangaId: String? = null,
    episodeId: String? = null,
    episodeNumber: String? = null,
    chapterId: String? = null,
): LaunchTarget? {
    resumeEpisodeId?.takeIf { it.isNotBlank() }?.let { return LaunchTarget.Resume(it) }
    if (!animeId.isNullOrBlank() && !episodeId.isNullOrBlank()) return LaunchTarget.Episode(animeId, episodeId, episodeNumber.orEmpty())
    if (!mangaId.isNullOrBlank() && !chapterId.isNullOrBlank()) return LaunchTarget.MangaChapter(mangaId, chapterId)
    animeId?.takeIf { it.isNotBlank() }?.let { return LaunchTarget.Anime(it) }
    mangaId?.takeIf { it.isNotBlank() }?.let { return LaunchTarget.Manga(it) }
    return when (tab) {
        TAB_EXPLORE_SEARCH -> LaunchTarget.Search
        TAB_SCHEDULE -> LaunchTarget.Schedule
        else -> null
    }
}

/** Player route that resumes [e]; [origin] keys the shared thumbnail when a visible row launched it. */
fun playerRouteFor(e: WatchHistoryEntity, origin: String?): String = Routes.player(
    e.animeId, e.episodeId, e.episodeNumber,
    e.episodeThumbnail.ifBlank { e.animeCover },
    shared = origin?.let { sharedEpisodeKey(e.episodeId, it) },
)

/** Null when the episode is no longer in history, so the app simply opens on Beranda. */
fun resumeRoute(entry: WatchHistoryEntity?): String? = entry?.let { playerRouteFor(it, null) }

/**
 * Whether the launching intent's target should be applied: not after process death ([restored]) and not when
 * Recents relaunches the task with its original intent, or the user would be pulled back to that screen.
 */
fun isFreshLaunch(restored: Boolean, flags: Int): Boolean =
    !restored && (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
