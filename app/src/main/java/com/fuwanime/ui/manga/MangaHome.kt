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
    val popular: MangaShelf = MangaShelf(),
    val latest: MangaShelf = MangaShelf(),
    val continueReading: List<MangaHistoryEntity> = emptyList(),
    val refreshing: Boolean = false,
)

class MangaHomeViewModel(private val repo: MangaRepository, library: MangaLibrary) : ViewModel() {
    private val indonesia = MutableStateFlow(MangaShelf())
    private val popular = MutableStateFlow(MangaShelf())
    private val latest = MutableStateFlow(MangaShelf())
    private val refreshing = MutableStateFlow(false)
    private var started = false

    val state: StateFlow<MangaHomeState> = combine(indonesia, popular, latest, library.continueReading, refreshing) { i, p, l, c, r -> MangaHomeState(i, p, l, c, r) }
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
                launch { load(popular, MangaOrder.POPULAR) }
                launch { load(latest, MangaOrder.LATEST) }
            }
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
fun LazyListScope.mangaHomeItems(state: MangaHomeState, topPad: Dp, onRetry: () -> Unit, onMangaClick: (String) -> Unit, onRead: (mangaId: String, chapterId: String) -> Unit) {
    item(key = "mTop") { Spacer(Modifier.height(topPad)) }
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
    shelf(R.string.manga_home_popular_id, "mIndonesia", state.indonesia, onRetry, onMangaClick)
    shelf(R.string.manga_home_popular, "mPopular", state.popular, onRetry, onMangaClick)
    shelf(R.string.manga_home_latest, "mLatest", state.latest, onRetry, onMangaClick)
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
