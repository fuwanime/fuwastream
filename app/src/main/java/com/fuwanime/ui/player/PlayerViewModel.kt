package com.fuwanime.ui.player

import androidx.lifecycle.SavedStateHandle
import com.fuwanime.data.SeasonEntry
import com.fuwanime.data.meta.AnimeLinkRepository
import com.fuwanime.data.meta.SkipRepository
import com.fuwanime.data.meta.SkipSegment
import com.fuwanime.ui.detail.nextInFranchise
import com.fuwanime.ui.detail.watchOrder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.fuwanime.data.Anime
import com.fuwanime.data.DownloadItem
import com.fuwanime.data.DownloadRepository
import com.fuwanime.data.closestTo
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.Episode
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.OpMark
import com.fuwanime.data.nextLowerThan
import com.fuwanime.data.StreamSource
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.local.VideoResize
import com.fuwanime.util.friendlyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val anime: Anime? = null,
    val episode: Episode,
    val sources: List<StreamSource> = emptyList(),
    val source: StreamSource? = null,
    /** Position to seek to once the current source is prepared. */
    val startPositionMs: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val next: Episode? = null,
    val previous: Episode? = null,
    /** Episodes of the current page, for the portrait episode strip. */
    val nearby: List<Episode> = emptyList(),
    /** Playing a downloaded file; no network needed. */
    val offline: Boolean = false,
    /** One-shot message for the snackbar (download started/failed). */
    val message: String? = null,
    /** The franchise entry after this one ("S2" after "S1"), offered when the last episode ends. */
    val nextSeason: SeasonEntry? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val repo: AnimeRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope,
    /** Whether the active network is metered (mobile data), for the mobile-data quality setting. */
    private val isMetered: () -> Boolean,
    saved: SavedStateHandle,
    /** Updates the "Lanjutkan" shortcut and widget once the user leaves an episode. */
    private val refreshHomeSurfaces: () -> Unit = {},
    private val links: AnimeLinkRepository? = null,
    private val skips: SkipRepository? = null,
) : ViewModel() {

    private val animeId: String = checkNotNull(saved["animeId"])

    private val _state = MutableStateFlow(
        PlayerUiState(
            anime = repo.cachedAnime(animeId),
            episode = Episode(
                id = checkNotNull(saved["episodeId"]),
                number = saved.get<String>("number").orEmpty(),
                title = "Episode ${saved.get<String>("number").orEmpty()}",
                // Shown in the video area until the first frame, so the tapped thumbnail has somewhere to land.
                thumbnail = saved.get<String>("thumb").orEmpty(),
            ),
        ),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Download state of whatever episode is on screen. */
    val download: StateFlow<DownloadItem?> = _state.map { it.episode.id }.distinctUntilChanged()
        .flatMapLatest { downloads.forEpisode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The user's opening mark for this anime, shared by all its episodes. */
    val opMark: StateFlow<OpMark?> =
        settingsStore.opMark(animeId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loadJob: Job? = null

    /** Height the user picked by hand in this player session; kept for the following episodes. */
    private var sessionHeight: Int? = null

    /** Sources of the current episode that already failed, so fallback never loops. */
    private val failedUrls = mutableSetOf<String>()

    /** AniSkip segments paired with the episode they belong to; read through [segmentsFor]. */
    private val _segments = MutableStateFlow("" to emptyList<SkipSegment>())
    val segments: StateFlow<Pair<String, List<SkipSegment>>> = _segments.asStateFlow()
    private var segmentsDoneFor: String? = null
    private var segmentsTriedAt: Long? = null
    private var segmentsJob: Job? = null

    init {
        // The franchise list (for "Tonton selanjutnya") comes with the detail; it also fills in a missing anime.
        viewModelScope.launch {
            repo.detailWithSeasons(animeId).onSuccess { d ->
                _state.update {
                    it.copy(anime = it.anime ?: d.anime, nextSeason = nextInFranchise(animeId, watchOrder(d.anime, d.seasons)))
                }
            }
        }
        play(_state.value.episode)
    }

    /** Fetches AniSkip segments once per episode, once the real duration is known (AniSkip matches on it). */
    fun onDurationKnown(episodeId: String, durationMs: Long) {
        val episode = _state.value.episode
        val now = System.currentTimeMillis()
        if (!shouldTrySegments(episodeId, episode.id, durationMs, segmentsDoneFor, segmentsJob?.isActive == true, segmentsTriedAt, now)) return
        val number = episodeNumberForSkip(episode.number) ?: return
        val links = links ?: return
        val skips = skips ?: return
        segmentsTriedAt = now
        segmentsJob = viewModelScope.launch {
            // Opened straight from Lanjutkan/widget, the anime may never have been linked on a Detail screen.
            val mal = links.malIdFor(animeId) ?: _state.value.anime?.let { links.link(it); links.malIdFor(animeId) } ?: return@launch
            segmentsDoneFor = episodeId
            val segs = skips.segments(mal, number, durationMs)
            if (_state.value.episode.id == episodeId) _segments.value = episodeId to segs
        }
    }

    fun play(episode: Episode) {
        loadJob?.cancel()
        failedUrls.clear()
        _state.update {
            it.copy(
                episode = episode, sources = emptyList(), source = null, loading = true, error = null,
                next = null, previous = null, offline = false,
            )
        }
        loadJob = viewModelScope.launch {
            val resumeAt = library.savedPosition(episode.id)
            if (playOffline(episode, resumeAt)) return@launch
            val stream = repo.stream(episode.id).getOrElse { e ->
                _state.update { it.copy(loading = false, error = e.friendlyMessage()) }
                return@launch
            }
            if (stream.sources.isEmpty()) {
                _state.update { it.copy(loading = false, error = "Belum ada sumber video untuk episode ini.") }
                return@launch
            }
            val merged = stream.episode?.let { info ->
                episode.copy(
                    title = info.title.ifBlank { episode.title },
                    thumbnail = info.thumbnail.ifBlank { episode.thumbnail },
                    number = episode.number.ifBlank { info.number },
                )
            } ?: episode
            val preferred = preferredHeight(settings.first(), isMetered(), sessionHeight)
            _state.update {
                it.copy(
                    episode = merged,
                    sources = stream.sources,
                    source = pickSource(stream.sources, preferred),
                    startPositionMs = resumeAt,
                    loading = false,
                )
            }
            loadNeighbours(merged)
        }
    }

    private fun pickSource(sources: List<StreamSource>, preferredHeight: Int): StreamSource =
        sources.closestTo(preferredHeight) ?: sources.first()

    /** Plays the downloaded file when there is one. Returns false to fall back to streaming. */
    private suspend fun playOffline(episode: Episode, resumeAt: Long): Boolean {
        val saved = downloads.completed(episode.id) ?: return false
        val e = saved.entity
        val local = StreamSource(Uri.fromFile(File(e.filePath)).toString(), "${e.quality} • Offline", "Perangkat")
        _state.update {
            it.copy(
                // Offline there may be no detail response, so rebuild the essentials from the download row.
                anime = it.anime ?: Anime(e.animeId, e.animeTitle, poster = e.animePoster),
                episode = episode.copy(
                    number = episode.number.ifBlank { e.episodeNumber },
                    title = e.episodeTitle.ifBlank { episode.title },
                    thumbnail = episode.thumbnail.ifBlank { e.episodeThumbnail },
                ),
                sources = listOf(local),
                source = local,
                startPositionMs = resumeAt,
                loading = false,
                offline = true,
            )
        }
        loadNeighbours(_state.value.episode)
        return true
    }

    fun downloadCurrent(source: StreamSource) {
        val s = _state.value
        val anime = s.anime ?: return
        viewModelScope.launch {
            val wifiOnly = settings.value.downloadWifiOnly
            downloads.enqueue(anime, s.episode, source, wifiOnly)
                .onSuccess { postMessage(if (wifiOnly) "Download dimulai (menunggu Wi-Fi)" else "Download ${source.quality} dimulai") }
                .onFailure { postMessage("Gagal download: ${it.friendlyMessage()}") }
        }
    }

    fun deleteDownload() {
        val id = _state.value.episode.id
        viewModelScope.launch {
            downloads.delete(id)
            postMessage("Download dihapus")
        }
    }

    private fun postMessage(text: String) = _state.update { it.copy(message = text) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private suspend fun loadNeighbours(episode: Episode) {
        val next = repo.adjacentEpisode(animeId, episode, newer = true)
        val prev = repo.adjacentEpisode(animeId, episode, newer = false)
        val page = repo.pageOf(animeId, episode.id)
        val nearby = page?.let { repo.episodePage(animeId, it).getOrNull() }?.sortedBy { it.sortKey }.orEmpty()
        if (_state.value.episode.id == episode.id) {
            _state.update { it.copy(next = next, previous = prev, nearby = nearby) }
        }
    }

    fun selectSource(source: StreamSource, currentPositionMs: Long) {
        if (source.height > 0) sessionHeight = source.height
        _state.update { it.copy(source = source, startPositionMs = currentPositionMs) }
    }

    /**
     * Switches to the next lower quality after a playback error. Returns false when playing offline
     * or when nothing lower is left, so the caller shows the error instead.
     */
    fun fallBack(positionMs: Long): Boolean {
        val s = _state.value
        val current = s.source ?: return false
        if (s.offline) return false
        failedUrls += current.url
        val lower = s.sources.nextLowerThan(current, failedUrls) ?: return false
        _state.update {
            it.copy(
                source = lower,
                startPositionMs = positionMs,
                message = "Gagal memutar ${current.quality}, beralih ke ${lower.quality}",
            )
        }
        return true
    }

    fun markOpStart(positionMs: Long) = saveOpMark(markOp(opMark.value, positionMs, isStart = true))
    fun markOpEnd(positionMs: Long) = saveOpMark(markOp(opMark.value, positionMs, isStart = false))
    fun setVideoResize(mode: VideoResize) {
        viewModelScope.launch { settingsStore.setVideoResize(mode) }
    }

    fun clearOpMark() {
        viewModelScope.launch { settingsStore.clearOpMark(animeId) }
    }

    private fun saveOpMark(mark: OpMark) {
        viewModelScope.launch { settingsStore.setOpMark(animeId, mark) }
    }

    fun playNext() {
        _state.value.next?.let(::play)
    }

    fun playPrevious() {
        _state.value.previous?.let(::play)
    }

    fun retry() = play(_state.value.episode)

    fun saveProgress(positionMs: Long, durationMs: Long): Job? {
        val s = _state.value
        val anime = s.anime ?: return null
        // Ignore the first few seconds so merely opening an episode doesn't clutter history.
        if (durationMs <= 0 || positionMs < 5_000) return null
        return appScope.launch { library.saveProgress(anime, s.episode, positionMs, durationMs) }
    }

    /** Leaving the episode (stopped, ended): save, then let launcher surfaces show it. */
    fun onPlayerExit(positionMs: Long, durationMs: Long) {
        val save = saveProgress(positionMs, durationMs)
        appScope.launch {
            save?.join() // so the widget reads this episode, not the previous one
            refreshHomeSurfaces()
        }
    }

    /** Real playing time for statistics, independent of playback speed. */
    fun addWatchTime(ms: Long) {
        appScope.launch { library.addWatchTime(ms) }
    }
}
