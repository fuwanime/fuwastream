package com.fuwanime.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.DownloadItem
import com.fuwanime.data.DownloadRepository
import com.fuwanime.data.DownloadState
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.Episode
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.SeasonEntry
import com.fuwanime.data.rankSimilar
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.remote.AnimeApiService.Companion.EPISODE_PAGE_SIZE
import com.fuwanime.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Franchise entries worth linking from a detail page. */
fun relatedSeasons(currentId: String, seasons: List<SeasonEntry>): List<SeasonEntry> =
    seasons.filter { it.anime.id.isNotBlank() && it.anime.id != currentId }.distinctBy { it.anime.id }

/** A page of episodes as shown in the range picker, e.g. "1–30". */
data class EpisodeRange(val page: Int, val label: String)

data class DetailUiState(
    val anime: Anime? = null,
    val loadingInfo: Boolean = true,
    val infoError: String? = null,
    val latestEpisode: Double? = null,
    val ranges: List<EpisodeRange> = emptyList(),
    val selectedPage: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val loadingEpisodes: Boolean = true,
    val episodesError: String? = null,
    val newestFirst: Boolean = false,
    val episodeQuery: String = "",
    /** Earliest episode of the series; the primary button plays it when there is no history. */
    val firstEpisode: Episode? = null,
    /** Titles with closely matching genres, shown as "Anime Serupa". */
    val similar: List<Anime> = emptyList(),
    /** Other seasons, movies and spin-offs of the same franchise, shown as "Season & Terkait". */
    val seasons: List<SeasonEntry> = emptyList(),
)

class DetailViewModel(
    private val repo: AnimeRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsStore,
    saved: SavedStateHandle,
) : ViewModel() {

    val animeId: String = checkNotNull(saved["animeId"])

    private val _state = MutableStateFlow(DetailUiState(anime = repo.cachedAnime(animeId)))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> =
        library.isFavorite(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Map<String, WatchHistoryEntity>> =
        library.progressFor(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var episodeJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadInfo()
        loadEpisodes()
    }

    fun loadInfo() {
        _state.update { it.copy(loadingInfo = true, infoError = null) }
        viewModelScope.launch {
            repo.detailWithSeasons(animeId)
                .onSuccess { d ->
                    _state.update {
                        it.copy(anime = d.anime, loadingInfo = false, seasons = relatedSeasons(animeId, d.seasons))
                    }
                    loadSimilar(d.anime)
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingInfo = false, infoError = e.friendlyMessage()) }
                    // The detail endpoint can be unreachable (upstream/Cloudflare) — the cached
                    // list data still carries genres, so "Anime Serupa" keeps working.
                    repo.cachedAnime(animeId)?.let { loadSimilar(it) }
                }
        }
    }

    /** Titles whose genres resemble this anime's, from a genre-filtered pool, without its own franchise. */
    private suspend fun loadSimilar(anime: Anime) {
        if (anime.genres.isEmpty()) return
        val candidates = repo.similarCandidates(anime).getOrNull().orEmpty()
        _state.update { s ->
            if (s.anime?.id != anime.id) return@update s
            s.copy(similar = rankSimilar(anime, candidates, exclude = s.seasons.map { it.anime.id }.toSet()))
        }
    }

    fun loadEpisodes() {
        episodeJob?.cancel()
        _state.update { it.copy(loadingEpisodes = true, episodesError = null) }
        episodeJob = viewModelScope.launch {
            val newest = repo.episodePage(animeId, 0).getOrElse { e ->
                _state.update { it.copy(loadingEpisodes = false, episodesError = e.friendlyMessage()) }
                return@launch
            }
            val latest = newest.maxOfOrNull { it.sortKey }?.takeIf { it != Double.MAX_VALUE }
            val pageCount = when {
                newest.size < EPISODE_PAGE_SIZE || latest == null -> 1
                else -> ceil(latest / EPISODE_PAGE_SIZE).toInt().coerceAtLeast(1)
            }
            val ranges = buildRanges(pageCount, latest)

            // Open on the page with the user's last-watched episode, otherwise on episode 1.
            val history = library.continueWatching(500).first().firstOrNull { it.animeId == animeId }
            val resumePage = history?.let { h ->
                val n = h.episodeNumber.toDoubleOrNull()
                if (latest != null && n != null) ((latest - n) / EPISODE_PAGE_SIZE).toInt().coerceIn(0, pageCount - 1) else null
            }
            val startPage = resumePage ?: (pageCount - 1)

            _state.update { it.copy(latestEpisode = latest, ranges = ranges) }
            selectPage(startPage)
        }
    }

    private fun buildRanges(pageCount: Int, latest: Double?): List<EpisodeRange> {
        if (pageCount <= 1 || latest == null) return listOf(EpisodeRange(0, "Semua"))
        val top = latest.toInt()
        // Ascending: oldest page (highest index) first.
        return (pageCount - 1 downTo 0).map { page ->
            val high = top - page * EPISODE_PAGE_SIZE
            val low = (high - EPISODE_PAGE_SIZE + 1).coerceAtLeast(1)
            EpisodeRange(page, "$low–$high")
        }
    }

    fun selectPage(page: Int) {
        _state.update { it.copy(selectedPage = page, loadingEpisodes = true, episodesError = null, episodeQuery = "") }
        viewModelScope.launch {
            repo.episodePage(animeId, page)
                .onSuccess { eps ->
                    if (eps.isEmpty() && page > 0) {
                        // Our page-count estimate overshot (gaps in numbering); drop the empty range.
                        _state.update { s -> s.copy(ranges = s.ranges.filterNot { it.page == page }) }
                        selectPage(page - 1)
                        return@onSuccess
                    }
                    if (_state.value.selectedPage == page) {
                        _state.update { it.copy(episodes = eps, loadingEpisodes = false) }
                        updatePrimary(eps, page)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingEpisodes = false, episodesError = e.friendlyMessage()) } }
        }
    }

    private suspend fun updatePrimary(loaded: List<Episode>, page: Int) {
        if (_state.value.firstEpisode != null) return
        val lastPage = _state.value.ranges.maxOfOrNull { it.page } ?: 0
        val pageEpisodes = if (page == lastPage) loaded else repo.episodePage(animeId, lastPage).getOrNull().orEmpty()
        _state.update { it.copy(firstEpisode = pageEpisodes.minByOrNull { e -> e.sortKey }) }
    }

    fun toggleOrder() = _state.update { it.copy(newestFirst = !it.newestFirst) }

    fun searchEpisode(query: String) {
        val digits = query.filter { it.isDigit() || it == '.' }
        _state.update { it.copy(episodeQuery = digits) }
        searchJob?.cancel()
        if (digits.isBlank()) {
            _state.value.selectedPage?.let { selectPage(it) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(loadingEpisodes = true, episodesError = null) }
            repo.searchEpisodes(animeId, digits)
                .onSuccess { eps ->
                    // Exact match first, then the rest of the substring matches.
                    val sorted = eps.sortedWith(compareBy<Episode> { it.number != digits }.thenBy { it.sortKey })
                    _state.update { it.copy(episodes = sorted, loadingEpisodes = false) }
                }
                .onFailure { e -> _state.update { it.copy(loadingEpisodes = false, episodesError = e.friendlyMessage()) } }
        }
    }

    /** episodeId -> download, for the per-episode download button. */
    val downloadMap: StateFlow<Map<String, DownloadItem>> =
        downloads.forAnime(animeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()
    fun messageShown() { _messages.value = null }

    fun download(episode: Episode) {
        val anime = _state.value.anime ?: return
        viewModelScope.launch {
            val prefs = settings.settings.first()
            _messages.value = "Menyiapkan Episode ${episode.number}…"
            downloads.download(anime, episode, prefs.downloadQuality, prefs.downloadWifiOnly)
                .onSuccess { _messages.value = "Episode ${episode.number} masuk antrean download" }
                .onFailure { _messages.value = "Gagal download: ${it.friendlyMessage()}" }
        }
    }

    /** Bulk-download up to [MAX_BULK_DOWNLOAD] episodes, skipping ones already stored or queued. */
    fun downloadAllEpisodes() {
        val anime = _state.value.anime ?: return
        viewModelScope.launch {
            val newest = repo.episodePage(animeId, 0).getOrNull()?.maxByOrNull { it.sortKey }
            if (newest == null || newest.sortKey == Double.MAX_VALUE) {
                _messages.value = "Belum ada episode untuk diunduh."
                return@launch
            }
            val pageCount = ceil(newest.sortKey / EPISODE_PAGE_SIZE).toInt().coerceAtLeast(1)
            val inFlight = downloads.forAnime(animeId).first()
            val all = (pageCount - 1 downTo 0).flatMap { page ->
                repo.episodePage(animeId, page).getOrNull().orEmpty()
            }
            val targets = all
                .filter { ep -> inFlight[ep.id]?.let { it.state == DownloadState.DONE || it.inProgress } != true }
                .sortedByDescending { it.sortKey }
                .take(MAX_BULK_DOWNLOAD)
            if (targets.isEmpty()) {
                _messages.value = "Semua episode sudah tersimpan atau sedang diunduh."
                return@launch
            }
            val prefs = settings.settings.first()
            _messages.value = "Menyiapkan ${targets.size} episode…"
            var queued = 0
            for (ep in targets.sortedBy { it.sortKey }) {
                downloads.download(anime, ep, prefs.downloadQuality, prefs.downloadWifiOnly)
                    .onSuccess { queued++ }
            }
            _messages.value = "$queued episode masuk antrean download"
        }
    }

    private companion object {
        /** Guard so "unduh semua" on long-running shows never explodes into 1000 stream requests. */
        const val MAX_BULK_DOWNLOAD = 30
    }

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch {
            downloads.delete(episode.id)
            _messages.value = "Download Episode ${episode.number} dihapus"
        }
    }

    fun toggleFavorite() {
        val anime = _state.value.anime ?: return
        viewModelScope.launch { library.setFavorite(anime, !isFavorite.value) }
    }

    /** Displayed list respects the order toggle; search results keep relevance order. */
    fun displayed(state: DetailUiState): List<Episode> = when {
        state.episodeQuery.isNotBlank() -> state.episodes
        state.newestFirst -> state.episodes.sortedByDescending { it.sortKey }
        else -> state.episodes.sortedBy { it.sortKey }
    }
}
