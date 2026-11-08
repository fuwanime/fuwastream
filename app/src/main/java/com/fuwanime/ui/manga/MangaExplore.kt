package com.fuwanime.ui.manga

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.fuwanime.ui.components.GenreSheet
import com.fuwanime.ui.components.GenreChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaOrder
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaTag
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.LocalNavBarPadding
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.PosterCard
import com.fuwanime.ui.components.PosterSkeleton
import com.fuwanime.ui.components.TagChip
import com.fuwanime.util.UiText
import com.fuwanime.util.friendlyText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val PAGE_SIZE = 30 // MangaRepository's page size
private const val OFFSET_CAP = 10_000 // MangaDex rejects offset + limit above this
private const val PREFETCH = 6

/** Load the next page once the last visible item is within [PREFETCH] of the end, and only while the server has more and the offset cap allows it. */
fun shouldLoadMore(lastVisible: Int, loaded: Int, total: Int): Boolean =
    loaded < total && loaded + PAGE_SIZE <= OFFSET_CAP && lastVisible >= loaded - PREFETCH

/** The MangaDex tag a genre name from any source stands for, or null (Indonesian-only names have none). */
fun genreTagId(name: String, tags: List<MangaTag>): String? = tags.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id

/** A genre tapped on a manga page, waiting for the Manga tab to show it as a filter. */
val pendingMangaGenre = MutableStateFlow<String?>(null)

private fun orderFor(query: String) = if (query.isBlank()) MangaOrder.POPULAR else MangaOrder.RELEVANCE

data class MangaExploreState(
    val query: String = "",
    val status: MangaStatus? = null,
    val tagIds: Set<String> = emptySet(),
    val tags: List<MangaTag> = emptyList(),
    val results: List<Manga> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    /** Bumps with every fresh first page, so UI state tied to the old result list (scroll, pending paging) can be dropped. */
    val generation: Int = 0,
) {
    val endReached get() = results.size >= total || results.size + PAGE_SIZE > OFFSET_CAP
    val filtered get() = query.isNotBlank() || status != null || tagIds.isNotEmpty()
}

@OptIn(FlowPreview::class)
class MangaExploreViewModel(private val repo: MangaRepository) : ViewModel() {
    private val _state = MutableStateFlow(MangaExploreState())
    val state: StateFlow<MangaExploreState> = _state.asStateFlow()
    private val retry = MutableStateFlow(0)
    private var moreJob: Job? = null
    private var started = false

    // What the shown results were fetched with (the query is debounced, so it can lag behind state.query).
    private var applied = Triple("", null as MangaStatus?, emptySet<String>())

    /** Starts the search pipeline when manga mode is first shown. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            // Empty query (initial load, or cleared) applies at once; typing waits 400 ms.
            val query = _state.map { it.query }.debounce { if (it.isEmpty()) 0L else 400L }.distinctUntilChanged()
            val filters = _state.map { it.status to it.tagIds }.distinctUntilChanged()
            combine(query, filters, retry) { q, f, _ -> Triple(q, f.first, f.second) }
                .collectLatest { applied = it; loadFirst(it.first, it.second, it.third) }
        }
        viewModelScope.launch {
            runCatching { repo.tags() }.onSuccess { all ->
                _state.update { s -> s.copy(tags = all.filter { it.group == "genre" || it.group == "theme" }) }
            }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }
    fun setStatus(s: MangaStatus?) = _state.update { it.copy(status = s) }
    fun toggleTag(id: String) = _state.update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }
    fun clearFilters() = _state.update { it.copy(query = "", status = null, tagIds = emptySet()) }
    fun reload() { retry.update { it + 1 } }

    /** Shows one genre: as the MangaDex filter when the name matches a tag, else as a text search. */
    fun applyGenre(name: String) {
        viewModelScope.launch {
            // On a cold tab the tag list may still be loading.
            val tags = withTimeoutOrNull(5_000) { _state.first { it.tags.isNotEmpty() }.tags }.orEmpty()
            val id = genreTagId(name, tags)
            _state.update { if (id != null) it.copy(query = "", status = null, tagIds = setOf(id)) else it.copy(query = name, status = null, tagIds = emptySet()) }
        }
    }

    private suspend fun loadFirst(q: String, status: MangaStatus?, tags: Set<String>) {
        moreJob?.cancel()
        _state.update { it.copy(results = emptyList(), total = 0, loading = true, loadingMore = false, error = null, generation = it.generation + 1) }
        try {
            val page = repo.browse(orderFor(q), q, tags.toList(), status, 0)
            // Text search also asks the Indonesian source; its matches come first (tags/status are MangaDex-only filters).
            val indonesian = if (q.isNotBlank() && tags.isEmpty() && status == null) repo.searchIndonesia(q) else emptyList()
            _state.update { it.copy(results = (indonesian + page.items).distinctBy { m -> m.id }, total = page.total + indonesian.size, loading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.friendlyText()) }
        }
    }

    /** [generation] is the one the caller saw; a request from before a filter change is ignored. */
    fun loadMore(generation: Int) {
        val s = _state.value
        if (generation != s.generation || s.loading || s.loadingMore || s.endReached) return
        val (q, status, tags) = applied
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            try {
                val page = repo.browse(orderFor(q), q, tags.toList(), status, s.results.size)
                _state.update { cur ->
                    val known = cur.results.mapTo(HashSet()) { it.id }
                    val fresh = page.items.filter { it.id !in known }
                    // An empty page ends paging even when `total` says more (it counts items we may not receive).
                    cur.copy(results = cur.results + fresh, total = if (page.items.isEmpty()) cur.results.size else page.total, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.friendlyText()) }
            }
        }
    }
}

/** One filter row under the search field: the genre picker (a sheet) and the publication status. */
@Composable
fun MangaFilterBar(state: MangaExploreState, vm: MangaExploreViewModel) {
    var genresOpen by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { GenreChip(state.tagIds.size) { genresOpen = true } }
        item { TagChip(stringResource(R.string.manga_filter_all), onClick = { vm.setStatus(null) }, selected = state.status == null) }
        item { TagChip(stringResource(R.string.manga_status_ongoing), onClick = { vm.setStatus(MangaStatus.ONGOING) }, selected = state.status == MangaStatus.ONGOING) }
        item { TagChip(stringResource(R.string.manga_status_completed), onClick = { vm.setStatus(MangaStatus.COMPLETED) }, selected = state.status == MangaStatus.COMPLETED) }
    }
    if (genresOpen) {
        GenreSheet(
            genres = state.tags.map { it.id to it.name },
            selected = state.tagIds,
            onToggle = vm::toggleTag,
            onClear = { state.tagIds.forEach(vm::toggleTag) },
            onDismiss = { genresOpen = false },
        )
    }
}

@Composable
fun MangaResultsGrid(state: MangaExploreState, vm: MangaExploreViewModel, onMangaClick: (String) -> Unit, topPad: Dp) {
    // A new first page starts with a fresh grid state, so it opens at the top.
    key(state.generation) { MangaResultsGridBody(state, vm, onMangaClick, topPad) }
}

@Composable
private fun MangaResultsGridBody(state: MangaExploreState, vm: MangaExploreViewModel, onMangaClick: (String) -> Unit, topPad: Dp) {
    val gridState = rememberLazyGridState()
    val last by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(last, state.results.size, state.total) {
        if (state.results.isNotEmpty() && shouldLoadMore(last, state.results.size, state.total)) vm.loadMore(state.generation)
    }
    val bottom = 24.dp + LocalNavBarPadding.current
    when {
        state.loading && state.results.isEmpty() -> LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, top = topPad + 16.dp, end = 16.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
        ) { items(12) { PosterSkeleton() } }

        state.error != null && state.results.isEmpty() -> Box(Modifier.padding(top = topPad)) { ErrorState(state.error, vm::reload) }

        state.results.isEmpty() -> Box(Modifier.padding(top = topPad)) {
            MessageState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.browse_no_results),
                message = stringResource(R.string.browse_no_results_hint),
                actionLabel = if (state.filtered) stringResource(R.string.browse_clear_filters) else null,
                onAction = if (state.filtered) vm::clearFilters else null,
            )
        }

        else -> {
            val status = mangaStatusLabel()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad + 12.dp, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.results, key = { it.id }) { m ->
                    PosterCard(m.toPoster(), onClick = { onMangaClick(m.id) }, modifier = Modifier.animateItem(), caption = status(m.status))
                }
                if (state.loadingMore || (!state.endReached && state.error == null)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { vm.loadMore(state.generation) }) { Text(stringResource(R.string.browse_load_failed_retry)) }
                        }
                    }
                }
            }
        }
    }
}
