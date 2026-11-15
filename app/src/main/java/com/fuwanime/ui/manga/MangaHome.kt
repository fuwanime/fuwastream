package com.fuwanime.ui.manga

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.manga.MangaOrder
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.PosterRow
import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.util.UiText
import com.fuwanime.util.friendlyText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaShelf(val items: List<Manga> = emptyList(), val loading: Boolean = true, val error: UiText? = null)

data class MangaHomeState(
    val indonesia: MangaShelf = MangaShelf(),
    val komiku: MangaShelf = MangaShelf(),
    val popular: MangaShelf = MangaShelf(),
    val latest: MangaShelf = MangaShelf(),
    val continueReading: List<MangaHistoryEntity> = emptyList(),
    val refreshing: Boolean = false,
    /** Comix failed; the Indonesian shelves stay visible and the Comix ones are hidden. */
    val catalogDown: Boolean = false,
)

class MangaHomeViewModel(private val repo: MangaRepository, library: MangaLibrary) : ViewModel() {
    private val indonesia = MutableStateFlow(MangaShelf())
    private val komiku = MutableStateFlow(MangaShelf())
    private val popular = MutableStateFlow(MangaShelf())
    private val latest = MutableStateFlow(MangaShelf())
    private val catalogDown = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private var started = false

    val state: StateFlow<MangaHomeState> = combine(
        combine(indonesia, komiku, ::Pair),
        popular,
        latest,
        combine(library.continueReading, catalogDown, ::Pair),
        refreshing,
    ) { ik, p, l, cd, r ->
        MangaHomeState(ik.first, ik.second, p, l, cd.first, r, catalogDown = cd.second)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaHomeState())

    /** Loads once, when manga mode is first shown. */
    fun start() {
        if (!started) refresh()
    }

    fun refresh() {
        started = true
        refreshing.value = true
        viewModelScope.launch {
            coroutineScope {
                launch { loadWith(indonesia) { repo.popularIndonesia().items } }
                launch { loadWith(komiku) { (1..3).flatMap { repo.popularKomiku(it).items } } }
                launch { load(popular, MangaOrder.POPULAR) }
                launch { load(latest, MangaOrder.LATEST) }
            }
            // Only show the source-specific notice when at least one Indonesian source really works.
            // If every source failed (for example, fully offline), keep the normal connection errors.
            val comixFailed = popular.value.error != null || latest.value.error != null
            val indonesiaAvailable = indonesia.value.error == null || komiku.value.error == null
            catalogDown.value = comixFailed && indonesiaAvailable
            refreshing.value = false
        }
    }

    private suspend fun load(shelf: MutableStateFlow<MangaShelf>, order: MangaOrder) = loadWith(shelf) { repo.browse(order).items }

    private suspend fun loadWith(shelf: MutableStateFlow<MangaShelf>, fetch: suspend () -> List<Manga>) {
        shelf.update { it.copy(loading = true, error = null) }
        try {
            shelf.value = MangaShelf(fetch(), loading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep what is already on screen when only a refresh failed.
            shelf.update { it.copy(loading = false, error = e.friendlyText()) }
        }
    }
}

/** Manga mode of Beranda: the items of the Home list. [topPad] clears the top bar and the mode switch. */
fun LazyListScope.mangaHomeItems(
    state: MangaHomeState,
    topPad: Dp,
    onRetry: () -> Unit,
    onMangaClick: (String) -> Unit,
    onRead: (mangaId: String, chapterId: String) -> Unit,
    /** Row between the Comix shelves and the Indonesian ones (genre shortcuts). */
    middle: (@Composable () -> Unit)? = null,
) {
    item(key = "mTop") { Spacer(Modifier.height(topPad)) }
    if (state.catalogDown) {
        item(key = "mCatalogDown") {
            MangaSourceNotice(onRetry, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
    if (state.continueReading.isNotEmpty()) {
        item(key = "mContinue") {
            val posters = state.continueReading.map { Anime(id = it.mangaId, title = it.mangaTitle, poster = it.coverUrl.orEmpty()) }
            val labels = state.continueReading.associate { it.mangaId to it.chapterLabel }
            val chapterOf = state.continueReading.associate { it.mangaId to it.chapterId }
            MangaSection(R.string.manga_home_continue) {
                PosterRow(posters, loading = false, onClick = { a -> chapterOf[a.id]?.let { onRead(a.id, it) } ?: onMangaClick(a.id) }, caption = { labels[it.id] })
            }
        }
    }
    // Comix is the main catalogue, so its shelves come first; Indonesian sources stay below.
    // Keep cached Comix shelves on screen, but don't show two error cards when Comix is blocked.
    if (!state.catalogDown || state.popular.items.isNotEmpty()) {
        shelf(R.string.manga_home_popular, "mPopular", state.popular, onRetry, onMangaClick)
    }
    if (!state.catalogDown || state.latest.items.isNotEmpty()) {
        shelf(R.string.manga_home_latest, "mLatest", state.latest, onRetry, onMangaClick)
    }
    if (middle != null) item(key = "mMiddle") { middle() }
    shelf(R.string.manga_home_popular_id, "mIndonesia", state.indonesia, onRetry, onMangaClick)
    shelf(R.string.manga_home_komiku, "mKomiku", state.komiku, onRetry, onMangaClick)
}

private fun LazyListScope.shelf(title: Int, key: String, shelf: MangaShelf, onRetry: () -> Unit, onMangaClick: (String) -> Unit) {
    if (shelf.error != null && shelf.items.isEmpty()) {
        item(key = key) { MangaSection(title) { ErrorState(shelf.error, onRetry) } }
    } else {
        item(key = key) {
            val status = mangaStatusLabel()
            val byId = shelf.items.associateBy { it.id }
            MangaSection(title) {
                PosterRow(shelf.items.map { it.toPoster() }, shelf.loading, { onMangaClick(it.id) }, caption = { a -> byId[a.id]?.let { status(it.status) } })
            }
        }
    }
}

@Composable
private fun MangaSection(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionHeader(stringResource(title))
        Spacer(Modifier.height(12.dp))
        content()
    }
}
