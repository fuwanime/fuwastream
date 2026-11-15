package com.fuwanime.ui.home

import com.fuwanime.util.UiText
import com.fuwanime.data.backup.BackupRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.LatestRelease
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.local.ContentPreferenceEntity
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.manga.MangaRelease
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.util.Day
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class HomeFeed(
    val popular: List<Anime> = emptyList(),
    val today: List<Anime> = emptyList(),
    val thisSeason: List<Anime> = emptyList(),
    /** Titles with a freshly released episode, newest first. */
    val latest: List<LatestRelease> = emptyList(),
    val loading: Boolean = true,
    /** Pull-to-refresh in progress (its own indicator). */
    val refreshing: Boolean = false,
    /** Quiet background refresh while data is already on screen (the top-bar hairline). */
    val syncing: Boolean = false,
    /** What is on screen came from the disk cache, not yet confirmed by the network. */
    val stale: Boolean = false,
    val error: UiText? = null,
) {
    val hasData: Boolean get() = popular.isNotEmpty() || thisSeason.isNotEmpty() || latest.isNotEmpty()
}

/** One row of the unified "Lanjutkan" shelf, mixing anime episodes and manga chapters. */
sealed interface HomeContinue {
    val updatedAt: Long

    data class Anime(val entry: WatchHistoryEntity) : HomeContinue {
        override val updatedAt: Long get() = entry.updatedAt
    }

    data class Manga(val entry: MangaHistoryEntity) : HomeContinue {
        override val updatedAt: Long get() = entry.updatedAt
    }
}

/** Releases from the user's collection since the previous Beranda visit. */
data class HomeUpdates(val anime: Int = 0, val manga: Int = 0) {
    val total: Int get() = anime + manga
}

data class HomeUiState(
    val feed: HomeFeed = HomeFeed(),
    val continueItems: List<HomeContinue> = emptyList(),
    val updates: HomeUpdates = HomeUpdates(),
    /** Latest episode feed narrowed to the user's collection; personalises the top of Beranda. */
    val collectionLatest: List<LatestRelease> = emptyList(),
) {
    /** Most-watched titles airing this season; all-time popular only if the schedule is unavailable. */
    val hero: List<Anime> get() = feed.thisSeason.ifEmpty { feed.popular }
        .filter { it.poster.isNotBlank() }
        .sortedByDescending { it.views }
        .take(8)
}

class HomeViewModel(
    private val anime: AnimeRepository,
    private val library: LibraryRepository,
    private val updateRepository: UpdateRepository,
    private val settingsStore: SettingsStore,
    private val networkStatus: NetworkStatus,
    val backup: BackupRepository,
    private val mangaLibrary: MangaLibrary,
    private val mangaRepository: MangaRepository,
) : ViewModel() {

    /** A fresh install (nothing watched or saved) gets one offer to restore a backup file. */
    val showRestorePrompt: StateFlow<Boolean> =
        combine(
            library.allHistory,
            library.favoriteList,
            mangaLibrary.favorites,
            mangaLibrary.continueReading,
            settingsStore.backup,
        ) { history, animeFavorites, mangaFavorites, mangaHistory, backup ->
            history.isEmpty() && animeFavorites.isEmpty() && mangaFavorites.isEmpty() &&
                mangaHistory.isEmpty() && !backup.promptDismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissRestorePrompt() = viewModelScope.launch { settingsStore.dismissBackupPrompt() }

    /** Long-press/close action on the unified Lanjutkan shelf. */
    fun hideFromContinue(item: HomeContinue) = viewModelScope.launch {
        when (item) {
            is HomeContinue.Anime -> library.hideAnimeFromContinue(item.entry.animeId)
            is HomeContinue.Manga -> library.hideMangaFromContinue(item.entry.mangaId)
        }
    }

    private val feed = MutableStateFlow(HomeFeed())

    /** Non-null when a newer GitHub release exists (checked at most once every 6 hours). */
    private val _update = MutableStateFlow<UpdateRepository.UpdateInfo?>(null)
    val update: StateFlow<UpdateRepository.UpdateInfo?> = _update.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = library.favoriteList
        .map { list -> list.mapTo(HashSet()) { it.animeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(anime: Anime) {
        viewModelScope.launch { library.setFavorite(anime, anime.id !in favoriteIds.value) }
    }

    /** Hides the update banner until the next release. */
    fun dismissUpdate() {
        _update.value?.let { info ->
            viewModelScope.launch { settingsStore.dismissUpdate(info.tag) }
        }
        _update.value = null
    }

    private val lastHomeOpen = MutableStateFlow<Long?>(null)
    private val mangaReleases = MutableStateFlow<List<MangaRelease>>(emptyList())

    /** Episode and chapter progress, minus titles the user hid from Lanjutkan. */
    private val continueItems = combine(
        library.continueWatching(30),
        mangaLibrary.continueReading,
        library.titlePreferences(),
    ) { animeRows, mangaRows, prefs ->
        val hidden = prefs.filter { it.hiddenFromContinue }.mapTo(HashSet()) { it.key }
        val items = buildList<HomeContinue> {
            animeRows.forEach { if (ContentPreferenceEntity.anime(it.animeId) !in hidden) add(HomeContinue.Anime(it)) }
            mangaRows.forEach { if (ContentPreferenceEntity.manga(it.mangaId) !in hidden) add(HomeContinue.Manga(it)) }
        }
        items.sortedByDescending { it.updatedAt }.take(15)
    }

    private data class LibrarySnapshot(
        val favoriteAnime: List<com.fuwanime.data.local.FavoriteEntity>,
        val favoriteManga: List<MangaFavoriteEntity>,
        val mangaReleases: List<MangaRelease>,
        val lastOpen: Long?,
    )

    private val librarySnapshot = combine(
        library.favoriteList,
        mangaLibrary.favorites,
        mangaReleases,
        lastHomeOpen,
    ) { a, m, releases, last -> LibrarySnapshot(a, m, releases, last) }

    val state: StateFlow<HomeUiState> = combine(feed, continueItems, librarySnapshot) { f, cw, lib ->
        val animeIds = lib.favoriteAnime.mapTo(HashSet()) { it.animeId }
        val mangaIds = lib.favoriteManga.mapTo(HashSet()) { it.mangaId }
        val last = lib.lastOpen
        HomeUiState(
            feed = f,
            continueItems = cw,
            updates = HomeUpdates(
                anime = if (last == null) 0 else f.latest.count { it.anime.id in animeIds && updatedAtMs(it.updatedAt) > last },
                manga = if (last == null) 0 else lib.mangaReleases.count { it.mangaId in mangaIds && it.publishAt > last },
            ),
            collectionLatest = f.latest.filter { it.anime.id in animeIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val today: Day = Day.today()

    private val loader = HomeFeedLoader(anime, today)

    /** Called when Beranda actually appears, so the summary covers the time since the previous visit. */
    fun onOpened() = viewModelScope.launch {
        // Capture the previous visit before overwriting it; this drives the update summary.
        lastHomeOpen.value = settingsStore.lastHomeOpen()
        settingsStore.markHomeOpened()
    }

    init {
        loadMangaReleases()
        viewModelScope.launch {
            // Last saved feed first, so Beranda draws at once; the network load below replaces it.
            val cached = loader.fetch(cacheOnly = true)
            feed.value.reduce(cached, fromCache = true, online = online())?.let { feed.value = it }
        }
        load(refreshing = false)
        viewModelScope.launch { _update.value = updateRepository.check() }
        viewModelScope.launch {
            networkStatus.state.map { it.online }.distinctUntilChanged().drop(1).filter { it }.collect {
                if (shouldAutoRefresh(feed.value)) load(refreshing = false)
            }
        }
        viewModelScope.launch {
            settingsStore.matureContent.distinctUntilChanged().drop(1).collect {
                // Rebuild both anime and Comix shelves immediately when the global content filter changes.
                load(refreshing = false)
                loadMangaReleases()
            }
        }
    }

    fun refresh() {
        load(refreshing = true)
        loadMangaReleases()
    }

    private fun loadMangaReleases() = viewModelScope.launch {
        mangaReleases.value = runCatching { mangaRepository.latestOngoingChapters(40) }.getOrDefault(emptyList())
    }

    private fun online() = networkStatus.state.value.online

    private fun load(refreshing: Boolean) {
        feed.update {
            it.copy(loading = !refreshing && !it.hasData, refreshing = refreshing, syncing = !refreshing && it.hasData, error = null)
        }
        viewModelScope.launch {
            // The network is often not ready at cold start; retry quietly before showing an error.
            repeat(AUTO_RETRIES) { attempt ->
                val hitsBefore = networkStatus.cacheHits
                val result = loader.fetch(cacheOnly = false)
                val servedFromCache = networkStatus.cacheHits != hitsBefore
                val last = attempt == AUTO_RETRIES - 1
                if (result.error == null || last) {
                    feed.value.reduce(result, fromCache = false, online = online(), servedFromCache)?.let { feed.value = it }
                    // Cached answer while the network looks up (not ready yet at cold start): keep retrying for fresh data.
                    if (!servedFromCache || last || !online()) return@launch
                }
                delay(2_000L * (attempt + 1))
            }
        }
    }

    private companion object {
        const val AUTO_RETRIES = 3
    }
}

private fun updatedAtMs(raw: String): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        isLenient = false
    }.parse(raw.trim())?.time ?: 0L
}.getOrDefault(0L)
