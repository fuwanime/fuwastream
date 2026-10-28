package com.fuwanime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.LatestRelease
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.SortOrder
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.util.Day
import com.fuwanime.util.friendlyMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val refreshing: Boolean = false,
    val error: String? = null,
)

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
    library: LibraryRepository,
    private val updateRepository: UpdateRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val feed = MutableStateFlow(HomeFeed())

    /** Non-null when a newer GitHub release exists (checked at most once every 6 hours). */
    private val _update = MutableStateFlow<UpdateRepository.UpdateInfo?>(null)
    val update: StateFlow<UpdateRepository.UpdateInfo?> = _update.asStateFlow()

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

    init {
        load(refreshing = false)
        viewModelScope.launch { _update.value = updateRepository.check() }
    }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        feed.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            // The network is often not ready at cold start; retry quietly before showing an error.
            repeat(AUTO_RETRIES) { attempt ->
                if (fetch(showError = attempt == AUTO_RETRIES - 1)) return@launch
                delay(2_000L * (attempt + 1))
            }
        }
    }

    /** Returns true when something useful was loaded. */
    private suspend fun fetch(showError: Boolean): Boolean = coroutineScope {
        val popularJob = async { anime.explore(page = 0, sort = SortOrder.POPULAR) }
        val latestJob = async { anime.latestReleases(LATEST_LIMIT) }
        // Every day's schedule together approximates "currently airing this season".
        val dayJobs = Day.entries.map { day -> day to async { anime.schedule(day.apiValue) } }

        val popular = popularJob.await()
        val days = dayJobs.map { (day, job) -> day to job.await() }

        val todayList = days.first { it.first == today }.second.getOrDefault(emptyList())
        val season = days.flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .sortedByDescending { it.views }

        val error = popular.exceptionOrNull()?.takeIf { season.isEmpty() }?.friendlyMessage()
        if (error != null && !showError) return@coroutineScope false
        feed.value = HomeFeed(
            popular = popular.getOrDefault(feed.value.popular),
            today = todayList,
            thisSeason = season,
            latest = latestJob.await().getOrDefault(feed.value.latest),
            loading = false,
            refreshing = false,
            error = error,
        )
        error == null
    }

    private companion object {
        const val AUTO_RETRIES = 3
        const val LATEST_LIMIT = 20
    }
}
