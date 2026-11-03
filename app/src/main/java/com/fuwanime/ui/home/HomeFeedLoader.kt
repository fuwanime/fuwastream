package com.fuwanime.ui.home

import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.LatestRelease
import com.fuwanime.data.SortOrder
import com.fuwanime.util.Day
import com.fuwanime.util.friendlyMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

const val OFFLINE_EMPTY = "Belum ada data tersimpan. Sambungkan internet lalu coba lagi."

/** One load of the Beranda feed. [popular]/[latest] are null when that call failed; [error] only when nothing useful came back. */
data class FeedResult(
    val popular: List<Anime>?,
    val today: List<Anime>,
    val season: List<Anime>,
    val latest: List<LatestRelease>?,
    val error: Throwable?,
) {
    val hasData: Boolean get() = !popular.isNullOrEmpty() || season.isNotEmpty() || !latest.isNullOrEmpty()
}

class HomeFeedLoader(private val anime: AnimeRepository, private val today: Day) {

    suspend fun fetch(cacheOnly: Boolean): FeedResult = coroutineScope {
        val popularJob = async { anime.explore(page = 0, sort = SortOrder.POPULAR, cacheOnly = cacheOnly) }
        val latestJob = async { anime.latestReleases(LATEST_LIMIT, cacheOnly = cacheOnly) }
        // Every day's schedule together approximates "currently airing this season".
        val dayJobs = Day.entries.map { day -> day to async { anime.schedule(day.apiValue, cacheOnly = cacheOnly) } }

        val popular = popularJob.await()
        val days = dayJobs.map { (day, job) -> day to job.await() }
        val season = days.flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .sortedByDescending { it.views }
        FeedResult(
            popular = popular.getOrNull(),
            today = days.first { it.first == today }.second.getOrDefault(emptyList()),
            season = season,
            latest = latestJob.await().getOrNull(),
            error = popular.exceptionOrNull()?.takeIf { season.isEmpty() },
        )
    }

    private companion object {
        const val LATEST_LIMIT = 20
    }
}

/**
 * Applies a load to the feed; null means "ignore it" (an empty or late cache read). Cached data shows at once and keeps
 * [HomeFeed.stale] until the network answers; a failed network load never replaces data already on screen.
 */
fun HomeFeed.reduce(
    result: FeedResult,
    fromCache: Boolean,
    online: Boolean,
    /** A network load the offline fallback answered from cache: show it, but keep it marked stale. */
    servedFromCache: Boolean = false,
): HomeFeed? = when {
    fromCache && (!stale && hasData) -> null
    fromCache && (result.error != null || !result.hasData) -> null
    fromCache -> HomeFeed(
        popular = result.popular.orEmpty(),
        today = result.today,
        thisSeason = result.season,
        latest = result.latest.orEmpty(),
        loading = false,
        syncing = true,
        stale = true,
    )
    result.error == null -> HomeFeed(
        popular = result.popular ?: popular,
        today = result.today,
        thisSeason = result.season,
        latest = result.latest ?: latest,
        loading = false,
        stale = servedFromCache,
    )
    hasData -> copy(loading = false, refreshing = false, syncing = false, error = null)
    else -> copy(
        loading = false,
        refreshing = false,
        syncing = false,
        error = if (online) result.error.friendlyMessage() else OFFLINE_EMPTY,
    )
}

/** Back online: refresh only when what is on screen came from the cache. */
fun shouldAutoRefresh(feed: HomeFeed): Boolean = feed.stale
