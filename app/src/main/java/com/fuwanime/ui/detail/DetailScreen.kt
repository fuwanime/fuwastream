package com.fuwanime.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableIntStateOf
import com.fuwanime.ui.motion.rememberIsEntering
import com.fuwanime.ui.motion.rememberIsExiting
import com.fuwanime.ui.motion.headerStillShown
import com.fuwanime.ui.motion.sharedBackdropKey
import com.fuwanime.ui.motion.staggerIn
import com.fuwanime.ui.motion.sharedPosterKey
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.sharedEpisodeKey
import com.fuwanime.ui.motion.rememberStaggerState
import com.fuwanime.ui.motion.aboveSharedElements
import com.fuwanime.ui.motion.navEnter
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.LocalReduceMotion
import com.fuwanime.data.Anime
import com.fuwanime.data.DownloadState
import com.fuwanime.data.DownloadItem
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import com.fuwanime.data.Episode
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.Pill
import com.fuwanime.ui.components.PosterRow
import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.ui.components.SkeletonBox
import com.fuwanime.ui.components.StatusPill
import com.fuwanime.ui.components.fadeToBackground
import com.fuwanime.util.compactNumber
import com.fuwanime.util.fuwaViewModel

@Composable
fun DetailRoute(
    /** Row the poster was tapped in; keys the shared poster transition. */
    origin: String?,
    /** Unique per back-stack entry, so rows on stacked detail pages never share keys. */
    entryKey: String,
    onBack: () -> Unit,
    /** [sharedKey] names the image that flies into the player; [thumb] is what the player shows meanwhile. */
    onPlay: (anime: Anime, episode: Episode, sharedKey: String?, thumb: String) -> Unit,
    onAnimeClick: OpenAnime,
    onGenre: (String) -> Unit,
) {
    val vm: DetailViewModel = fuwaViewModel(key = "detail") { c, h -> DetailViewModel(c.animeRepository, c.libraryRepository, c.downloadRepository, c.settingsStore, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloadMap.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            vm.messageShown()
            snackbar.showSnackbar(it)
        }
    }
    Box(Modifier.fillMaxSize()) {
        DetailScreen(
            state,
            vm.displayed(state),
            favorite,
            progress,
            downloads,
            vm,
            origin,
            entryKey,
            onBack,
            onPlay,
            onAnimeClick,
            onGenre,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(
    state: DetailUiState,
    episodes: List<Episode>,
    favorite: Boolean,
    progress: Map<String, WatchHistoryEntity>,
    downloads: Map<String, DownloadItem>,
    vm: DetailViewModel,
    origin: String?,
    entryKey: String,
    onBack: () -> Unit,
    onPlay: (Anime, Episode, String?, String) -> Unit,
    onAnimeClick: OpenAnime,
    onGenre: (String) -> Unit,
) {
    val anime = state.anime
    val listState = rememberLazyListState()
    val stagger = rememberStaggerState()
    var favoritePop by remember { mutableIntStateOf(0) }
    // Outlives the lazy "actions" item, so scrolling it back never replays the pop.
    var favoritePopShown by rememberSaveable { mutableIntStateOf(0) }
    val resume = remember(progress) { progress.values.maxByOrNull { it.updatedAt } }

    Box(Modifier.fillMaxSize()) {
        // Scrolling while the poster flies used to make it chase the header. Instead of locking the list, the link
        // is dropped the moment the user scrolls mid-transition: the poster settles into the header and scrolls with it.
        val entering = rememberIsEntering()
        val headerShown by remember {
            derivedStateOf {
                headerStillShown(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, HEADER_LINK_MAX_OFFSET_PX)
            }
        }
        // The poster only flies in. Flying back on close crossed over the other posters on its way down to its card.
        val exiting = rememberIsExiting()
        val linkHeader = headerShown && !(entering && listState.isScrollInProgress)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Once the header is scrolled away, closing just fades instead of flying a poster in from off-screen.
            item(key = "header") { Header(anime, origin.takeIf { linkHeader && !exiting }, entryKey, linkBackdrop = linkHeader) }

            if (anime == null && state.infoError != null) {
                item { ErrorState(state.infoError, vm::loadInfo) }
                return@LazyColumn
            }

            item(key = "actions") {
                Row(
                    Modifier.navEnter().fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val target: Episode? = resume?.let { Episode(it.episodeId, it.episodeNumber, it.episodeTitle, it.episodeThumbnail) }
                        ?: state.firstEpisode
                    Button(
                        // The header backdrop flies into the video area.
                        onClick = {
                            if (anime != null && target != null) {
                                onPlay(anime, target, sharedBackdropKey(anime.id, entryKey), anime.backdrop)
                            }
                        },
                        enabled = anime != null && target != null,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                resume != null -> "Lanjutkan Ep ${resume.episodeNumber}"
                                target != null -> "Tonton Ep ${target.number}"
                                else -> "Memuat episode…"
                            },
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconToggleButton(
                        checked = favorite,
                        onCheckedChange = { adding ->
                            if (adding) favoritePop++
                            vm.toggleFavorite()
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        FavoriteIcon(favorite, favoritePop, favoritePopShown) { favoritePopShown = it }
                    }
                }
            }

            if (anime != null) {
                if (resume != null) {
                    item(key = "resumeBar") {
                        Column(Modifier.navEnter(140).padding(horizontal = 20.dp)) {
                            LinearProgressIndicator(
                                progress = { resume.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                if (anime.genres.isNotEmpty()) {
                    item(key = "genres") {
                        FlowRow(Modifier.navEnter(160).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            anime.genres.forEach { g -> AssistChip(onClick = { onGenre(g) }, label = { Text(g) }) }
                        }
                    }
                }
                item(key = "synopsis") { Box(Modifier.navEnter(180)) { Synopsis(anime.synopsis) } }
                item(key = "info") { Box(Modifier.navEnter(200)) { InfoGrid(anime) } }
                if (state.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionHeader("Season & Terkait")
                            Spacer(Modifier.height(12.dp))
                            val labels = state.seasons.associate { it.anime.id to it.label }
                            PosterRow(
                                state.seasons.map { it.anime },
                                loading = false,
                                onClick = { onAnimeClick(it, "season-$entryKey") },
                                caption = { a -> labels[a.id]?.takeIf { it.isNotBlank() } },
                                origin = "season-$entryKey",
                            )
                        }
                    }
                }
                if (state.similar.isNotEmpty()) {
                    item(key = "similar") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionHeader("Anime Serupa")
                            Spacer(Modifier.height(12.dp))
                            PosterRow(
                                state.similar,
                                loading = false,
                                onClick = { onAnimeClick(it, "similar-$entryKey") },
                                origin = "similar-$entryKey",
                            )
                        }
                    }
                }
            }

            item(key = "epHeader") {
                Box(Modifier.navEnter(220)) { EpisodeHeader(state, vm) }
            }

            if (state.ranges.size > 1 && state.episodeQuery.isBlank()) {
                item(key = "ranges") {
                    val rangeState = rememberLazyListState()
                    LaunchedEffect(state.selectedPage, state.ranges.size) {
                        val idx = state.ranges.indexOfFirst { it.page == state.selectedPage }
                        if (idx >= 0) rangeState.animateScrollToItem((idx - 1).coerceAtLeast(0))
                    }
                    LazyRow(
                        state = rangeState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.ranges, key = { it.page }) { r ->
                            FilterChip(
                                selected = r.page == state.selectedPage,
                                onClick = { vm.selectPage(r.page) },
                                label = { Text(r.label) },
                            )
                        }
                    }
                }
            }

            when {
                state.loadingEpisodes -> items(4) { EpisodeSkeleton() }
                state.episodesError != null -> item {
                    ErrorState(state.episodesError, { state.selectedPage?.let(vm::selectPage) ?: vm.loadEpisodes() })
                }
                episodes.isEmpty() -> item {
                    Text(
                        if (state.episodeQuery.isBlank()) "Belum ada episode." else "Episode ${state.episodeQuery} tidak ditemukan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                else -> itemsIndexed(episodes, key = { _, e -> e.id }) { index, ep ->
                    Box(Modifier.animateItem().staggerIn(stagger, ep.id, index)) {
                    EpisodeRow(
                        ep,
                        progress[ep.id],
                        isResume = resume?.episodeId == ep.id,
                        download = downloads[ep.id],
                        onDownload = { vm.download(ep) },
                        onDeleteDownload = { vm.deleteDownload(ep) },
                        onClick = { anime?.let { onPlay(it, ep, sharedEpisodeKey(ep.id, "detail"), ep.thumbnail) } },
                    )
                    }
                }
            }
        }

        val solid by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 520 }
        }
        DetailTopBar(solid, anime?.title.orEmpty(), onBack)
    }
}

/** Header scroll offset beyond which the poster no longer flies back on close. */
private const val HEADER_LINK_MAX_OFFSET_PX = 600

@Composable
private fun Header(anime: Anime?, origin: String?, entryKey: String, linkBackdrop: Boolean) {
    val bg = MaterialTheme.colorScheme.background
    Box {
        if (anime == null) {
            SkeletonBox(Modifier.fillMaxWidth().height(280.dp), RoundedCornerShape(0.dp))
        } else {
            NetImage(
                anime.backdrop,
                Modifier
                    .navEnter(0, rise = false)
                    .fillMaxWidth()
                    .height(280.dp)
                    .sharedKey(sharedBackdropKey(anime.id, entryKey).takeIf { linkBackdrop })
                    .fadeToBackground(bg),
            )
        }
        Row(
            Modifier.padding(top = 190.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (anime == null) {
                SkeletonBox(Modifier.width(116.dp).aspectRatio(2f / 3f))
            } else {
                NetImage(
                    anime.poster,
                    Modifier
                        .width(116.dp)
                        .aspectRatio(2f / 3f)
                        .sharedKey(origin?.let { sharedPosterKey(anime.id, it) })
                        .shadow(16.dp, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium),
                    anime.title,
                )
            }
            Spacer(Modifier.width(16.dp))
            // Above the flying poster: it slides in behind the pills and title.
            Column(Modifier.weight(1f).offset(y = (-4).dp).aboveSharedElements().navEnter()) {
                if (anime == null) {
                    SkeletonBox(Modifier.fillMaxWidth().height(24.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.6f).height(14.dp))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(anime.status)
                        if (anime.type.isNotBlank()) Pill(anime.type)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(anime.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (anime.alternativeTitles.isNotBlank()) {
                        Text(
                            anime.alternativeTitles,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(compactNumber(anime.views), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Favorite, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text(compactNumber(anime.favorites), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Text("Sinopsis", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (expanded) "Tutup" else "Selengkapnya",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoGrid(anime: Anime) {
    val rows = listOfNotNull(
        anime.studio.takeIf { it.isNotBlank() }?.let { "Studio" to it },
        anime.airedStart.takeIf { it.isNotBlank() }?.let { "Mulai tayang" to it },
        anime.displayYear.takeIf { it.isNotBlank() }?.let { "Tahun" to it },
        anime.type.takeIf { it.isNotBlank() }?.let { "Tipe" to it },
    )
    if (rows.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (k, v) ->
                Row {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                    Text(v, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EpisodeHeader(state: DetailUiState, vm: DetailViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmDownloadAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Episode", style = MaterialTheme.typography.titleLarge)
                state.latestEpisode?.let {
                    Text(
                        "Terbaru: Episode ${if (it % 1.0 == 0.0) it.toLong() else it}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirmDownloadAll = true }) {
                Icon(Icons.Rounded.Download, "Unduh massal episode")
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) vm.searchEpisode("")
            }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Cari episode") }
            IconButton(onClick = vm::toggleOrder) {
                Icon(Icons.Rounded.SwapVert, if (state.newestFirst) "Urutkan terlama" else "Urutkan terbaru")
            }
        }
        AnimatedVisibility(searching) {
            OutlinedTextField(
                value = state.episodeQuery,
                onValueChange = vm::searchEpisode,
                placeholder = { Text("Nomor episode, mis. 1001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDownloadAll) {
        AlertDialog(
            onDismissRequest = { confirmDownloadAll = false },
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text("Unduh massal episode?") },
            text = {
                Text(
                    "Mengantre hingga 30 episode terbaru yang belum tersimpan, memakai kualitas " +
                        "download dan opsi Wi-Fi dari Pengaturan.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDownloadAll = false; vm.downloadAllEpisodes() }) { Text("Unduh") }
            },
            dismissButton = { TextButton(onClick = { confirmDownloadAll = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    progress: WatchHistoryEntity?,
    isResume: Boolean,
    download: DownloadItem?,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isResume) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {
            NetImage(ep.thumbnail, Modifier.fillMaxSize().sharedKey(sharedEpisodeKey(ep.id, "detail")).clip(MaterialTheme.shapes.small))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
            Icon(
                Icons.Rounded.PlayArrow,
                null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(4.dp),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { if (progress.isFinished) 1f else progress.progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    trackColor = Color.White.copy(alpha = 0.3f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Episode ${ep.number}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (ep.isNew) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "BARU",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (ep.title.isNotBlank() && ep.title != "Episode ${ep.number}") {
                Text(ep.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOf(ep.releaseDate, if (ep.views > 0) "${compactNumber(ep.views)} x" else "").filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress?.isFinished == true) {
            Icon(Icons.Rounded.CheckCircle, "Sudah ditonton", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        }
        EpisodeDownloadButton(download, onDownload, onDeleteDownload)
    }
}

/** Heart that pops when the anime is added to favourites; [pop] increments on each add. */
@Composable
private fun FavoriteIcon(favorite: Boolean, pop: Int, shown: Int, onShown: (Int) -> Unit) {
    val scale = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(pop) {
        if (pop > shown) {
            onShown(pop)
            if (reduce) return@LaunchedEffect
            scale.animateTo(1.3f, tween(120, easing = Motion.EmphasizedDecelerate))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    Crossfade(favorite, animationSpec = tween(Motion.Short), label = "favorite") { fav ->
        Icon(
            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            "Favorit",
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

@Composable
private fun EpisodeSkeleton() {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(Modifier.width(132.dp).aspectRatio(16f / 9f), MaterialTheme.shapes.small)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(Modifier.width(100.dp).height(14.dp))
            SkeletonBox(Modifier.width(70.dp).height(10.dp))
        }
    }
}

@Composable
private fun DetailTopBar(solid: Boolean, title: String, onBack: () -> Unit) {
    val bg by animateColorAsState(if (solid) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent, label = "detailBar")
    Row(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (solid) Color.Transparent else Color.Black.copy(alpha = 0.4f),
                contentColor = if (solid) MaterialTheme.colorScheme.onSurface else Color.White,
            ),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        AnimatedVisibility(solid, enter = fadeIn(), exit = fadeOut()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun EpisodeDownloadButton(download: DownloadItem?, onDownload: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        when {
            download?.state == DownloadState.DONE ->
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.DownloadDone, "Tersimpan offline", tint = MaterialTheme.colorScheme.tertiary)
                }
            download?.inProgress == true -> {
                if (download.state == DownloadState.RUNNING && download.totalBytes > 0) {
                    CircularProgressIndicator(progress = { download.progress }, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
                IconButton(onClick = { confirm = true }) {} // tap to cancel
            }
            else -> IconButton(onClick = onDownload) {
                Icon(
                    if (download?.state == DownloadState.FAILED) Icons.Rounded.ErrorOutline else Icons.Rounded.Download,
                    if (download?.state == DownloadState.FAILED) "Download gagal, coba lagi" else "Download",
                    tint = if (download?.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        val running = download?.inProgress == true
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (running) "Batalkan download?" else "Hapus download?") },
            text = { Text(if (running) "Download episode ini akan dihentikan." else "File episode ini akan dihapus dari perangkat.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(if (running) "Batalkan" else "Hapus") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Tutup") } },
        )
    }
}
