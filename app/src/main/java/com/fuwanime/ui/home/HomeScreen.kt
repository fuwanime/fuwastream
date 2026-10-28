package com.fuwanime.ui.home

import android.os.Build
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.sharedPosterKey
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.pressScale
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.data.Anime
import com.fuwanime.data.UpdateRepository
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.components.ContinueWatchingCard
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.Pill
import com.fuwanime.ui.components.PosterRow
import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.ui.components.SkeletonBox
import com.fuwanime.ui.components.StatusPill
import com.fuwanime.ui.components.fadeToBackground
import com.fuwanime.util.airTime
import com.fuwanime.util.compactNumber
import com.fuwanime.util.episodeShortLabel
import com.fuwanime.util.relativeTime
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.delay

val QuickGenres = listOf(
    "Action", "Fantasy", "Romance", "Comedy", "Isekai", "Slice of Life",
    "School", "Drama", "Supernatural", "Sci-Fi", "Mystery", "Sports",
)

@Composable
fun HomeRoute(
    onAnimeClick: OpenAnime,
    onContinue: (WatchHistoryEntity) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSchedule: () -> Unit,
    onExplore: (genre: String?) -> Unit,
) {
    val vm: HomeViewModel = fuwaViewModel { c, _ ->
        HomeViewModel(c.animeRepository, c.libraryRepository, c.updateRepository, c.settingsStore)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        todayLabel = vm.today.label,
        onRefresh = vm::refresh,
        update = update,
        onDismissUpdate = vm::dismissUpdate,
        onAnimeClick = onAnimeClick,
        onContinue = onContinue,
        onSearch = onSearch,
        onSettings = onSettings,
        onSchedule = onSchedule,
        onExplore = onExplore,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    state: HomeUiState,
    todayLabel: String,
    onRefresh: () -> Unit,
    update: UpdateRepository.UpdateInfo?,
    onDismissUpdate: () -> Unit,
    onAnimeClick: OpenAnime,
    onContinue: (WatchHistoryEntity) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSchedule: () -> Unit,
    onExplore: (String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val feed = state.feed
    var showUpdateSheet by remember { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = feed.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                val heroLoading = feed.loading && state.hero.isEmpty()
                Crossfade(heroLoading, animationSpec = tween(Motion.Medium), label = "hero") { loading ->
                    if (loading) {
                        SkeletonBox(Modifier.fillMaxWidth().height(600.dp), shape = MaterialTheme.shapes.extraSmall)
                    } else if (state.hero.isNotEmpty()) {
                        HeroPager(state.hero) { onAnimeClick(it, "home-hero") }
                    } else {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).height(72.dp))
                    }
                }
            }

            // Below the hero, not under the status bar where the edge-to-edge hero starts.
            if (update != null) {
                item(key = "updateBanner") {
                    UpdateBanner(update, onClick = { showUpdateSheet = true }, onDismiss = onDismissUpdate)
                }
            }

            if (feed.error != null && feed.popular.isEmpty()) {
                item { ErrorState(feed.error, onRefresh) }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "cw") {
                    Section("Lanjutkan Nonton") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.continueWatching, key = { it.episodeId }) { entry ->
                                ContinueWatchingCard(entry, onClick = { onContinue(entry) }, sharedOrigin = "home-cw")
                            }
                        }
                    }
                }
            }

            if (feed.loading || feed.latest.isNotEmpty()) {
                item(key = "latest") {
                    Section("Episode Terbaru", subtitle = "Baru saja dirilis") {
                        val releases = remember(feed.latest) { feed.latest.associateBy { it.anime.id } }
                        PosterRow(feed.latest.map { it.anime }, feed.loading, { onAnimeClick(it, "home-latest") }, origin = "home-latest", caption = { a ->
                            releases[a.id]?.let { r ->
                                listOf(episodeShortLabel(r.episodeLabel), relativeTime(r.updatedAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { null }
                            }
                        })
                    }
                }
            }

            if (feed.loading || feed.today.isNotEmpty()) {
                item(key = "today") {
                    Section("Tayang Hari Ini", subtitle = todayLabel, onSeeAll = onSchedule) {
                        PosterRow(feed.today, feed.loading, { onAnimeClick(it, "home-today") }, origin = "home-today", caption = { a ->
                            a.nextAirTime.takeIf { it.isNotBlank() }?.let { "Rilis ${airTime(it)}" }
                        })
                    }
                }
            }

            if (feed.loading || feed.thisSeason.isNotEmpty()) {
                item(key = "season") {
                    Section("Sedang Tayang", subtitle = "Anime ongoing musim ini") {
                        PosterRow(
                            feed.thisSeason, feed.loading, { onAnimeClick(it, "home-season") },
                            origin = "home-season", caption = { "${compactNumber(it.views)} ditonton" },
                        )
                    }
                }
            }

            item(key = "popular") {
                Section("Terpopuler", subtitle = "Paling banyak ditonton", onSeeAll = { onExplore(null) }) {
                    PosterRow(
                        feed.popular.take(20), feed.loading, { onAnimeClick(it, "home-popular") },
                        origin = "home-popular", caption = { "${compactNumber(it.views)} ditonton" },
                    )
                }
            }

            item(key = "genres") {
                Section("Jelajahi Genre") {
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        QuickGenres.forEach { g -> SuggestionChip(onClick = { onExplore(g) }, label = { Text(g) }) }
                    }
                }
            }
        }

        HomeTopBar(
            solid = remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }.value,
            onSearch = onSearch,
            onSettings = onSettings,
        )
    }

    if (showUpdateSheet && update != null) {
        UpdateSheet(update, onDismiss = { showUpdateSheet = false })
    }
}

@Composable
private fun UpdateBanner(info: UpdateRepository.UpdateInfo, onClick: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.SystemUpdateAlt,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Update ${info.tag.removePrefix("v")} tersedia — ketuk untuk lihat perubahan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = onClick),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    "Tutup banner update",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(info: UpdateRepository.UpdateInfo, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdateAlt, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                info.tag.removePrefix("v"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                info.notes.ifBlank { "Tanpa catatan rilis." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                Button(
                    onClick = {
                        info.apkUrl?.let { url ->
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                            )
                        }
                    },
                    enabled = info.apkUrl != null,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Unduh APK")
                }
                OutlinedIconButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.pageUrl)),
                        )
                    },
                ) {
                    Icon(Icons.Rounded.OpenInNew, "Buka di GitHub")
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionHeader(title, subtitle = subtitle, onSeeAll = onSeeAll)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HomeTopBar(solid: Boolean, onSearch: () -> Unit, onSettings: () -> Unit) {
    val bg by animateColorAsState(
        if (solid) MaterialTheme.colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
        label = "topbarBg",
    )
    val scrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (solid) 0f else 0.55f), Color.Transparent))
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .background(scrim)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(onDark = !solid)
        Spacer(Modifier.weight(1f))
        val tint = if (solid) MaterialTheme.colorScheme.onSurface else Color.White
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Cari", tint = tint) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Pengaturan", tint = tint) }
    }
}

@Composable
fun Wordmark(onDark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Fuwa",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Stream",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HeroPager(items: List<Anime>, onOpen: (Anime) -> Unit) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (items.size > 1) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val background = MaterialTheme.colorScheme.background
    // RenderEffect blur is Android 12+; older devices get a heavier scrim instead.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(600.dp)) { page ->
            val anime = items[page]
            Box(Modifier.fillMaxSize()) {
                NetImage(
                    anime.poster,
                    Modifier.fillMaxSize().then(if (canBlur) Modifier.blur(36.dp) else Modifier),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = if (canBlur) 0.35f else 0.7f),
                            0.55f to background.copy(alpha = if (canBlur) 0.55f else 0.8f),
                            1f to background,
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val press = remember { MutableInteractionSource() }
                    NetImage(
                        anime.poster,
                        Modifier
                            .width(176.dp)
                            .aspectRatio(2f / 3f)
                            .pressScale(press)
                            .sharedKey(sharedPosterKey(anime.id, "home-hero"))
                            .shadow(24.dp, MaterialTheme.shapes.large)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(interactionSource = press, indication = LocalIndication.current) { onOpen(anime) },
                        anime.title,
                    )
                    Spacer(Modifier.height(18.dp))
                    Pill(
                        "#${page + 1} Musim Ini",
                        leading = Icons.Rounded.LocalFireDepartment,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        anime.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            anime.type.takeIf { it.isNotBlank() },
                            anime.displayYear.takeIf { it.isNotBlank() },
                            "${compactNumber(anime.views)} ditonton",
                        ).plus(anime.genres.take(2)).joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onOpen(anime) }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Tonton Sekarang")
                        }
                        Spacer(Modifier.width(10.dp))
                        FilledTonalIconButton(onClick = { onOpen(anime) }) { Icon(Icons.Rounded.Info, "Detail") }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(items.size) { i ->
                val selected = pager.currentPage == i
                val w by animateDpAsState(if (selected) 20.dp else 6.dp, label = "dot")
                Box(
                    Modifier
                        .height(6.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                )
            }
        }
    }
}
