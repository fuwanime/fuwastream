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
import com.fuwanime.data.manga.MangaPage
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.MangaTag
import com.fuwanime.data.manga.filterManga
import com.fuwanime.data.manga.dedupeByTitle
import com.fuwanime.data.manga.MangaType
import com.fuwanime.data.manga.sameTitle
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

private const val PREFETCH = 6
private const val PAGE_BATCH = 30

/** Load more when near end and more items may be available. */
fun shouldLoadMore(lastVisible: Int, loaded: Int, total: Int): Boolean =
    loaded < total && lastVisible >= loaded - PREFETCH

/** The Comix genre id a genre name from any source stands for, or null (Indonesian-only names have none). */
fun genreTagId(name: String, tags: List<MangaTag>): String? = tags.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id

/** A genre tapped on a manga page, waiting for the Manga tab to show it as a filter. */
val pendingMangaGenre = MutableStateFlow<String?>(null)

private fun orderFor(query: String) = if (query.isBlank()) MangaOrder.POPULAR else MangaOrder.RELEVANCE

data class MangaExploreState(
    val query: String = "",
    val status: MangaStatus? = null,
    val tagIds: Set<String> = emptySet(),
    val tags: List<MangaTag> = emptyList(),
    val type: MangaType? = null,
    val results: List<Manga> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    /** Bumps with every fresh first page, so UI state tied to the old result list (scroll, pending paging) can be dropped. */
    val generation: Int = 0,
    /** English catalog failed; the shown results come from Indonesian sources. */
    val catalogDown: Boolean = false,
) {
    val endReached get() = results.size >= total
    val filtered get() = query.isNotBlank() || status != null || tagIds.isNotEmpty() || type != null
}

@OptIn(FlowPreview::class)
class MangaExploreViewModel(private val repo: MangaRepository) : ViewModel() {
    private val _state = MutableStateFlow(MangaExploreState())
    val state: StateFlow<MangaExploreState> = _state.asStateFlow()
    private val retry = MutableStateFlow(0)
    private var moreJob: Job? = null
    private var started = false

    private data class Applied(val q: String = "", val status: MangaStatus? = null, val tags: Set<String> = emptySet(), val type: MangaType? = null)

    // What the shown results were fetched with (the query is debounced, so it can lag behind state.query).
    private var applied = Applied()
    private val raw = mutableListOf<Manga>()
    private val seenIds = HashSet<String>()
    private var englishPage = 0
    private var indoPage = 0
    private var englishHasMore = true
    private var indoHasMore = true
    private var englishError: UiText? = null

    /** Starts the search pipeline when manga mode is first shown. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            // Empty query (initial load, or cleared) applies at once; typing waits 400 ms.
            val query = _state.map { it.query }.debounce { if (it.isEmpty()) 0L else 400L }.distinctUntilChanged()
            val filters = _state.map { Triple(it.status, it.tagIds, it.type) }.distinctUntilChanged()
            combine(query, filters, retry) { q, f, _ -> Applied(q, f.first, f.second, f.third) }
                .collectLatest { applied = it; loadFirst(it) }
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
    fun clearFilters() = _state.update { it.copy(query = "", status = null, tagIds = emptySet(), type = null) }
    fun setType(t: MangaType?) = _state.update { it.copy(type = t) }
    fun reload() { retry.update { it + 1 } }

    /** Shows one genre: try tag filter if known, else as a text search. */
    fun applyGenre(name: String) {
        viewModelScope.launch {
            val tags = withTimeoutOrNull(3_000) { _state.first { it.tags.isNotEmpty() }.tags }.orEmpty()
            val id = genreTagId(name, tags)
            _state.update { if (id != null) it.copy(query = "", status = null, tagIds = setOf(id)) else it.copy(query = name, status = null, tagIds = emptySet()) }
        }
    }

    private suspend fun <T> quiet(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun resetPages() {
        raw.clear()
        seenIds.clear()
        englishPage = 0
        indoPage = 0
        englishHasMore = true
        indoHasMore = true
        englishError = null
    }

    private suspend fun loadFirst(a: Applied) {
        moreJob?.cancel()
        resetPages()
        val gen = _state.value.generation + 1
        _state.update { it.copy(results = emptyList(), total = 0, loading = true, loadingMore = false, error = null, generation = gen, catalogDown = false) }
        fetchNextBatch(a, initial = true, generation = gen)
    }

    /** [generation] is the one the caller saw; a request from before a filter change is ignored. */
    fun loadMore(generation: Int) {
        val s = _state.value
        if (generation != s.generation || s.loading || s.loadingMore || s.endReached) return
        val a = applied
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            fetchNextBatch(a, initial = false, generation = generation)
        }
    }

    private suspend fun fetchNextBatch(a: Applied, initial: Boolean, generation: Int) {
        if (_state.value.generation != generation) return
        val searching = a.q.isNotBlank()
        val nextEnglishPage = if (englishHasMore) englishPage + 1 else -1
        val nextIndoPage = if (indoHasMore) indoPage + 1 else -1

        val (englishPageResult, indoResult) = coroutineScope {
            val en = async {
                if (nextEnglishPage < 0) return@async null
                if (searching && englishPage >= 1) return@async null
                try {
                    val offset = (nextEnglishPage - 1) * PAGE_BATCH
                    Result.success(repo.browse(orderFor(a.q), a.q, a.tags.toList(), a.status, if (searching) 0 else offset, a.type))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            val id = async {
                if (nextIndoPage < 0) return@async null
                if (searching) {
                    try { repo.searchIndonesiaPaged(a.q, nextIndoPage) } catch (e: CancellationException) { throw e } catch (_: Exception) { MangaPage(emptyList(), 0, 0) }
                } else {
                    // Browse: combine Shinigami + Komiku popular pages.
                    val sh = quiet { repo.popularIndonesia(nextIndoPage, a.type) }
                    val km = quiet { repo.popularKomiku(nextIndoPage, a.type) }
                    val items = (sh?.items.orEmpty() + km?.items.orEmpty()).distinctBy { it.id }
                    val shMore = sh != null && sh.items.isNotEmpty() && sh.items.size >= PAGE_BATCH / 2 && sh.offset + sh.items.size < sh.total
                    val kmMore = km != null && km.items.isNotEmpty()
                    val hasMore = shMore || kmMore
                    MangaPage(items, if (hasMore) nextIndoPage * PAGE_BATCH + 1 else nextIndoPage * PAGE_BATCH, (nextIndoPage - 1) * PAGE_BATCH)
                }
            }
            en.await() to id.await()
        }

        if (nextEnglishPage > 0) {
            val page = englishPageResult?.getOrNull()
            if (page != null) {
                englishPage = nextEnglishPage
                englishHasMore = if (searching) false else page.offset + page.items.size < page.total
                page.items.forEach { if (seenIds.add(it.id)) raw += it }
            } else {
                if (searching || initial) {
                    englishHasMore = false
                    englishError = englishPageResult?.exceptionOrNull()?.friendlyText()
                } else {
                    // transient failure on "load more": stop fetching English but keep existing results
                    englishHasMore = false
                }
            }
        }
        if (nextIndoPage > 0 && indoResult != null) {
            indoPage = nextIndoPage
            indoHasMore = if (searching) indoResult.offset + indoResult.items.size < indoResult.total else indoResult.offset + indoResult.items.size < indoResult.total
            indoResult.items.forEach { if (seenIds.add(it.id)) raw += it }
        }

        refilter()

        val hasMore = englishHasMore || indoHasMore
        val filtered = _state.value.results
        val needMore = filtered.size < PAGE_BATCH && hasMore && _state.value.generation == generation

        if (needMore) {
            // Client-side filters (status/genre/type) can drop many items; keep fetching to fill the screen.
            fetchNextBatch(a, initial = false, generation = generation)
            return
        }

        val error = when {
            filtered.isNotEmpty() -> null
            a.tags.isNotEmpty() && raw.isNotEmpty() -> null
            else -> englishError
        }
        _state.update {
            it.copy(
                loading = false,
                loadingMore = false,
                total = if (hasMore) filtered.size + 1 else filtered.size,
                error = if (initial) error else if (filtered.isEmpty()) error else null,
                catalogDown = englishPage == 0 && indoPage > 0,
            )
        }
    }

    private fun stateSnapshotGeneration(): Int = _state.value.generation

    private fun refilter(): Int {
        val a = applied
        val filtered = filterManga(dedupeByTitle(raw.toList()), a.status, a.type, a.tags)
        val hasMore = englishHasMore || indoHasMore
        _state.update { it.copy(results = filtered, total = if (hasMore) filtered.size + 1 else filtered.size) }
        return filtered.size
    }
}

/** One filter row under the search field: the genre picker (a sheet) and the publication status. */
@Composable
fun MangaFilterBar(state: MangaExploreState, vm: MangaExploreViewModel) {
    var genresOpen by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.tags.isNotEmpty()) item { GenreChip(state.tagIds.size) { genresOpen = true } }
        item { TagChip(stringResource(R.string.manga_filter_all), onClick = { vm.setStatus(null) }, selected = state.status == null) }
        item { TagChip(stringResource(R.string.manga_status_ongoing), onClick = { vm.setStatus(MangaStatus.ONGOING) }, selected = state.status == MangaStatus.ONGOING) }
        item { TagChip(stringResource(R.string.manga_status_completed), onClick = { vm.setStatus(MangaStatus.COMPLETED) }, selected = state.status == MangaStatus.COMPLETED) }
        items(MangaType.entries) { t -> TagChip(t.label, onClick = { vm.setType(if (state.type == t) null else t) }, selected = state.type == t) }
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
                if (state.catalogDown) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MangaSourceNotice(vm::reload, Modifier.padding(bottom = 4.dp))
                    }
                }
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
