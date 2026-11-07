package com.fuwanime.ui.library

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

    /** Favourites in the chosen order, each with its "N baru" label (null when nothing new). */
    val favoriteCards: StateFlow<List<Pair<FavoriteEntity, String?>>?> =
        combine(library.favoriteList, library.allHistory, settings.notifyBaselines, settings.favoriteSort) { favs, all, baselines, sort ->
            val byAnime = all.groupBy { it.animeId }
            val lastWatched = byAnime.mapValues { (_, rows) -> rows.maxOf { it.updatedAt } }
            sortFavorites(favs, lastWatched, sort).map { fav ->
                val watched = byAnime[fav.animeId].orEmpty().map { it.episodeNumber }
                fav to newBadgeLabel(newEpisodeCount(baselines[fav.animeId], watched))
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
) {
    val vm: LibraryViewModel = fuwaViewModel { c, _ -> LibraryViewModel(c.libraryRepository, c.downloadRepository, c.settingsStore, c::refreshHomeSurfaces) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteCards by vm.favoriteCards.collectAsStateWithLifecycle()
    val favoriteSort by vm.favoriteSort.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 3 }
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
                IconButton(onClick = { vm.clearSelection() }, modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Rounded.Close, "Batal pilih") }
                Text("${selection.size} dipilih", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (selection.size >= 2) confirmDeleteSelected = true else vm.deleteSelected(pager.currentPage)
                }) { Icon(Icons.Rounded.Delete, "Hapus yang dipilih") }
            } else {
                Text("Koleksi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (pager.currentPage == 0 && !history.isNullOrEmpty()) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Hapus riwayat") }
                }
                IconWithTooltip("Pengaturan", Icons.Rounded.Settings, onSettings)
            }
        }
        week?.takeIf { !selecting }?.let { WeekCard(it, onStats) }
        SegmentedTabs(
            listOf("Riwayat" to history?.size, "Favorit" to favorites?.size, "Unduhan" to downloads?.size)
                .map { (label, count) -> if (count != null && count > 0) "$label · $count" else label },
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        )
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> HistoryList(history, onResume, onAnimeClick, onRemove = { vm.removeHistory(it) }, selection, vm::toggleSelect)
                1 -> FavoriteGrid(favoriteCards, favoriteSort, vm::setFavoriteSort, onAnimeClick, selection, vm::toggleSelect)
                else -> DownloadList(downloads, watched, onPlayDownload, onDelete = { vm.deleteDownload(it) }, onDeleteWatched = { vm.deleteWatched() })
            }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Hapus ${selection.size} item?") },
            text = {
                Text(if (pager.currentPage == 0) "Progres tontonan anime yang dipilih akan dihapus." else "Anime yang dipilih akan dihapus dari Favorit.")
            },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(pager.currentPage); confirmDeleteSelected = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text("Batal") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Progres tontonan semua anime akan dihapus. Favorit tidak terpengaruh.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
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
        MessageState(Icons.Rounded.History, "Belum ada riwayat", message = "Episode yang kamu tonton akan muncul di sini.")
        return
    }
    val groups = remember(items) { items.groupBy { historyDayLabel(it.updatedAt) } }
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
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(
                onLongClick = { onToggleSelect(entry.animeId) },
                onClick = { if (selecting) onToggleSelect(entry.animeId) else onResume(entry) },
            )
            // TalkBack has no long-press gesture: offer "Pilih" in its actions menu.
            .semantics { customActions = listOf(CustomAccessibilityAction("Pilih") { onToggleSelect(entry.animeId); true }) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(12.dp))) {
            NetImage(
                entry.episodeThumbnail.ifBlank { entry.animeCover },
                Modifier.fillMaxSize().sharedKey(sharedEpisodeKey(entry.episodeId, "library")).clip(RoundedCornerShape(12.dp)),
                // The only place the anime's name is inside the row's TalkBack node (the title text is its own button).
                "${entry.animeTitle} — Episode ${entry.episodeNumber}",
            )
            Text(
                "EP ${entry.episodeNumber}",
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
                    entry.isFinished -> "Selesai ditonton"
                    else -> remainingLabel(entry.positionMs, entry.durationMs) ?: entry.episodeTitle
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (selected) "Dipilih" else "Tidak dipilih",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            IconButton(onClick = { onRemove(entry.animeId) }) { Icon(Icons.Rounded.Close, "Hapus dari riwayat") }
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
    items: List<Pair<FavoriteEntity, String?>>?,
    sort: FavoriteSort,
    onSort: (FavoriteSort) -> Unit,
    onAnimeClick: OpenAnime,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(Icons.Rounded.FavoriteBorder, "Belum ada favorit", message = "Ketuk ikon hati di halaman anime untuk menyimpannya di sini.")
        return
    }
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
                    TagChip(option.label, onClick = { onSort(option) }, selected = option == sort)
                }
            }
        }
        items(items, key = { it.first.animeId }) { (fav, badge) ->
            val anime = fav.toAnime()
            PosterCard(
                anime,
                onClick = { if (selecting) onToggleSelect(fav.animeId) else onAnimeClick(anime, "library-fav") },
                sharedOrigin = "library-fav",
                badge = badge,
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
) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(
            Icons.Rounded.DownloadForOffline,
            "Belum ada unduhan",
            message = "Ketuk ikon download di daftar episode atau di pemutar untuk menonton tanpa internet.",
        )
        return
    }
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmWatched by remember { mutableStateOf(false) }
    val watchedBytes = watched.sumOf { it.totalBytes }
    val stored = items.filter { it.state == DownloadState.DONE }.sumOf { it.totalBytes }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${items.count { it.state == DownloadState.DONE }} episode tersimpan • ${formatBytes(stored)}",
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
                    Text("Hapus yang sudah ditonton (${watched.size} • ${formatBytes(watchedBytes)})")
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
                        "${e.animeTitle} — Episode ${e.episodeNumber}",
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
                    Text("Episode ${e.episodeNumber} • ${e.quality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when (d.state) {
                            DownloadState.DONE -> formatBytes(d.totalBytes)
                            DownloadState.QUEUED -> "Menunggu antrean…"
                            DownloadState.PAUSED -> "Dijeda, menunggu jaringan…"
                            DownloadState.RUNNING -> "${(d.progress * 100).toInt()}% • ${formatBytes(d.downloadedBytes)}"
                            DownloadState.FAILED -> "Gagal. Hapus lalu download ulang"
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
                    Icon(if (d.inProgress) Icons.Rounded.Close else Icons.Rounded.Delete, if (d.inProgress) "Batalkan" else "Hapus")
                }
            }
        }
    }

    if (confirmWatched) {
        AlertDialog(
            onDismissRequest = { confirmWatched = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Hapus ${watched.size} unduhan?") },
            text = { Text("Episode yang sudah selesai ditonton akan dihapus dan ${formatBytes(watchedBytes)} dikosongkan.") },
            confirmButton = { TextButton(onClick = { onDeleteWatched(); confirmWatched = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmWatched = false }) { Text("Batal") } },
        )
    }

    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (d.inProgress) "Batalkan download?" else "Hapus download?") },
            text = { Text("${d.entity.animeTitle} Episode ${d.entity.episodeNumber}") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(d.entity.episodeId)
                    pendingDelete = null
                }) { Text(if (d.inProgress) "Batalkan" else "Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Tutup") } },
        )
    }
}

/** Koleksi's way into Statistik: this week at a glance, the whole card opens the full screen. */
@Composable
private fun WeekCard(week: WeekSummary, onClick: () -> Unit) {
    val watchedAny = week.watchedMs > 0 || week.episodes > 0
    val summary = "${watchTimeLabel(week.watchedMs)} · ${week.episodes} episode"
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
                contentDescription = if (watchedAny) "Statistik minggu ini, $summary. Buka statistik" else "Statistik. Belum ada tontonan minggu ini. Buka statistik"
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Minggu ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (watchedAny) {
                Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Text("Mulai nonton untuk melihat statistikmu", style = MaterialTheme.typography.bodyMedium)
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
            Text("Lihat statistik ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
