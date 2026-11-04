package com.fuwanime.ui.navigation

import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.motion.sharedEpisodeKey

/** Where the app should land when opened from a notification, shortcut or widget. */
sealed interface LaunchTarget {
    data class Anime(val id: String) : LaunchTarget
    data object Search : LaunchTarget
    data object Schedule : LaunchTarget
    data class Resume(val episodeId: String) : LaunchTarget
}

const val TAB_EXPLORE_SEARCH = "explore_search"
const val TAB_SCHEDULE = "schedule"

/** Resume beats a specific anime, which beats a tab; blank values count as absent. */
fun parseLaunchTarget(animeId: String?, tab: String?, resumeEpisodeId: String?): LaunchTarget? {
    resumeEpisodeId?.takeIf { it.isNotBlank() }?.let { return LaunchTarget.Resume(it) }
    animeId?.takeIf { it.isNotBlank() }?.let { return LaunchTarget.Anime(it) }
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

/** Stub: replaced in the fix pass. */
fun isFreshLaunch(restored: Boolean, flags: Int): Boolean = true
