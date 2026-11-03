package com.fuwanime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.LatestRelease
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.remote.NetworkStatus
import com.fuwanime.data.local.WatchHistoryEntity
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
    val error: String? = null,
) {
    val hasData: Boolean get() = popular.isNotEmpty() || thisSeason.isNotEmpty() || latest.isNotEmpty()
}

data class HomeUiState(
    val feed: HomeFeed = HomeFeed(),
    val continueWatching: List<WatchHistoryEntity> = emptyList(),
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
) : ViewModel() {

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

    val state: StateFlow<HomeUiState> = combine(feed, library.continueWatching(15)) { f, cw ->
        HomeUiState(f, cw)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val today: Day = Day.today()

    private val loader = HomeFeedLoader(anime, today)

    init {
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
    }

    fun refresh() = load(refreshing = true)

    private fun online() = networkStatus.state.value.online

    private fun load(refreshing: Boolean) {
        feed.update {
            it.copy(loading = !refreshing && !it.hasData, refreshing = refreshing, syncing = !refreshing && it.hasData, error = null)
        }
        viewModelScope.launch {
            // The network is often not ready at cold start; retry quietly before showing an error.
            repeat(AUTO_RETRIES) { attempt ->
                val result = loader.fetch(cacheOnly = false)
                if (result.error == null || attempt == AUTO_RETRIES - 1) {
                    feed.value.reduce(result, fromCache = false, online = online())?.let { feed.value = it }
                    return@launch
                }
                delay(2_000L * (attempt + 1))
            }
        }
    }

    private companion object {
        const val AUTO_RETRIES = 3
    }
}
