package com.fuwanime.data

import com.fuwanime.R
import com.fuwanime.util.UiText
import com.fuwanime.util.UiTextException
import androidx.annotation.StringRes
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.data.remote.KuramanimeSource
import com.fuwanime.data.remote.isKuramanimeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class SortOrder(val apiValue: String, @StringRes val label: Int) {
    POPULAR("views", R.string.common_sort_popular),
    TITLE("alphabet", R.string.common_sort_title),
    LATEST("latest", R.string.common_sort_latest),
}

/**
 * Remote data with a small in-memory cache so screens can show what they already know
 * (e.g. a poster tapped on Home) while the full payload loads.
 */
/** [cachedApi] answers from the disk cache only (see ForceCache); it backs the cache-first read on Beranda. */
class AnimeRepository(
    private val api: AnimeApiService,
    private val cachedApi: AnimeApiService = api,
    private val kuramanime: KuramanimeSource? = null,
    /** Global "Mature content" preference; false hides anime carrying explicit mature genres. */
    private val mature: suspend () -> Boolean = { false },
) {

    private val animeCache = ConcurrentHashMap<String, Anime>()

    /** animeId -> (page -> episodes newest-first). Pages are fixed windows of 30 from the API. */
    private val episodePages = ConcurrentHashMap<String, MutableMap<Int, List<Episode>>>()
    private val episodeLock = Mutex()

    fun cachedAnime(id: String): Anime? = animeCache[id]

    /** Seeds the cache with what a list already shows, so the next screen can draw it before loading. */
    fun prime(anime: Anime) {
        if (anime.id.isNotBlank()) animeCache.putIfAbsent(anime.id, anime)
    }

    private suspend fun visible(list: List<Anime>): List<Anime> =
        if (mature()) list else list.filterNot { it.hasMatureGenre() }

    /** Lower-case genre name -> id for the genre endpoint; fetched once. */
    @Volatile
    private var genreIds: Map<String, String>? = null

    private fun remember(list: List<Anime>): List<Anime> {
        list.forEach { anime -> animeCache.merge(anime.id, anime, ::mergeAnime) }
        return list
    }

    /** Detail responses carry fields (studio) list responses lack, and vice versa; feeds without genres keep known ones. */
    private fun mergeAnime(old: Anime, new: Anime) = new.copy(
        studio = new.studio.ifBlank { old.studio },
        nextAirTime = new.nextAirTime.ifBlank { old.nextAirTime },
        cover = new.cover.ifBlank { old.cover },
        poster = new.poster.ifBlank { old.poster },
        genres = new.genres.ifEmpty { old.genres },
    )

    suspend fun explore(
        page: Int,
        sort: SortOrder = SortOrder.POPULAR,
        keyword: String = "",
        type: String? = null,
        cacheOnly: Boolean = false,
    ): Result<List<Anime>> =
        io {
            val source = if (cacheOnly) cachedApi else api
            // The type endpoint ignores keywords, so searches use the keyword endpoint and callers filter by type.
            val response = if (type != null && keyword.isBlank()) {
                source.exploreByType(type = type, page = page, sort = sort.apiValue)
            } else {
                source.explore(page = page, sort = sort.apiValue, keyword = keyword.trim())
            }
            val items = response.data?.movie.orEmpty()
            remember(visible(items.map { it.toAnime() }.distinctBy { it.id }))
        }

    /**
     * Pool for "Anime Serupa": the most watched titles of this anime's two most specific genres, filtered by the
     * server. Falls back to the general popular list when the genre list is unavailable. Rank with [rankSimilar].
     */
    suspend fun similarCandidates(anime: Anime): Result<List<Anime>> = io {
        val ids = genreIds ?: try {
            api.genres().data?.genre.orEmpty()
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .associate { it.name.trim().lowercase() to it.id }
                .also { if (it.isNotEmpty()) genreIds = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        val seeds = seedGenres(anime.genres).mapNotNull { ids[it.trim().lowercase()] }
        val items = if (seeds.isEmpty()) {
            api.explore(page = 0, sort = SortOrder.POPULAR.apiValue, keyword = "").data?.movie.orEmpty()
        } else {
            coroutineScope {
                seeds.map { id -> async { api.exploreByGenre(id, 0, SortOrder.POPULAR.apiValue).data?.movie.orEmpty() } }
                    .awaitAll()
                    .flatten()
            }
        }
        remember(visible(items.map { it.toAnime() }.distinctBy { it.id }))
    }

    suspend fun latestReleases(limit: Int = 20, cacheOnly: Boolean = false): Result<List<LatestRelease>> = io {
        val releases = (if (cacheOnly) cachedApi else api).latestEpisodes(limit).data?.movie.orEmpty()
            .filter { it.id.isNotBlank() }
            .map { it.toLatestRelease() }
            .distinctBy { it.anime.id }
        remember(releases.map { it.anime })
        val merged = releases.map { r -> r.copy(anime = animeCache[r.anime.id] ?: r.anime) }
        if (mature()) merged else merged.filterNot { it.anime.hasMatureGenre() }
    }

    suspend fun schedule(day: String, cacheOnly: Boolean = false): Result<List<Anime>> = io {
        remember(visible((if (cacheOnly) cachedApi else api).schedule(day.lowercase()).data?.movie.orEmpty().map { it.toAnime() }))
    }

    /** Detail plus the franchise list ("S1", "Movie", …) that only the detail response carries. */
    suspend fun detailWithSeasons(id: String): Result<AnimeDetail> = io {
        if (isKuramanimeId(id)) {
            val kr = kuramanime ?: throw UiTextException(UiText(R.string.common_error_anime_not_found))
            val anime = kr.detail(id)
            AnimeDetail(animeCache.merge(id, anime, ::mergeAnime)!!, emptyList())
        } else {
            val data = api.detail(id).data
            val anime = data?.movie?.toAnime() ?: throw UiTextException(UiText(R.string.common_error_anime_not_found))
            val seasons = data.season.orEmpty()
                .filter { it.id.isNotBlank() }
                .map { SeasonEntry(it.toAnime(), it.season?.trim().orEmpty()) }
            AnimeDetail(animeCache.merge(id, anime, ::mergeAnime)!!, seasons)
        }
    }

    suspend fun detail(id: String): Result<Anime> = detailWithSeasons(id).map { it.anime }

    suspend fun episodePage(animeId: String, page: Int, forceRefresh: Boolean = false): Result<List<Episode>> = io {
        if (!forceRefresh) {
            episodePages[animeId]?.get(page)?.let { return@io it }
        }
        val episodes = if (isKuramanimeId(animeId)) {
            // Kuramanime lists all episodes in one page; return newest-first on page 0 to match app convention.
            if (page != 0) emptyList()
            else (kuramanime?.episodes(animeId).orEmpty()).sortedByDescending { it.sortKey }
        } else {
            api.episodes(animeId, page = page).data?.episode.orEmpty().map { it.toEpisode() }
        }
        episodeLock.withLock {
            episodePages.getOrPut(animeId) { ConcurrentHashMap() }[page] = episodes
        }
        episodes
    }

    /** Server-side substring match on the episode number, e.g. "10" -> 10, 100–109, 110, 210… */
    suspend fun searchEpisodes(animeId: String, query: String): Result<List<Episode>> = io {
        if (isKuramanimeId(animeId)) {
            val q = query.trim()
            val all = episodePages[animeId]?.get(0) ?: kuramanime?.episodes(animeId).orEmpty().sortedByDescending { it.sortKey }
            all.filter { it.number.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true) }
        } else {
            api.episodes(animeId, page = 0, search = query.trim()).data?.episode.orEmpty().map { it.toEpisode() }
        }
    }

    fun pageOf(animeId: String, episodeId: String): Int? =
        episodePages[animeId]?.entries?.firstOrNull { (_, eps) -> eps.any { it.id == episodeId } }?.key

    /**
     * Finds the chronologically adjacent episode, loading the neighbouring page when the
     * episode sits on a page boundary. [newer] = true means the next episode to watch.
     */
    suspend fun adjacentEpisode(animeId: String, episode: Episode, newer: Boolean): Episode? {
        val page = pageOf(animeId, episode.id) ?: locatePage(animeId, episode) ?: return null
        val list = episodePages[animeId]?.get(page) ?: return null
        val i = list.indexOfFirst { it.id == episode.id }
        if (i < 0) return null
        // Pages are newest-first, so "newer" means a lower index.
        return if (newer) {
            if (i > 0) list[i - 1]
            else if (page > 0) episodePage(animeId, page - 1).getOrNull()?.lastOrNull()
            else null
        } else {
            if (i < list.lastIndex) list[i + 1]
            else episodePage(animeId, page + 1).getOrNull()?.firstOrNull()
        }
    }

    /** For episodes opened from history: estimate the page from the episode number, then verify. */
    private suspend fun locatePage(animeId: String, episode: Episode): Int? {
        if (isKuramanimeId(animeId)) {
            val page = pageOf(animeId, episode.id)
            if (page != null) return page
            // ensure page 0 loaded
            episodePage(animeId, 0).getOrNull()
            return pageOf(animeId, episode.id)
        }
        val latest = episodePage(animeId, 0).getOrNull()?.firstOrNull()?.sortKey ?: return null
        pageOf(animeId, episode.id)?.let { return it }
        val target = episode.sortKey
        if (target == Double.MAX_VALUE) return null
        val estimate = ((latest - target) / AnimeApiService.EPISODE_PAGE_SIZE).toInt().coerceAtLeast(0)
        for (candidate in listOf(estimate, estimate - 1, estimate + 1).filter { it >= 0 }) {
            val eps = episodePage(animeId, candidate).getOrNull() ?: continue
            if (eps.any { it.id == episode.id }) return candidate
        }
        return null
    }

    suspend fun stream(episodeId: String): Result<EpisodeStream> = io {
        if (isKuramanimeId(episodeId)) {
            val kr = kuramanime ?: throw UiTextException(UiText(R.string.common_error_stream_unavailable))
            val stream = kr.stream(episodeId)
            if (stream.sources.isEmpty()) throw UiTextException(UiText(R.string.common_error_stream_unavailable))
            stream
        } else {
            val data = api.stream(episodeId).data ?: throw UiTextException(UiText(R.string.common_error_stream_unavailable))
            val servers = data.server.orEmpty().filter { it.link.isNotBlank() }
            // Prefer direct files; other types are embed pages the native player can't open.
            val direct = servers.filter { it.type.equals("direct", ignoreCase = true) }
            val sources = (direct.ifEmpty { servers }).map { it.toSource() }
                .distinctBy { it.url }
                .sortedByDescending { it.height }
            EpisodeStream(episode = data.episode?.toEpisode(), sources = sources)
        }
    }

    // --- Kuramanime browse/search (additive source) ---

    suspend fun kuramanimeBrowse(order: String = "popular", page: Int = 1): Result<List<Anime>> = io {
        val kr = kuramanime ?: return@io emptyList()
        remember(visible(kr.browse(order, page).items))
    }

    suspend fun kuramanimeLatest(page: Int = 1): Result<List<Anime>> = io {
        val kr = kuramanime ?: return@io emptyList()
        remember(visible(kr.latest(page).items))
    }

    suspend fun kuramanimeSearch(query: String): Result<List<Anime>> = io {
        val kr = kuramanime ?: return@io emptyList()
        remember(visible(kr.search(query)))
    }

    suspend fun kuramanimeSchedule(day: String): Result<List<Anime>> = io {
        val kr = kuramanime ?: return@io emptyList()
        remember(visible(kr.schedule(day)))
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
