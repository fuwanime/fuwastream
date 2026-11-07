package com.fuwanime.ui.home

import com.fuwanime.R
import androidx.compose.ui.res.stringResource
import com.fuwanime.ui.settings.rememberRestoreFlow
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Restore
import android.widget.Toast
import com.fuwanime.ui.components.IconWithTooltip
import androidx.compose.foundation.layout.heightIn
import com.fuwanime.ui.components.LocalNavBarPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fuwanime.ui.theme.PlusJakarta
import com.fuwanime.ui.components.ImageScrim
import com.fuwanime.ui.components.TagChip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import com.fuwanime.ui.components.RefreshHairline
import com.fuwanime.ui.components.PosterRow
import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.ui.components.SkeletonBox
import com.fuwanime.util.airTime
import com.fuwanime.util.compactNumber
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
        HomeViewModel(c.animeRepository, c.libraryRepository, c.updateRepository, c.settingsStore, c.networkStatus, c.backupRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val showRestore by vm.showRestorePrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restore = rememberRestoreFlow(
        vm.backup,
        onRestored = { vm.dismissRestorePrompt() },
        onMessage = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
    )
    HomeScreen(
        state = state,
        favoriteIds = favoriteIds,
        onToggleFavorite = vm::toggleFavorite,
        todayLabel = stringResource(vm.today.full),
        onRefresh = vm::refresh,
        update = update,
        onDismissUpdate = vm::dismissUpdate,
        showRestore = showRestore,
        onRestore = restore,
        onDismissRestore = { vm.dismissRestorePrompt() },
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
    favoriteIds: Set<String>,
    onToggleFavorite: (Anime) -> Unit,
    todayLabel: String,
    onRefresh: () -> Unit,
    update: UpdateRepository.UpdateInfo?,
    onDismissUpdate: () -> Unit,
    showRestore: Boolean,
    onRestore: () -> Unit,
    onDismissRestore: () -> Unit,
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
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp + LocalNavBarPadding.current), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                val heroLoading = feed.loading && state.hero.isEmpty()
                Crossfade(heroLoading, animationSpec = tween(Motion.Medium), label = "hero") { loading ->
                    if (loading) {
                        SkeletonBox(Modifier.fillMaxWidth().height(heroHeightDp(LocalDensity.current.fontScale).dp), shape = MaterialTheme.shapes.extraSmall)
                    } else if (state.hero.isNotEmpty()) {
                        HeroPager(state.hero, favoriteIds, onToggleFavorite) { onAnimeClick(it, "home-hero") }
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

            if (showRestore) {
                item(key = "restoreBanner") { RestoreBanner(onRestore, onDismissRestore) }
            }

            if (feed.error != null && feed.popular.isEmpty()) {
                item { ErrorState(feed.error, onRefresh) }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "cw") {
                    Section(stringResource(R.string.browse_home_continue)) {
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
                    Section(stringResource(R.string.browse_home_latest)) {
                        val releases = remember(feed.latest) { feed.latest.associateBy { it.anime.id } }
                        val res = LocalContext.current.resources
                        PosterRow(feed.latest.map { it.anime }, feed.loading, { onAnimeClick(it, "home-latest") }, origin = "home-latest", isNew = { true }, caption = { a ->
                            releases[a.id]?.let { r ->
                                listOf(r.episodeLabel.trim(), relativeTime(res, r.updatedAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { null }
                            }
                        })
                    }
                }
            }

            if (feed.loading || feed.today.isNotEmpty()) {
                item(key = "today") {
                    Section(stringResource(R.string.browse_home_today), subtitle = todayLabel, onSeeAll = onSchedule) {
                        val releaseFmt = stringResource(R.string.browse_release_at, "%1\$s")
                        PosterRow(feed.today, feed.loading, { onAnimeClick(it, "home-today") }, origin = "home-today", caption = { a ->
                            a.nextAirTime.takeIf { it.isNotBlank() }?.let { releaseFmt.format(airTime(it)) }
                        })
                    }
                }
            }

            if (feed.loading || feed.thisSeason.isNotEmpty()) {
                item(key = "season") {
                    Section(stringResource(R.string.browse_home_airing), subtitle = stringResource(R.string.browse_home_airing_subtitle)) {
                        val seasonRes = LocalContext.current.resources
                        val viewsFmt = stringResource(R.string.browse_views, "%1\$s")
                        PosterRow(
                            feed.thisSeason, feed.loading, { onAnimeClick(it, "home-season") },
                            origin = "home-season", caption = { viewsFmt.format(compactNumber(seasonRes, it.views)) },
                        )
                    }
                }
            }

            item(key = "popular") {
                Section(stringResource(R.string.common_sort_popular), subtitle = stringResource(R.string.browse_home_popular_subtitle), onSeeAll = { onExplore(null) }) {
                    val top = feed.popular.take(10)
                    if (top.isEmpty()) {
                        PosterRow(top, feed.loading, { onAnimeClick(it, "home-popular") })
                    } else {
                        RankedRow(top, { onAnimeClick(it, "home-popular") }, origin = "home-popular")
                    }
                }
            }

            item(key = "genres") {
                Section(stringResource(R.string.browse_home_genres)) {
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickGenres.forEach { g -> TagChip(g, onClick = { onExplore(g) }) }
                    }
                }
            }
        }

        HomeTopBar(
            solid = remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }.value,
            syncing = feed.syncing,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.SystemUpdateAlt,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.browse_update_banner, info.tag.removePrefix("v")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = onClick),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.browse_update_dismiss_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RestoreBanner(onRestore: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.browse_restore_prompt),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            TextButton(onClick = onRestore) { Text(stringResource(R.string.browse_restore)) }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.browse_restore_dismiss_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
                info.notes.ifBlank { stringResource(R.string.browse_update_no_notes) },
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
                    Text(stringResource(R.string.browse_update_download))
                }
                OutlinedIconButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.pageUrl)),
                        )
                    },
                ) {
                    Icon(Icons.Rounded.OpenInNew, stringResource(R.string.browse_update_github_cd))
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
private fun HomeTopBar(solid: Boolean, syncing: Boolean, onSearch: () -> Unit, onSettings: () -> Unit) {
    val bg by animateColorAsState(
        if (solid) MaterialTheme.colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
        label = "topbarBg",
    )
    val scrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (solid) 0f else 0.55f), Color.Transparent))
    Column {
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
            IconWithTooltip(stringResource(R.string.browse_search), Icons.Rounded.Search, onSearch, tint = tint)
            IconWithTooltip(stringResource(R.string.browse_settings), Icons.Rounded.Settings, onSettings, tint = tint)
        }
        RefreshHairline(syncing)
    }
}

@Composable
fun Wordmark(onDark: Boolean = false) {
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, letterSpacing = (-0.03).em)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("fuwa", style = style, color = MaterialTheme.colorScheme.primary)
        Text("stream", style = style, color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}


/** The API's images are small: decode one tiny and blur it, so the stretched backdrop never shows its pixels. */
@Composable
private fun AmbientBackdrop(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        // Own cache key, so this tiny bitmap never stands in for the sharp poster. Below Android 12 blur is a no-op; the upscale still softens it.
        model = remember(url) { ImageRequest.Builder(context).data(url).size(64).memoryCacheKey("$url#ambient").crossfade(250).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).blur(36.dp, BlurredEdgeTreatment.Rectangle),
    )
}

@Composable
private fun HeroPager(items: List<Anime>, favoriteIds: Set<String>, onToggleFavorite: (Anime) -> Unit, onOpen: (Anime) -> Unit) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (items.size > 1) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val background = MaterialTheme.colorScheme.background
    Box {
        // Grows with large font scales so the title and the Tonton button are never squeezed out.
        HorizontalPager(pager, Modifier.fillMaxWidth().height(heroHeightDp(LocalDensity.current.fontScale).dp)) { page ->
            val anime = items[page]
            Box(Modifier.fillMaxSize()) {
                val press = remember { MutableInteractionSource() }
                AmbientBackdrop(anime.poster, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to ImageScrim.copy(alpha = 0.6f),
                            0.3f to ImageScrim.copy(alpha = 0.3f),
                            0.62f to background.copy(alpha = 0.8f),
                            1f to background,
                        ),
                    ),
                )
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, start = 20.dp, end = 20.dp, bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NetImage(
                        anime.poster,
                        Modifier
                            .width(128.dp)
                            .aspectRatio(2f / 3f)
                            .pressScale(press)
                            .sharedKey(sharedPosterKey(anime.id, "home-hero"))
                            .shadow(24.dp, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(interactionSource = press, indication = null) { onOpen(anime) },
                        anime.title,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.browse_hero_rank, page + 1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.06.em,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        val kind = listOf(anime.type, anime.displayYear).filter { it.isNotBlank() }.joinToString(" · ")
                        if (kind.isNotBlank()) {
                            Text(
                                kind,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        anime.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(stringResource(R.string.browse_views, compactNumber(LocalContext.current.resources, anime.views))).plus(anime.genres.take(2)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onOpen(anime) },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.browse_watch), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        val favorite = anime.id in favoriteIds
                        OutlinedIconButton(
                            onClick = { onToggleFavorite(anime) },
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
                        ) {
                            Icon(
                                if (favorite) Icons.Rounded.Check else Icons.Rounded.Add,
                                if (favorite) stringResource(R.string.browse_remove_from_library) else stringResource(R.string.browse_add_to_library),
                            )
                        }
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

/** Top-10 row: big outlined rank numbers peeking out from behind each poster. */
@Composable
private fun RankedRow(items: List<Anime>, onClick: (Anime) -> Unit, origin: String) {
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    LazyRow(
        contentPadding = PaddingValues(start = 8.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items, key = { _, a -> a.id }) { index, anime ->
            Box(Modifier.width(158.dp).height(160.dp)) {
                Text(
                    "${index + 1}",
                    style = TextStyle(
                        fontFamily = PlusJakarta,
                        fontSize = 120.sp,
                        lineHeight = 120.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.06).em,
                        drawStyle = Stroke(width = strokePx),
                    ),
                    color = RankStroke,
                    modifier = Modifier.align(Alignment.BottomStart).offset(y = 14.dp),
                )
                val press = remember { MutableInteractionSource() }
                NetImage(
                    anime.poster,
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(106.dp)
                        .height(152.dp)
                        .pressScale(press)
                        .sharedKey(sharedPosterKey(anime.id, origin))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = press, indication = LocalIndication.current) { onClick(anime) },
                    anime.title,
                )
            }
        }
    }
}

private val RankStroke = Color(0xFF4A4455)
