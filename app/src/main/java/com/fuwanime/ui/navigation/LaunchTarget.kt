package com.fuwanime.ui.navigation

import com.fuwanime.data.local.WatchHistoryEntity

sealed interface LaunchTarget {
    data class Anime(val id: String) : LaunchTarget
    data object Search : LaunchTarget
    data object Schedule : LaunchTarget
    data class Resume(val episodeId: String) : LaunchTarget
}

fun parseLaunchTarget(animeId: String?, tab: String?, resumeEpisodeId: String?): LaunchTarget? = LaunchTarget.Search

fun resumeRoute(entry: WatchHistoryEntity?): String? = "stub"
