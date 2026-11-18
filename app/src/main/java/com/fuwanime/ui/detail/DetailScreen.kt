package com.fuwanime.ui.detail

import android.content.Intent
import androidx.core.net.toUri
import com.fuwanime.ui.components.rememberDownloadGate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.fuwanime.ui.stats.episodeHeaderStacked
import com.fuwanime.ui.components.IconWithTooltip
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fuwanime.R
import com.fuwanime.util.typeLabel
import com.fuwanime.data.manga.titleKey
import com.fuwanime.data.meta.CastRole
import com.fuwanime.data.meta.CastEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import com.fuwanime.util.text
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.fuwanime.util.loadBitmap
import com.fuwanime.ui.components.readableWidth
import com.fuwanime.ui.components.frostedStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeState
import androidx.palette.graphics.Palette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fuwanime.util.statusLabel
import com.fuwanime.util.remainingLabel
import com.fuwanime.ui.components.NewBadge
import com.fuwanime.data.meta.AnimeMeta
import com.fuwanime.ui.components.TagChip
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.fuwanime.ui.components.FuwaSnackbarHost
import com.fuwanime.data.Episode
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.local.CollectionStatus
import com.fuwanime.data.local.ContentPreferenceEntity
import com.fuwanime.data.local.TitleNotifyMode
import com.fuwanime.data.notify.NotificationAccess
import com.fuwanime.data.notify.notificationAccess
import com.fuwanime.data.notify.openNotificationSettings
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.PosterRow
import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.ui.components.SkeletonBox
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
    onOpenManga: (mangaId: String) -> Unit = {},
) {
    val vm: DetailViewModel = fuwaViewModel(key = "detail") { c, h -> DetailViewModel(c.animeRepository, c.libraryRepository, c.downloadRepository, c.settingsStore, c.animeLinkRepository, c.mangaRepository, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val collectionStatus by vm.collectionStatus.collectAsStateWithLifecycle()
    val preference by vm.preference.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloadMap.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = LocalContext.current.resources
    LaunchedEffect(message) {
        message?.let {
            vm.messageShown()
            snackbar.showSnackbar(it(res))
        }
    }
    LaunchedEffect(state.mangaLookup) {
        (state.mangaLookup as? MangaLookup.Found)?.let { vm.mangaOpened(); onOpenManga(it.mangaId) }
    }
    Box(Modifier.fillMaxSize()) {
        DetailScreen(
            state,
            vm.displayed(state),
            favorite,
            collectionStatus,
            preference,
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
        FuwaSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(
    state: DetailUiState,
    episodes: List<Episode>,
    favorite: Boolean,
    collectionStatus: CollectionStatus?,
    preference: ContentPreferenceEntity?,
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
    val gate = rememberDownloadGate()
    val anime = state.anime
    val listState = rememberLazyListState()
    val stagger = rememberStaggerState()
    var collectionDialog by rememberSaveable { mutableStateOf(false) }
    var notificationDialog by rememberSaveable { mutableStateOf(false) }
    val resume = remember(progress) { progress.values.maxByOrNull { it.updatedAt } }
    val hazeState = remember { HazeState() }
    // A dark colour from the poster for the header wash and the play button; null keeps the app's own colours.
    val context = LocalContext.current
    val tint by produceState<Color?>(null, anime?.poster) {
        val poster = anime?.poster.orEmpty()
        value = if (poster.isBlank()) null else loadBitmap(context, poster, 64)?.let { bitmap ->
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
            posterTint(palette.darkVibrantSwatch?.rgb, palette.vibrantSwatch?.rgb, palette.dominantSwatch?.rgb)?.let { Color(it) }
        }
    }
    // Arrives after the first frame: fade it in rather than pop.
    val headerTint by animateColorAsState(tint ?: Color.Transparent, label = "posterTint")

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
            modifier = Modifier.fillMaxHeight().readableWidth().hazeSource(hazeState),
        ) {
            // Once the header is scrolled away, closing just fades instead of flying a poster in from off-screen.
            item(key = "header") {
                Header(anime, origin.takeIf { linkHeader && !exiting }, entryKey, linkBackdrop = linkHeader, episodeCount = state.latestEpisode, tint = headerTint)
            }

            if (anime == null && state.infoError != null) {
                item { ErrorState(state.infoError, vm::loadInfo) }
                return@LazyColumn
            }

            if (anime != null) {
                item(key = "stats") {
                    val res = LocalContext.current.resources
                    val meta = state.meta
                    // Kuramanime has no public counters; AniList popularity/favourites fill the gap.
                    val siteViews = anime.views.takeIf { it > 0 }
                    val views = siteViews ?: meta?.popularity?.toLong() ?: 0
                    val favorites = anime.favorites.takeIf { it > 0 } ?: meta?.favourites?.toLong() ?: 0
                    if (views > 0 || favorites > 0) {
                        val viewsLabel = stringResource(if (siteViews == null) R.string.watch_stat_members else R.string.watch_stat_views)
                        val favoritesLabel = stringResource(R.string.watch_stat_favorites)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(compactNumber(res, views)) }
                                append(" $viewsLabel      ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(compactNumber(res, favorites)) }
                                append(" $favoritesLabel")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.navEnter().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                        )
                    }
                }
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
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = tint?.let { t ->
                            val container = lighten(t.toArgb())
                            ButtonDefaults.buttonColors(containerColor = Color(container), contentColor = Color(onColorFor(container)))
                        } ?: ButtonDefaults.buttonColors(),
                        // min, not fixed: two lines of text at 200% font must not be clipped.
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                when {
                                    resume != null -> stringResource(R.string.watch_continue_ep, resume.episodeNumber)
                                    target != null -> stringResource(R.string.watch_watch_ep, target.number)
                                    else -> stringResource(R.string.watch_loading_episodes)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                            resume?.takeUnless { it.isFinished }?.let { remainingLabel(LocalContext.current.resources, it.positionMs, it.durationMs) }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.75f))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconButton(
                        onClick = { collectionDialog = true },
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            stringResource(R.string.ux_collection_change),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { notificationDialog = true },
                        modifier = Modifier.size(52.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Notifications, stringResource(R.string.ux_notification_title))
                    }
                }
            }

            if (anime != null) {
                if (anime.genres.isNotEmpty()) {
                    item(key = "genres") {
                        FlowRow(
                            Modifier.navEnter(160).padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            anime.genres.forEach { g -> TagChip(g, onClick = { onGenre(g) }) }
                        }
                    }
                }
                state.meta?.let { meta ->
                    item(key = "meta") { MetaRow(meta, Modifier.navEnter(170).padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
                }
                item(key = "synopsis") { Box(Modifier.navEnter(180)) { Synopsis(anime.synopsis) } }
                item(key = "info") { Box(Modifier.navEnter(200)) { InfoGrid(anime, state.meta) } }
                state.meta?.takeIf { it.trailerUrl.isNotBlank() }?.let { meta ->
                    item(key = "trailer") {
                        val context = LocalContext.current
                        TrailerCard(
                            thumbnail = meta.trailerThumbnail,
                            fallbackThumbnail = anime?.let { a -> a.cover.ifBlank { a.poster } }.orEmpty(),
                            onClick = {
                                // Trailers play outside the app (YouTube/Dailymotion app or the browser).
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, meta.trailerUrl.toUri())) }
                            },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                        )
                    }
                }
                if (state.meta?.sourceTitles?.isNotEmpty() == true) {
                    item(key = "read-manga") {
                        ReadMangaButton(state.mangaLookup, vm::openManga, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
                    }
                }
                state.meta?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                    item(key = "cast") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionHeader(stringResource(R.string.watch_cast))
                            Spacer(Modifier.height(12.dp))
                            CastRow(cast)
                        }
                    }
                }
                if (state.meta != null) {
                    item(key = "anilist") { AniListFooter(onUnlink = vm::unlinkMeta) }
                }
                if (state.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionHeader(stringResource(R.string.watch_season_related))
                            Spacer(Modifier.height(12.dp))
                            val labels = state.seasons.associate { it.anime.id to it.label }
                            val viewingNow = stringResource(R.string.watch_now_viewing)
                            PosterRow(
                                state.seasons.map { it.anime },
                                loading = false,
                                // The franchise includes this anime, in airing order; its own card is not a link.
                                onClick = { if (it.id != anime.id) onAnimeClick(it, "season-$entryKey") },
                                caption = { a ->
                                    if (a.id == anime.id) viewingNow else labels[a.id]?.takeIf { it.isNotBlank() }
                                },
                                origin = "season-$entryKey",
                                highlightId = anime.id,
                            )
                        }
                    }
                }
                if (state.similar.isNotEmpty()) {
                    item(key = "similar") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionHeader(stringResource(R.string.watch_similar))
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
                            TagChip(r.label.text(), onClick = { vm.selectPage(r.page) }, selected = r.page == state.selectedPage)
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
                        if (state.episodeQuery.isBlank()) stringResource(R.string.watch_no_episodes) else stringResource(R.string.watch_episode_not_found, state.episodeQuery),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                else -> itemsIndexed(episodes, key = { _, e -> e.id }) { index, ep ->
                    // Prefer the source's own thumbnail, then AniList's streaming still for this episode number,
                    // then the anime's artwork so a row never shows an empty box.
                    val shown = ep.copy(
                        thumbnail = ep.thumbnail
                            .ifBlank { state.meta?.episodeThumbnails?.get(ep.number).orEmpty() }
                            .ifBlank { anime?.let { a -> a.cover.ifBlank { a.poster } }.orEmpty() },
                    )
                    Box(Modifier.animateItem().staggerIn(stagger, ep.id, index)) {
                    EpisodeRow(
                        shown,
                        progress[ep.id],
                        isResume = resume?.episodeId == ep.id,
                        download = downloads[ep.id],
                        onDownload = { gate { vm.download(shown) } },
                        onDeleteDownload = { vm.deleteDownload(ep) },
                        onMark = { vm.markEpisode(ep, it) },
                        onRestart = { vm.restartEpisode(ep) },
                        onMarkBefore = { vm.markBefore(ep) },
                        onClick = { anime?.let { onPlay(it, shown, sharedEpisodeKey(ep.id, "detail"), shown.thumbnail) } },
                    )
                    }
                }
            }
        }

        val solid by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 520 }
        }
        DetailTopBar(solid, anime?.title.orEmpty(), onBack, hazeState)
    }

    if (collectionDialog) {
        CollectionStatusDialog(
            selected = collectionStatus,
            hiddenFromContinue = preference?.hiddenFromContinue == true,
            onSelect = { vm.setCollectionStatus(it); collectionDialog = false },
            onHidden = vm::setHiddenFromContinue,
            onDismiss = { collectionDialog = false },
        )
    }
    if (notificationDialog) {
        AnimeNotificationDialog(
            selected = preference?.notifyMode?.let { raw -> TitleNotifyMode.entries.firstOrNull { it.name == raw } } ?: TitleNotifyMode.DEFAULT,
            autoDownload = preference?.autoDownload == true,
            onMode = vm::setNotificationMode,
            onAutoDownload = vm::setAutoDownload,
            onDismiss = { notificationDialog = false },
        )
    }
}

/** Header scroll offset beyond which the poster no longer flies back on close. */
private const val HEADER_LINK_MAX_OFFSET_PX = 600

@Composable
private fun Header(anime: Anime?, origin: String?, entryKey: String, linkBackdrop: Boolean, episodeCount: Double?, tint: Color) {
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
                stringResource(R.string.watch_backdrop_cd, anime.title),
            )
        }
        // The poster's colour washes over the lower backdrop, behind the poster and title.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.6f to tint.copy(alpha = tint.alpha * washAlpha(MaterialTheme.colorScheme.background.luminance().toDouble())), 1f to Color.Transparent),
            ),
        )
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
                        .shadow(24.dp, MaterialTheme.shapes.medium)
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
                    Text(
                        anime.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val altTitle = anime.alternativeTitles.split(',', ';').map { it.trim() }
                        .firstOrNull { it.isNotBlank() && titleKey(it) != titleKey(anime.title) }.orEmpty()
                    if (altTitle.isNotBlank()) {
                        Text(
                            altTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val countText = stringResource(R.string.watch_episode_count, episodeCount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
                    val count = if (episodeCount != null) countText else ""
                    Text(
                        listOf(anime.displayYear, anime.type.takeIf { it.isNotBlank() }?.let { typeLabel(LocalContext.current.resources, it) }.orEmpty(), statusLabel(LocalContext.current.resources, anime.status), count).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Score, next-episode countdown and AniList tags; nothing here is interactive. */
@Composable
private fun MetaRow(meta: AnimeMeta, modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val chips = listOfNotNull(
        scoreLabel(meta.score),
        meta.nextAiringAt?.takeIf { it > now }?.let { at -> meta.nextEpisode?.let { airingLabel(LocalContext.current.resources, it, at, now) } },
    ) + meta.tags
    if (chips.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { text ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun AniListFooter(onUnlink: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.watch_anilist_info), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.watch_not_this_anime), style = MaterialTheme.typography.labelSmall) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.watch_hide_anilist_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onUnlink()
                }) { Text(stringResource(R.string.watch_hide)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.watch_cancel)) } },
        )
    }
}

@Composable
private fun Synopsis(text: String) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(
                stringResource(if (expanded) R.string.watch_show_less else R.string.watch_read_more),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoGrid(anime: Anime, meta: AnimeMeta?) {
    // Kuramanime omits these fields; fall back to AniList when it has them.
    val studio = anime.studio.ifBlank { meta?.studio.orEmpty() }
    val year = anime.displayYear.ifBlank { meta?.year?.toString().orEmpty() }
    val totalEpisodes = meta?.totalEpisodes?.takeIf { it > 0 }
    val duration = meta?.durationMinutes?.takeIf { it > 0 }
    val rows = listOfNotNull(
        studio.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_info_studio) to it },
        anime.airedStart.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_info_aired) to it },
        year.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_info_year) to it },
        anime.type.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_info_type) to typeLabel(LocalContext.current.resources, it) },
        totalEpisodes?.let {
            stringResource(R.string.watch_info_total_episodes) to stringResource(R.string.watch_episode_count, it.toString())
        },
        duration?.let {
            stringResource(R.string.watch_info_duration) to stringResource(R.string.stats_minutes, it)
        },
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
    val gate = rememberDownloadGate()
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmDownloadAll by rememberSaveable { mutableStateOf(false) }
    var rangeFrom by rememberSaveable { mutableStateOf("") }
    var rangeTo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(top = 24.dp)) {
        val episodesTitle = stringResource(R.string.watch_episodes_title)
        val title: @Composable (Modifier) -> Unit = { m ->
            Text(
                buildAnnotatedString {
                    append(episodesTitle)
                    state.latestEpisode?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)) {
                            append(" · ${if (it % 1.0 == 0.0) it.toLong() else it}")
                        }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = m,
            )
        }
        val actions: @Composable () -> Unit = {
            TextButton(onClick = {
                rangeFrom = "1"
                rangeTo = state.latestEpisode?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
                confirmDownloadAll = true
            }) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.watch_download_all))
            }
            IconWithTooltip(stringResource(if (searching) R.string.watch_search_close else R.string.watch_search_open), if (searching) Icons.Rounded.Close else Icons.Rounded.Search, {
                searching = !searching
                if (!searching) vm.searchEpisode("")
            })
            val orderDescription = stringResource(if (state.newestFirst) R.string.watch_order_newest_cd else R.string.watch_order_oldest_cd)
            TextButton(onClick = vm::toggleOrder, modifier = Modifier.semantics { contentDescription = orderDescription }) {
                Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (state.newestFirst) R.string.watch_order_newest else R.string.watch_order_oldest))
            }
        }
        // Large text can't fit the title and three labelled actions on one line; give the actions their own row.
        if (!episodeHeaderStacked(LocalDensity.current.fontScale, LocalConfiguration.current.screenWidthDp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                actions()
            }
        } else {
            title(Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        AnimatedVisibility(searching) {
            OutlinedTextField(
                value = state.episodeQuery,
                onValueChange = vm::searchEpisode,
                placeholder = { Text(stringResource(R.string.watch_search_hint)) },
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
            title = { Text(stringResource(R.string.watch_bulk_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pluralStringResource(R.plurals.watch_bulk_message, 30, 30))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = rangeFrom,
                            onValueChange = { rangeFrom = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.watch_bulk_from)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = rangeTo,
                            onValueChange = { rangeTo = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.watch_bulk_to)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownloadAll = false
                    gate { vm.downloadEpisodes(rangeFrom.toDoubleOrNull(), rangeTo.toDoubleOrNull()) }
                }) { Text(stringResource(R.string.watch_bulk_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDownloadAll = false }) { Text(stringResource(R.string.watch_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    ep: Episode,
    progress: WatchHistoryEntity?,
    isResume: Boolean,
    download: DownloadItem?,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onMark: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onMarkBefore: () -> Unit,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .background(if (isResume) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val finished = progress?.isFinished == true
        Box(Modifier.width(124.dp).height(70.dp).clip(RoundedCornerShape(10.dp))) {
            NetImage(
                ep.thumbnail,
                Modifier.fillMaxSize().alpha(if (finished) 0.4f else 1f).sharedKey(sharedEpisodeKey(ep.id, "detail")).clip(RoundedCornerShape(10.dp)),
                // The row's texts already say which episode; a description would replace them in TalkBack.
                null,
            )
            if (finished) {
                Icon(Icons.Rounded.Check, stringResource(R.string.watch_watched_cd), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
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
                    stringResource(R.string.watch_episode_label, ep.number),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (ep.isNew) {
                    Spacer(Modifier.width(6.dp))
                    NewBadge()
                }
            }
            if (ep.title.isNotBlank() && ep.title != "Episode ${ep.number}") {
                Text(ep.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOf(ep.releaseDate, if (ep.views > 0) "${compactNumber(LocalContext.current.resources, ep.views)} x" else "").filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EpisodeDownloadButton(download, onDownload, onDeleteDownload)
    }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (progress?.isFinished == true) R.string.ux_mark_unwatched else R.string.ux_mark_watched)) },
                onClick = { menu = false; onMark(progress?.isFinished != true) },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.ux_restart)) }, onClick = { menu = false; onRestart() })
            DropdownMenuItem(text = { Text(stringResource(R.string.ux_mark_before_watched)) }, onClick = { menu = false; onMarkBefore() })
            DropdownMenuItem(text = { Text(stringResource(R.string.watch_download)) }, onClick = { menu = false; onDownload() })
        }
    }
}

@Composable
private fun CollectionStatusDialog(
    selected: CollectionStatus?,
    hiddenFromContinue: Boolean,
    onSelect: (CollectionStatus?) -> Unit,
    onHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ux_collection)) },
        text = {
            Column {
                CollectionStatus.entries.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(status) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == status, onClick = { onSelect(status) })
                        Text(stringResource(status.labelResource()))
                    }
                }
                if (selected != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onHidden(!hiddenFromContinue) }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(hiddenFromContinue, onCheckedChange = onHidden)
                        Text(stringResource(R.string.ux_hide_continue))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ux_done)) } },
        dismissButton = {
            if (selected != null) TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.ux_collection_remove)) }
        },
    )
}

@Composable
private fun AnimeNotificationDialog(
    selected: TitleNotifyMode,
    autoDownload: Boolean,
    onMode: (TitleNotifyMode) -> Unit,
    onAutoDownload: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val blocked = notificationAccess(context) != NotificationAccess.GRANTED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ux_notification_title)) },
        text = {
            Column {
                TitleNotifyMode.entries.forEach { mode ->
                    val label = when (mode) {
                        TitleNotifyMode.DEFAULT -> R.string.ux_notify_default
                        TitleNotifyMode.ALL -> R.string.ux_notify_all
                        TitleNotifyMode.FINAL_ONLY -> R.string.ux_notify_final
                        TitleNotifyMode.OFF -> R.string.ux_notify_off
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onMode(mode) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == mode, onClick = { onMode(mode) })
                        Text(stringResource(label))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onAutoDownload(!autoDownload) }.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(autoDownload, onCheckedChange = onAutoDownload)
                    Text(stringResource(R.string.ux_auto_download))
                }
                if (blocked) {
                    Text(
                        stringResource(R.string.ux_notify_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { openNotificationSettings(context) }) {
                        Text(stringResource(R.string.mine_notify_open_settings))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ux_done)) } },
    )
}

private fun CollectionStatus.labelResource() = when (this) {
    CollectionStatus.PLANNED -> R.string.ux_status_planned
    CollectionStatus.IN_PROGRESS -> R.string.ux_status_in_progress
    CollectionStatus.WAITING -> R.string.ux_status_waiting
    CollectionStatus.PAUSED -> R.string.ux_status_paused
    CollectionStatus.COMPLETED -> R.string.ux_status_completed
    CollectionStatus.DROPPED -> R.string.ux_status_dropped
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
private fun DetailTopBar(solid: Boolean, title: String, onBack: () -> Unit, hazeState: HazeState) {
    Row(
        Modifier
            .fillMaxWidth()
            // Frosted once the header has scrolled away; the content stays faintly visible behind it.
            .then(if (solid) Modifier.hazeEffect(hazeState, frostedStyle()) else Modifier)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (solid) Color.Transparent else Color.Black.copy(alpha = 0.4f),
                contentColor = if (solid) MaterialTheme.colorScheme.onSurface else Color.White,
            ),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.watch_back)) }
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
                    Icon(Icons.Rounded.DownloadDone, stringResource(R.string.watch_saved_offline_cd), tint = MaterialTheme.colorScheme.tertiary)
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
                    stringResource(if (download?.state == DownloadState.FAILED) R.string.watch_download_failed_retry else R.string.watch_download),
                    tint = if (download?.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        val running = download?.inProgress == true
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(if (running) R.string.watch_cancel_download_title else R.string.watch_delete_download_title)) },
            text = { Text(stringResource(if (running) R.string.watch_cancel_download_msg else R.string.watch_delete_download_msg)) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(stringResource(if (running) R.string.watch_stop_download else R.string.watch_delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.watch_close)) } },
        )
    }
}

/** Clickable trailer card: AniList's poster frame with a play badge; the trailer opens outside the app. */
@Composable
private fun TrailerCard(
    thumbnail: String,
    fallbackThumbnail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.watch_trailer)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        NetImage(thumbnail.ifBlank { fallbackThumbnail }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(34.dp), tint = Color.White)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Opens the manga this anime adapts. */
@Composable
private fun ReadMangaButton(lookup: MangaLookup, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        if (lookup == MangaLookup.Searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.watch_read_manga))
    }
}

/** Characters and their Japanese voice actors from AniList, main cast first; swipe for more. */
@Composable
private fun CastRow(cast: List<CastEntry>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cast, key = { it.role.name + it.character + it.voiceActor.orEmpty() }) { c ->
            val role = stringResource(if (c.role == CastRole.MAIN) R.string.watch_cast_main else R.string.watch_cast_supporting)
            Column(
                Modifier.width(88.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NetImage(c.imageUrl.orEmpty(), Modifier.size(72.dp).clip(CircleShape))
                Spacer(Modifier.height(6.dp))
                Text(c.character, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(
                    c.voiceActor ?: role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
