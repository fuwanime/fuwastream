package com.fuwanime.ui.library

import com.fuwanime.ui.manga.MangaShelfViewModel
import com.fuwanime.ui.manga.MangaDownloadRows
import com.fuwanime.ui.manga.MangaLibraryTab
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.fuwanime.R
import androidx.compose.ui.platform.LocalContext
import com.fuwanime.ui.components.IconWithTooltip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.layout.heightIn
import com.fuwanime.ui.components.LocalNavBarPadding
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import com.fuwanime.ui.stats.WeekSummary
import com.fuwanime.ui.stats.watchTimeLabel
import com.fuwanime.ui.stats.weekSummary
import com.fuwanime.util.epochDay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.LaunchedEffect
import com.fuwanime.ui.components.TagChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fuwanime.util.remainingLabel
import com.fuwanime.util.historyDayLabel
import com.fuwanime.ui.components.ImageScrim
import androidx.compose.foundation.background
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.sharedEpisodeKey
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.data.Anime
import com.fuwanime.util.formatBytes
import com.fuwanime.data.local.DownloadEntity
import com.fuwanime.data.DownloadState
import com.fuwanime.data.DownloadRepository
import com.fuwanime.data.DownloadItem
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Delete
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.PosterCard
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import com.fuwanime.data.local.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val library: LibraryRepository,
    private val downloadRepo: DownloadRepository,
    private val settings: SettingsStore,
    private val refreshHomeSurfaces: () -> Unit = {},
) : ViewModel() {
    val downloads: StateFlow<List<DownloadItem>?> =
        downloadRepo.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteDownload(episodeId: String) = viewModelScope.launch { downloadRepo.delete(episodeId) }

    val history: StateFlow<List<WatchHistoryEntity>?> =
        library.continueWatching(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val favorites: StateFlow<List<FavoriteEntity>?> =
        library.favoriteList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteSort: StateFlow<FavoriteSort> =
        settings.favoriteSort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoriteSort.ADDED)

    fun setFavoriteSort(sort: FavoriteSort) = viewModelScope.launch { settings.setFavoriteSort(sort) }

    /** Favourites in the chosen order, each with its count of new episodes (0 when nothing new). */
    val favoriteCards: StateFlow<List<Pair<FavoriteEntity, Int>>?> =
        combine(library.favoriteList, library.allHistory, settings.notifyBaselines, settings.favoriteSort) { favs, all, baselines, sort ->
            val byAnime = all.groupBy { it.animeId }
            val lastWatched = byAnime.mapValues { (_, rows) -> rows.maxOf { it.updatedAt } }
            sortFavorites(favs, lastWatched, sort).map { fav ->
                val watched = byAnime[fav.animeId].orEmpty().map { it.episodeNumber }
                fav to newEpisodeCount(baselines[fav.animeId], watched)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Finished downloads of episodes already watched to the end. */
    val week: StateFlow<WeekSummary?> =
        combine(library.allHistory, library.watchDaily) { h, d -> weekSummary(h, d, epochDay(System.currentTimeMillis())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val watched: StateFlow<List<DownloadItem>> =
        combine(downloadRepo.downloads, library.allHistory) { dls, all -> watchedDownloads(dls, all.associateBy { it.episodeId }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteWatched() = viewModelScope.launch { watched.value.forEach { downloadRepo.delete(it.entity.episodeId) } }

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    /** animeIds picked in multi-select mode (Riwayat or Favorit); empty means not selecting. */
    val selection: StateFlow<Set<String>> = _selection

    fun toggleSelect(animeId: String) = _selection.update { if (animeId in it) it - animeId else it + animeId }
    fun clearSelection() { _selection.value = emptySet() }

    /** [page] 0 = Riwayat, 1 = Favorit. */
    fun deleteSelected(page: Int) = viewModelScope.launch {
        val ids = _selection.value
        when (page) {
            0 -> ids.forEach { library.removeFromHistory(it) }
            1 -> favorites.value.orEmpty().filter { it.animeId in ids }.forEach { library.setFavorite(it.toAnime(), false) }
        }
        clearSelection()
        if (page == 0) refreshHomeSurfaces()
    }

    fun removeHistory(animeId: String) = viewModelScope.launch { library.removeFromHistory(animeId); refreshHomeSurfaces() }
    fun clearHistory() = viewModelScope.launch { library.clearHistory(); refreshHomeSurfaces() }
}

fun FavoriteEntity.toAnime() = Anime(
    id = animeId,
    title = title,
    poster = poster,
    cover = cover,
    type = type,
    status = status,
    year = year,
    genres = genres.split(',').filter { it.isNotBlank() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onAnimeClick: OpenAnime,
    onResume: (WatchHistoryEntity) -> Unit,
    onPlayDownload: (DownloadEntity) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onMangaClick: (String) -> Unit,
    onMangaRead: (String, String) -> Unit,
) {
    val vm: LibraryViewModel = fuwaViewModel { c, _ -> LibraryViewModel(c.libraryRepository, c.downloadRepository, c.settingsStore, c::refreshHomeSurfaces) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val mangaVm: MangaShelfViewModel = fuwaViewModel { c, _ -> MangaShelfViewModel(c.mangaLibrary, c.mangaDownloads) }
    val mangaDownloads by mangaVm.downloads.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteCards by vm.favoriteCards.collectAsStateWithLifecycle()
    val favoriteSort by vm.favoriteSort.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    // Selection belongs to one tab; leaving it (or pressing back) ends multi-select without deleting anything.
    LaunchedEffect(pager.currentPage) { vm.clearSelection() }
    BackHandler(enabled = selecting) { vm.clearSelection() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                IconButton(onClick = { vm.clearSelection() }, modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.mine_cancel_select)) }
                Text(stringResource(R.string.mine_selected_count, selection.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (selection.size >= 2) confirmDeleteSelected = true else vm.deleteSelected(pager.currentPage)
                }) { Icon(Icons.Rounded.Delete, stringResource(R.string.mine_delete_selected)) }
            } else {
                Text(stringResource(R.string.common_tab_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (pager.currentPage == 0 && !history.isNullOrEmpty()) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.mine_clear_history)) }
                }
                IconWithTooltip(stringResource(R.string.mine_settings), Icons.Rounded.Settings, onSettings)
            }
        }
        week?.takeIf { !selecting }?.let { WeekCard(it, onStats) }
        SegmentedTabs(
            listOf(R.string.mine_tab_history to history?.size, R.string.mine_tab_favorites to favorites?.size, R.string.manga_tab to null, R.string.mine_tab_downloads to downloads?.size?.plus(mangaDownloads?.size ?: 0))
                .map { (label, count) -> stringResource(label).let { if (count != null && count > 0) stringResource(R.string.mine_tab_with_count, it, count) else it } },
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        )
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> HistoryList(history, onResume, onAnimeClick, onRemove = { vm.removeHistory(it) }, selection, vm::toggleSelect)
                1 -> FavoriteGrid(favoriteCards, favoriteSort, vm::setFavoriteSort, onAnimeClick, selection, vm::toggleSelect)
                2 -> MangaLibraryTab(onOpen = onMangaClick, onRead = onMangaRead)
                else -> Column(Modifier.fillMaxSize()) {
                    // Manga downloads sit above the anime ones, one row per title.
                    MangaDownloadRows(mangaDownloads.orEmpty(), onOpen = onMangaClick, onDelete = mangaVm::deleteDownloads)
                    Box(Modifier.weight(1f)) {
                        DownloadList(downloads, watched, onPlayDownload, onDelete = { vm.deleteDownload(it) }, onDeleteWatched = { vm.deleteWatched() }, showEmpty = mangaDownloads.isNullOrEmpty())
                    }
                }
            }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(pluralStringResource(R.plurals.mine_delete_items_title, selection.size, selection.size)) },
            text = {
                Text(stringResource(if (pager.currentPage == 0) R.string.mine_delete_history_selected else R.string.mine_delete_favorites_selected))
            },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(pager.currentPage); confirmDeleteSelected = false }) { Text(stringResource(R.string.mine_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(stringResource(R.string.mine_clear_history_title)) },
            text = { Text(stringResource(R.string.mine_clear_history_body)) },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.mine_delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }
}

@Composable
private fun HistoryList(
    items: List<WatchHistoryEntity>?,
    onResume: (WatchHistoryEntity) -> Unit,
    onAnimeClick: OpenAnime,
    onRemove: (String) -> Unit,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(Icons.Rounded.History, stringResource(R.string.mine_history_empty_title), message = stringResource(R.string.mine_history_empty_body))
        return
    }
    val res = LocalContext.current.resources
    val groups = remember(items) { items.groupBy { historyDayLabel(res, it.updatedAt) } }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 16.dp + LocalNavBarPadding.current)) {
        groups.forEach { (day, entries) ->
            item(key = "day-$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.em,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                )
            }
            items(entries, key = { it.episodeId }) { entry ->
                HistoryRow(entry, onResume, onAnimeClick, onRemove, selected = entry.animeId in selection, selecting = selection.isNotEmpty(), onToggleSelect)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: WatchHistoryEntity,
    onResume: (WatchHistoryEntity) -> Unit,
    onAnimeClick: OpenAnime,
    onRemove: (String) -> Unit,
    selected: Boolean,
    selecting: Boolean,
    onToggleSelect: (String) -> Unit,
) {
    val selectLabel = stringResource(R.string.common_select)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(
                onLongClick = { onToggleSelect(entry.animeId) },
                onClick = { if (selecting) onToggleSelect(entry.animeId) else onResume(entry) },
            )
            // TalkBack has no long-press gesture: offer "Pilih" in its actions menu.
            .semantics { customActions = listOf(CustomAccessibilityAction(selectLabel) { onToggleSelect(entry.animeId); true }) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(12.dp))) {
            NetImage(
                entry.episodeThumbnail.ifBlank { entry.animeCover },
                Modifier.fillMaxSize().sharedKey(sharedEpisodeKey(entry.episodeId, "library")).clip(RoundedCornerShape(12.dp)),
                // The only place the anime's name is inside the row's TalkBack node (the title text is its own button).
                stringResource(R.string.mine_episode_cd, entry.animeTitle, entry.episodeNumber),
            )
            Text(
                stringResource(R.string.mine_ep_badge, entry.episodeNumber),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ImageScrim.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            LinearProgressIndicator(
                progress = { entry.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                trackColor = Color.White.copy(alpha = 0.2f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.animeTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = !selecting) {
                    onAnimeClick(Anime(entry.animeId, entry.animeTitle, poster = entry.animePoster, cover = entry.animeCover), "library-history")
                },
            )
            Text(
                when {
                    entry.isFinished -> stringResource(R.string.common_finished_watching)
                    else -> remainingLabel(LocalContext.current.resources, entry.positionMs, entry.durationMs) ?: entry.episodeTitle
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                stringResource(if (selected) R.string.common_selected_cd else R.string.mine_not_selected),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            IconButton(onClick = { onRemove(entry.animeId) }) { Icon(Icons.Rounded.Close, stringResource(R.string.mine_remove_from_history)) }
        }
    }
}

/** Pill-shaped tab switcher that follows the pager. */
@Composable
private fun SegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        titles.forEachIndexed { i, title ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FavoriteGrid(
    items: List<Pair<FavoriteEntity, Int>>?,
    sort: FavoriteSort,
    onSort: (FavoriteSort) -> Unit,
    onAnimeClick: OpenAnime,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(Icons.Rounded.FavoriteBorder, stringResource(R.string.mine_favorites_empty_title), message = stringResource(R.string.mine_favorites_empty_body))
        return
    }
    val res = LocalContext.current.resources
    val selecting = selection.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavoriteSort.entries.forEach { option ->
                    TagChip(stringResource(option.label), onClick = { onSort(option) }, selected = option == sort)
                }
            }
        }
        items(items, key = { it.first.animeId }) { (fav, newCount) ->
            val anime = fav.toAnime()
            PosterCard(
                anime,
                onClick = { if (selecting) onToggleSelect(fav.animeId) else onAnimeClick(anime, "library-fav") },
                sharedOrigin = "library-fav",
                badge = newBadgeLabel(res, newCount),
                onLongClick = { onToggleSelect(fav.animeId) },
                selected = fav.animeId in selection,
            )
        }
    }
}

@Composable
private fun DownloadList(
    items: List<DownloadItem>?,
    watched: List<DownloadItem>,
    onPlay: (DownloadEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteWatched: () -> Unit,
    showEmpty: Boolean = true,
) {
    if (items == null) return
    if (items.isEmpty()) {
        if (!showEmpty) return
        MessageState(
            Icons.Rounded.DownloadForOffline,
            stringResource(R.string.mine_downloads_empty_title),
            message = stringResource(R.string.mine_downloads_empty_body),
        )
        return
    }
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmWatched by remember { mutableStateOf(false) }
    val watchedBytes = watched.sumOf { it.totalBytes }
    val res = LocalContext.current.resources
    val doneCount = items.count { it.state == DownloadState.DONE }
    val stored = items.filter { it.state == DownloadState.DONE }.sumOf { it.totalBytes }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                pluralStringResource(R.plurals.mine_downloads_stored, doneCount, doneCount, formatBytes(res, stored)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (watched.isNotEmpty()) {
            item(key = "watched") {
                TextButton(onClick = { confirmWatched = true }) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mine_delete_watched, watched.size, formatBytes(res, watchedBytes)))
                }
            }
        }
        items(items, key = { it.entity.episodeId }) { d ->
            val e = d.entity
            val playable = d.state == DownloadState.DONE
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = playable) { onPlay(e) }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {
                    NetImage(
                        e.episodeThumbnail.ifBlank { e.animePoster },
                        Modifier.fillMaxSize(),
                        stringResource(R.string.mine_episode_cd, e.animeTitle, e.episodeNumber),
                    )
                    if (playable) {
                        Icon(
                            Icons.Rounded.PlayCircle, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.animeTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.mine_episode_quality, e.episodeNumber, e.quality), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when (d.state) {
                            DownloadState.DONE -> formatBytes(LocalContext.current.resources, d.totalBytes)
                            DownloadState.QUEUED -> stringResource(R.string.mine_download_queued)
                            DownloadState.PAUSED -> stringResource(R.string.mine_download_paused)
                            DownloadState.RUNNING -> stringResource(R.string.mine_download_running, (d.progress * 100).toInt(), formatBytes(res, d.downloadedBytes))
                            DownloadState.FAILED -> stringResource(R.string.mine_download_failed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (d.inProgress) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            drawStopIndicator = {},
                            gapSize = 0.dp,
                        )
                    }
                }
                IconButton(onClick = { pendingDelete = d }) {
                    Icon(if (d.inProgress) Icons.Rounded.Close else Icons.Rounded.Delete, stringResource(if (d.inProgress) R.string.mine_cancel_action else R.string.mine_delete))
                }
            }
        }
    }

    if (confirmWatched) {
        AlertDialog(
            onDismissRequest = { confirmWatched = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(pluralStringResource(R.plurals.mine_delete_downloads_title, watched.size, watched.size)) },
            text = { Text(stringResource(R.string.mine_delete_downloads_body, formatBytes(res, watchedBytes))) },
            confirmButton = { TextButton(onClick = { onDeleteWatched(); confirmWatched = false }) { Text(stringResource(R.string.mine_delete)) } },
            dismissButton = { TextButton(onClick = { confirmWatched = false }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }

    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(if (d.inProgress) R.string.mine_cancel_download_title else R.string.mine_delete_download_title)) },
            text = { Text(stringResource(R.string.mine_download_item, d.entity.animeTitle, d.entity.episodeNumber)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(d.entity.episodeId)
                    pendingDelete = null
                }) { Text(stringResource(if (d.inProgress) R.string.mine_cancel_action else R.string.mine_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.mine_close)) } },
        )
    }
}

/** Koleksi's way into Statistik: this week at a glance, the whole card opens the full screen. */
@Composable
private fun WeekCard(week: WeekSummary, onClick: () -> Unit) {
    val watchedAny = week.watchedMs > 0 || week.episodes > 0
    val summary = pluralStringResource(R.plurals.mine_week_summary, week.episodes, watchTimeLabel(LocalContext.current.resources, week.watchedMs), week.episodes)
    val cd = if (watchedAny) stringResource(R.string.mine_week_cd_watched, summary) else stringResource(R.string.mine_week_cd_empty)
    val bar = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = cd
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.mine_this_week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (watchedAny) {
                Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.mine_week_start), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val max = (week.days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
            Canvas(Modifier.width(84.dp).height(32.dp)) {
                val slot = size.width / week.days.size
                val w = slot * 0.6f
                week.days.forEachIndexed { i, d ->
                    val h = (size.height * d.minutes / max).coerceAtLeast(3.dp.toPx())
                    val color = if (i == week.days.lastIndex) bar else bar.copy(alpha = 0.4f)
                    drawRoundRect(color, Offset(slot * i + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 2))
                }
            }
            Text(stringResource(R.string.mine_see_stats), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
