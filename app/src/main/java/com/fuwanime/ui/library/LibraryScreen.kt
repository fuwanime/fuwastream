package com.fuwanime.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import com.fuwanime.util.formatDuration
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class LibraryViewModel(private val library: LibraryRepository, private val downloadRepo: DownloadRepository) : ViewModel() {
    val downloads: StateFlow<List<DownloadItem>?> =
        downloadRepo.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteDownload(episodeId: String) = viewModelScope.launch { downloadRepo.delete(episodeId) }

    val history: StateFlow<List<WatchHistoryEntity>?> =
        library.continueWatching(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val favorites: StateFlow<List<FavoriteEntity>?> =
        library.favoriteList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removeHistory(animeId: String) = viewModelScope.launch { library.removeFromHistory(animeId) }
    fun clearHistory() = viewModelScope.launch { library.clearHistory() }
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
) {
    val vm: LibraryViewModel = fuwaViewModel { c, _ -> LibraryViewModel(c.libraryRepository, c.downloadRepository) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Koleksi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (pager.currentPage == 0 && !history.isNullOrEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Hapus riwayat") }
            }
        }
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            listOf("Riwayat" to history?.size, "Favorit" to favorites?.size, "Unduhan" to downloads?.size).forEachIndexed { i, (label, count) ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(if (count != null && count > 0) "$label ($count)" else label) },
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> HistoryList(history, onResume, onAnimeClick, onRemove = { vm.removeHistory(it) })
                1 -> FavoriteGrid(favorites, onAnimeClick)
                else -> DownloadList(downloads, onPlayDownload, onDelete = { vm.deleteDownload(it) })
            }
        }
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
) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(Icons.Rounded.History, "Belum ada riwayat", message = "Episode yang kamu tonton akan muncul di sini.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.episodeId }) { entry ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onResume(entry) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {
                    NetImage(
                        entry.episodeThumbnail.ifBlank { entry.animeCover },
                        Modifier.fillMaxSize().sharedKey(sharedEpisodeKey(entry.episodeId, "library")).clip(MaterialTheme.shapes.small),
                    )
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.animeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            onAnimeClick(Anime(entry.animeId, entry.animeTitle, poster = entry.animePoster, cover = entry.animeCover), "library-history")
                        },
                    )
                    Text("Episode ${entry.episodeNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (entry.isFinished) "Selesai ditonton" else "${formatDuration(entry.positionMs)} / ${formatDuration(entry.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                IconButton(onClick = { onRemove(entry.animeId) }) { Icon(Icons.Rounded.Close, "Hapus dari riwayat") }
            }
        }
    }
}

@Composable
private fun FavoriteGrid(items: List<FavoriteEntity>?, onAnimeClick: OpenAnime) {
    if (items == null) return
    if (items.isEmpty()) {
        MessageState(Icons.Rounded.FavoriteBorder, "Belum ada favorit", message = "Ketuk ikon hati di halaman anime untuk menyimpannya di sini.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.animeId }) { fav ->
            val anime = fav.toAnime()
            PosterCard(anime, onClick = { onAnimeClick(anime, "library-fav") }, sharedOrigin = "library-fav")
        }
    }
}

@Composable
private fun DownloadList(
    items: List<DownloadItem>?,
    onPlay: (DownloadEntity) -> Unit,
    onDelete: (String) -> Unit,
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
    val stored = items.filter { it.state == DownloadState.DONE }.sumOf { it.totalBytes }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "${items.count { it.state == DownloadState.DONE }} episode tersimpan • ${formatBytes(stored)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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
                    NetImage(e.episodeThumbnail.ifBlank { e.animePoster }, Modifier.fillMaxSize())
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
