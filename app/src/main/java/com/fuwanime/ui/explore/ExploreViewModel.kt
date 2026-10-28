package com.fuwanime.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.SortOrder
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.util.friendlyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusFilter(val label: String) { ALL("Semua"), ONGOING("Tayang"), FINISHED("Tamat") }

/** [apiType] is the API's `type` value; null means no type filter. */
enum class TypeFilter(val label: String, val apiType: String?) {
    ALL("Semua", null),
    SERIES("Series", "SERIES"),
    MOVIE("Film", "MOVIE"),
    LIVE_ACTION("Live Action", "LIVE ACTION"),
    ONA("ONA", "ONA"),
    OVA("OVA", "OVA"),
    SPECIAL("Special", "SPECIAL");

    fun matches(type: String): Boolean =
        apiType == null || type.trim().replace('-', ' ').equals(apiType, ignoreCase = true)
}

data class ExploreFilters(
    val query: String = "",
    val genre: String? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val type: TypeFilter = TypeFilter.ALL,
    val sort: SortOrder = SortOrder.POPULAR,
) {
    val activeCount: Int get() = listOfNotNull(
        genre,
        status.takeIf { it != StatusFilter.ALL },
        type.takeIf { it != TypeFilter.ALL },
    ).size
}

data class ExploreUiState(
    val filters: ExploreFilters = ExploreFilters(),
    val results: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class ExploreViewModel(
    private val repo: AnimeRepository,
    private val library: LibraryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ExploreUiState(filters = ExploreFilters(genre = savedState.get<String>("genre")?.takeIf { it.isNotBlank() })),
    )
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    val recentSearches: StateFlow<List<String>> =
        library.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val raw = mutableListOf<Anime>()
    private val seen = HashSet<String>()
    private var nextPage = 0
    private var loadJob: Job? = null
    private var debounceJob: Job? = null

    init {
        reload()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(filters = it.filters.copy(query = query)) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(450)
            reload()
        }
    }

    fun submitSearch(query: String = _state.value.filters.query) {
        debounceJob?.cancel()
        _state.update { it.copy(filters = it.filters.copy(query = query)) }
        viewModelScope.launch { library.addSearch(query) }
        reload()
    }

    fun removeRecent(query: String) = viewModelScope.launch { library.removeSearch(query) }
    fun clearRecent() = viewModelScope.launch { library.clearSearches() }

    fun setGenre(genre: String?) = updateFilters { it.copy(genre = genre) }
    fun setStatus(status: StatusFilter) = updateFilters { it.copy(status = status) }
    fun setType(type: TypeFilter) = updateFilters { it.copy(type = type) }
    fun setSort(sort: SortOrder) = updateFilters { it.copy(sort = sort) }
    fun clearFilters() = updateFilters { it.copy(genre = null, status = StatusFilter.ALL, type = TypeFilter.ALL) }

    private fun updateFilters(change: (ExploreFilters) -> ExploreFilters) {
        val before = _state.value.filters
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(filters = after) }
        // Sort, query and type hit the server; genre/status only re-filter what we already have.
        if (after.sort != before.sort || after.query != before.query || after.type != before.type) reload()
        else refilter(fillPage = true)
    }

    fun reload() {
        loadJob?.cancel()
        raw.clear()
        seen.clear()
        nextPage = 0
        _state.update { it.copy(results = emptyList(), loading = true, endReached = false, error = null) }
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.endReached || (loadJob?.isActive == true)) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = !it.loading, error = null) }
            val startCount = _state.value.results.size
            // Genre/status filtering is client-side, so keep fetching until enough new matches show up.
            repeat(MAX_PAGES_PER_BATCH) {
                val f = _state.value.filters
                val page = repo.explore(nextPage, f.sort, f.query, f.type.apiType).getOrElse { e ->
                    _state.update { it.copy(loading = false, loadingMore = false, error = e.friendlyMessage()) }
                    return@launch
                }
                nextPage++
                page.forEach { if (seen.add(it.id)) raw += it }
                val end = page.size < AnimeApiService.EXPLORE_PAGE_SIZE
                if (end) _state.update { it.copy(endReached = true) }
                val matches = refilter(fillPage = false)
                if (end || matches - startCount >= MIN_BATCH) {
                    _state.update { it.copy(loading = false, loadingMore = false) }
                    return@launch
                }
            }
            _state.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    private fun refilter(fillPage: Boolean): Int {
        val f = _state.value.filters
        val filtered = raw.filter { a ->
            (f.genre == null || a.genres.any { it.equals(f.genre, ignoreCase = true) } ||
                (f.genre.equals("Isekai", true) && a.synopsis.contains("isekai", true))) &&
                when (f.status) {
                    StatusFilter.ALL -> true
                    StatusFilter.ONGOING -> a.isOngoing
                    StatusFilter.FINISHED -> !a.isOngoing && !a.isUpcoming
                } &&
                // Server-filtered pages already match; keyword searches are type-filtered here.
                f.type.matches(a.type)
        }
        _state.update { it.copy(results = filtered) }
        if (fillPage) {
            if (filtered.size < MIN_BATCH && !_state.value.endReached) loadMore()
        }
        return filtered.size
    }

    private companion object {
        const val MIN_BATCH = 18
        const val MAX_PAGES_PER_BATCH = 6
    }
}
