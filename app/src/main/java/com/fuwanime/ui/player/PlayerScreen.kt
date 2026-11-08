package com.fuwanime.ui.player

import com.fuwanime.util.text
import com.fuwanime.ui.components.IconWithTooltip
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import com.fuwanime.data.Anime
import com.fuwanime.data.SeasonEntry
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.geometry.Offset
import com.fuwanime.data.meta.SkipSegment
import com.fuwanime.data.meta.SegmentKind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.animation.core.tween
import com.fuwanime.ui.motion.rememberIsExiting
import com.fuwanime.ui.motion.rememberIsEntering
import com.fuwanime.ui.motion.LocalNavScope
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.Motion
import com.fuwanime.FuwaApp
import com.fuwanime.R
import com.fuwanime.util.UiText
import com.fuwanime.util.formatBytes
import com.fuwanime.data.DownloadState
import com.fuwanime.data.DownloadItem
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import com.fuwanime.data.Episode
import com.fuwanime.data.OpMark
import com.fuwanime.data.StreamSource
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.VideoResize
import com.fuwanime.ui.components.NetImage
import com.fuwanime.util.formatDuration
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FuwaPlayer"

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun mediaItem(url: String, np: NowPlaying): MediaItem = MediaItem.Builder()
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(np.title)
            .setArtist(np.artist)
            .setSubtitle(np.subtitle.ifBlank { null })
            .setArtworkUri(np.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .build(),
    )
    .build()

private fun Context.isMeteredNetwork(): Boolean =
    (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A downloaded file plays from a local source, which has no quality tag or server of its own. */
private fun StreamSource.isLocal() = url.startsWith("file:")

@Composable
private fun StreamSource.label(): String = if (isLocal()) stringResource(R.string.watch_quality_offline, quality) else quality

/** Snapshot of ExoPlayer state, polled for the Compose controls. */
private class PlaybackState {
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var videoWidth by mutableIntStateOf(16)
    var videoHeight by mutableIntStateOf(9)
    var error by mutableStateOf<UiText?>(null)

    /**
     * Seek requested before the stream became seekable. The MKV files keep their seek index (Cues)
     * at the end, so ExoPlayer reports the item unseekable until it has fetched it; seeking then
     * would jump to 0:00. We hold the target and apply it once the index is in.
     */
    var pendingSeek by mutableStateOf<Long?>(null)

    /** False until the current source shows its first frame; the thumbnail placeholder covers the gap. */
    var firstFrame by mutableStateOf(false)
}

private fun ExoPlayer.seekSafely(positionMs: Long, playback: PlaybackState) {
    val target = positionMs.coerceAtLeast(0).let { t -> if (duration > 0) t.coerceAtMost(duration - 1_000) else t }
    if (isCurrentMediaItemSeekable) {
        playback.pendingSeek = null
        seekTo(target)
    } else {
        playback.pendingSeek = target
    }
    playback.position = target
}

/** Position the user expects to be at, counting a seek that is still waiting for the index. */
private fun ExoPlayer.effectivePosition(playback: PlaybackState): Long = playback.pendingSeek ?: currentPosition

@OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(
    /** Key of the image (episode thumbnail or detail backdrop) that flies into the video area. */
    sharedKey: String? = null,
    onBack: () -> Unit,
    /** Opens another anime's Detail (the next season at the end of this one). */
    onOpenAnime: (Anime) -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val vm: PlayerViewModel = fuwaViewModel { c, h ->
        PlayerViewModel(
            c.animeRepository, c.libraryRepository, c.downloadRepository, c.settingsStore, c.appScope,
            isMetered = { appContext.isMeteredNetwork() },
            saved = h,
            refreshHomeSurfaces = c::refreshHomeSurfaces,
            links = c.animeLinkRepository,
            skips = c.skipRepository,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = LocalContext.current.resources
    LaunchedEffect(state.message) {
        state.message?.let {
            vm.messageShown()
            snackbar.showSnackbar(it.resolve(res))
        }
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val opMark by vm.opMark.collectAsStateWithLifecycle()
    val segmentsState by vm.segments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val pip = LocalPipController.current
    val inPip = pip?.isInPip?.value == true
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val holder = remember(context) { (context.applicationContext as FuwaApp).container.playbackHolder }
    val player = remember { holder.acquire() }
    val initialEpisodeId = remember { state.episode.id }
    // The nav-arg thumbnail, which is what flew in; the stream may report a different URL for the same image.
    val initialThumbnail = remember { state.episode.thumbnail }
    val playback = remember { PlaybackState() }
    var speed by remember { mutableFloatStateOf(1f) }
    var sleep by remember { mutableStateOf(SleepOption.OFF) }
    var sleepAtMs by remember { mutableStateOf<Long?>(null) }
    /** Set when a timed sleep fired; blocks autoplay until the user presses play again. */
    var sleepExpired by remember { mutableStateOf(false) }
    /** Episode whose opening was auto-skipped or re-marked; survives rotation and PiP unlike VideoSurface state. */
    var autoSkipHandledFor by remember { mutableStateOf<String?>(null) }

    // --- Player wiring ---------------------------------------------------------------------
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playback.isPlaying = isPlaying
                if (isPlaying) sleepExpired = false
                view.keepScreenOn = isPlaying
            }

            override fun onPlaybackStateChanged(s: Int) {
                playback.buffering = s == Player.STATE_BUFFERING
                playback.ended = s == Player.STATE_ENDED
                if (s == Player.STATE_READY) playback.error = null
                if (s == Player.STATE_ENDED) vm.onPlayerExit(player.duration, player.duration)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                Log.d(TAG, "timeline reason=$reason seekable=${player.isCurrentMediaItemSeekable} duration=${player.duration}")
                val pending = playback.pendingSeek
                if (pending != null && player.isCurrentMediaItemSeekable) {
                    playback.pendingSeek = null
                    player.seekTo(pending)
                }
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                Log.d(TAG, "discontinuity reason=$reason ${old.positionMs} -> ${new.positionMs}")
            }

            override fun onPlaybackParametersChanged(params: PlaybackParameters) {
                Log.d(TAG, "speed=${params.speed}")
            }

            override fun onRenderedFirstFrame() {
                playback.firstFrame = true
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    playback.videoWidth = size.width
                    playback.videoHeight = size.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A smaller file often plays where the bigger one failed; only surface the error when nothing is left.
                if (shouldFallBack(error.errorCode) && vm.fallBack(player.effectivePosition(playback).coerceAtLeast(0))) return
                playback.error = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> UiText(R.string.watch_err_connection)
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        UiText(R.string.watch_err_timeout)
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> UiText(R.string.watch_err_http)
                    else -> UiText(R.string.watch_err_play)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            vm.saveProgress(player.currentPosition, player.duration)
            player.removeListener(listener)
            holder.release(player)
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(state.source?.url) {
        playback.error = null
        playback.ended = false
        playback.pendingSeek = null
        playback.firstFrame = false
        val source = state.source ?: run {
            player.stop()
            return@LaunchedEffect
        }
        player.setMediaItem(mediaItem(source.url, nowPlaying(res, state.episode, state.anime)), state.startPositionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = !sleepExpired
    }

    LaunchedEffect(player) {
        while (true) {
            playback.position = player.effectivePosition(playback).coerceAtLeast(0)
            playback.buffered = player.bufferedPosition.coerceAtLeast(0)
            playback.duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            // Only once the loaded item is this episode's source does the duration belong to it.
            if (segmentSourceReady(playback.firstFrame, player.currentMediaItem?.localConfiguration?.uri?.toString(), state.source?.url)) {
                vm.onDurationKnown(state.episode.id, playback.duration)
            }
            delay(250)
        }
    }

    // Persist progress periodically so a crash or swipe-away never loses more than a few seconds.
    LaunchedEffect(player, state.episode.id) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) {
                vm.saveProgress(player.currentPosition, player.duration)
                vm.addWatchTime(5_000)
            }
        }
    }

    // Sleep timer: a wall-clock deadline, so it keeps counting across episode changes.
    LaunchedEffect(sleepAtMs) {
        val at = sleepAtMs ?: return@LaunchedEffect
        delay((at - System.currentTimeMillis()).coerceAtLeast(0))
        sleepExpired = true
        player.pause()
        sleep = SleepOption.OFF
        sleepAtMs = null
        snackbar.showSnackbar(res.getString(R.string.watch_sleep_paused))
    }
    // "Akhir episode" is one-shot: it covers the episode it was set on.
    LaunchedEffect(state.episode.id) {
        if (sleep == SleepOption.END_OF_EPISODE) sleep = SleepOption.OFF
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.onPlayerExit(player.currentPosition, player.duration)
        // Closing the PiP window stops the activity while it still reports PiP mode.
        val wasInPip = inPip || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true)
        val screenOn = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (stopAction(wasInPip, screenOn, settings.backgroundAudio) == StopAction.PAUSE) player.pause()
    }

    // --- PiP + fullscreen -----------------------------------------------------------------
    DisposableEffect(pip, playback.isPlaying, settings.autoPip, playback.videoWidth, playback.videoHeight) {
        pip?.setAutoEnter(playback.isPlaying && settings.autoPip, playback.videoWidth, playback.videoHeight, playback.isPlaying)
        onDispose { pip?.setAutoEnter(false) }
    }

    /** The user's fullscreen choice since the last rotation; null follows the orientation. */
    var manualFullscreen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(landscape) { manualFullscreen = null }
    val fullscreen = isFullscreen(landscape, manualFullscreen, inPip)
    DisposableEffect(fullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = -1f } }
        }
    }
    fun setFullscreen(enable: Boolean) {
        manualFullscreen = enable
        activity?.requestedOrientation =
            if (enable) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    BackHandler(enabled = fullscreen) { setFullscreen(false) }

    // --- Layout ---------------------------------------------------------------------------
    var showSettings by remember { mutableStateOf(false) }

    val videoArea: @Composable (Modifier) -> Unit = { modifier ->
        VideoSurface(
            player = player,
            playback = playback,
            state = state,
            settings = settings,
            resize = settings.videoResize,
            onResize = vm::setVideoResize,
            speed = speed,
            opMark = opMark,
            segments = segmentsFor(segmentsState, state.episode.id),
            sleep = sleep,
            sleepExpired = sleepExpired,
            // Only the episode that was tapped has a thumbnail to fly in; later episodes just fade.
            thumbnailKey = sharedKey?.takeIf { state.episode.id == initialEpisodeId },
            placeholderUrl = if (state.episode.id == initialEpisodeId) initialThumbnail.ifBlank { state.episode.thumbnail } else state.episode.thumbnail,
            autoSkipHandledFor = autoSkipHandledFor,
            onAutoSkipped = { autoSkipHandledFor = it },
            fullscreen = fullscreen,
            // Hide the controls as soon as the shrink starts, not after it lands.
            inPip = inPip || pip?.isEnteringPip?.value == true,
            onBack = { if (fullscreen) setFullscreen(false) else onBack() },
            onToggleFullscreen = { setFullscreen(!fullscreen) },
            onOpenSettings = { showSettings = true },
            onEnterPip = { pip?.enter() },
            pipSupported = pip?.isSupported == true,
            onNext = vm::playNext,
            onPrevious = vm::playPrevious,
            onOpenAnime = onOpenAnime,
            onRetry = {
                if (state.error != null) vm.retry()
                else {
                    playback.error = null
                    player.prepare()
                    player.play()
                }
            },
            modifier = modifier,
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (fullscreen || inPip) {
            videoArea(Modifier.fillMaxSize().background(Color.Black))
        } else {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.background(Color.Black).statusBarsPadding()) {
                    videoArea(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                EpisodeInfo(
                    state = state,
                    download = download,
                    onSelectSource = { vm.selectSource(it, player.currentPosition) },
                    onPlay = vm::play,
                    onNext = vm::playNext,
                    onPrevious = vm::playPrevious,
                    onDownload = vm::downloadCurrent,
                    onDeleteDownload = vm::deleteDownload,
                )
            }
        }
        // One snackbar host for both layouts so messages (download started/failed) are visible fullscreen too.
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (showSettings) {
        PlayerSettingsSheet(
            sources = state.sources,
            current = state.source,
            speed = speed,
            resize = settings.videoResize,
            positionMs = playback.position,
            opMark = opMark,
            sleep = sleep,
            onDismiss = { showSettings = false },
            onSource = {
                vm.selectSource(it, player.currentPosition)
                showSettings = false
            },
            onSpeed = {
                speed = it
                player.setPlaybackSpeed(it)
            },
            onResize = vm::setVideoResize,
            onMarkOpStart = {
                autoSkipHandledFor = state.episode.id
                vm.markOpStart(player.effectivePosition(playback))
            },
            onMarkOpEnd = {
                autoSkipHandledFor = state.episode.id
                vm.markOpEnd(player.effectivePosition(playback))
            },
            onClearOpMark = vm::clearOpMark,
            onSleep = { option ->
                sleep = option
                sleepAtMs = sleepDeadline(option, System.currentTimeMillis())
            },
        )
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalAnimationApi::class)
@Composable
private fun VideoSurface(
    player: ExoPlayer,
    playback: PlaybackState,
    state: PlayerUiState,
    settings: AppSettings,
    resize: VideoResize,
    onResize: (VideoResize) -> Unit,
    speed: Float,
    opMark: OpMark?,
    segments: List<SkipSegment>,
    sleep: SleepOption,
    sleepExpired: Boolean,
    thumbnailKey: String?,
    placeholderUrl: String,
    autoSkipHandledFor: String?,
    onAutoSkipped: (String) -> Unit,
    fullscreen: Boolean,
    inPip: Boolean,
    pipSupported: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterPip: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenAnime: (Anime) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var seekFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // (forward, seconds)
    var gestureLevel by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }
    var boosting by remember { mutableStateOf(false) }
    val baseSpeed by rememberUpdatedState(speed)
    var locked by remember { mutableStateOf(false) }
    /** Where the last auto-skip jumped from, while its "Batalkan" chip shows. */
    var undoSkipTo by remember { mutableStateOf<Long?>(null) }
    /** Mode just picked by a pinch, shown briefly in the middle of the video. */
    var resizeHint by remember { mutableStateOf<VideoResize?>(null) }
    LaunchedEffect(resizeHint) {
        if (resizeHint != null) {
            delay(1_000)
            resizeHint = null
        }
    }
    var showUnlock by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) { if (!fullscreen) locked = false }
    BackHandler(enabled = locked) { showUnlock = true }

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3_500)
            if (player.isPlaying) controlsVisible = false
        }
    }
    LaunchedEffect(playback.isPlaying) {
        if (!playback.isPlaying) controlsVisible = true
        scheduleHide()
    }

    val seekStepMs = settings.doubleTapSeekSeconds * 1000L

    // Inflating PlayerView (a SurfaceView) is heavy; doing it on the transition's first frame made the flying
    // thumbnail stall. It is attached once the open transition settles; the thumbnail covers the wait.
    val navTransitionRunning = LocalNavScope.current?.transition?.isRunning == true
    var surfaceReady by remember { mutableStateOf(!navTransitionRunning) }
    LaunchedEffect(navTransitionRunning) { if (!navTransitionRunning) surfaceReady = true }

    Box(modifier.background(Color.Black)) {
        if (surfaceReady) AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = {
                it.resizeMode = if (resize == VideoResize.ZOOM) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Thumbnail until the first frame: the landing spot for the shared thumbnail, then a soft reveal.
        // Held while the open transition runs, so a fast first frame never cuts the flying thumbnail short. Only on
        // open: appearing mid-way through the close transition left the detail backdrop stuck above its header.
        val entering = rememberIsEntering()
        val exiting = rememberIsExiting()
        AnimatedVisibility(
            visible = (!playback.firstFrame || entering) && !state.offline && placeholderUrl.isNotBlank(),
            enter = fadeIn(tween(Motion.Short)),
            exit = fadeOut(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                NetImage(placeholderUrl, Modifier.fillMaxSize().sharedKey(thumbnailKey.takeIf { !exiting }))
                // Keeps the white loading text legible on bright thumbnails.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
        }

        if (inPip) return@Box

        // Gesture layer: tap toggles controls, double-tap seeks, hold plays at 2x, vertical drags adjust brightness/volume.
        Box(
            Modifier
                .fillMaxSize()
                // Two-finger pinch switches Pas/Isi layar. It runs first (Initial pass) and consumes the gesture once
                // a second finger lands, so tap, double-tap seek, hold-2x and the level drags never fire with it.
                .pointerInput(resize, locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var startDistance = 0f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                if (startDistance == 0f) {
                                    startDistance = distance
                                } else if (distance > 0f) {
                                    pinchResize(resize, distance / startDistance)?.let {
                                        onResize(it)
                                        resizeHint = it
                                        startDistance = distance
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(seekStepMs) {
                    detectTapGestures(
                        onPress = {
                            val boost = scope.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                boosting = true
                                player.setPlaybackSpeed(boostedSpeed(baseSpeed))
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                // Also runs when the gesture is cancelled (rotation, PiP), so 2x never sticks.
                                boost.cancel()
                                if (boosting) {
                                    boosting = false
                                    player.setPlaybackSpeed(baseSpeed)
                                }
                            }
                        },
                        // Handled in onPress; declaring it stops the release from also counting as a tap.
                        onLongPress = {},
                        onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) scheduleHide()
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2
                            player.seekSafely(player.effectivePosition(playback) + if (forward) seekStepMs else -seekStepMs, playback)
                            val prev = seekFeedback
                            val secs = if (prev != null && prev.first == forward) prev.second + settings.doubleTapSeekSeconds else settings.doubleTapSeekSeconds
                            seekFeedback = forward to secs
                            scope.launch {
                                delay(700)
                                if (seekFeedback?.second == secs) seekFeedback = null
                            }
                        },
                    )
                }
                .pointerInput(fullscreen) {
                    if (!fullscreen) return@pointerInput
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    var leftSide = false
                    var level = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            leftSide = start.x < size.width / 2
                            level = if (leftSide) {
                                activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                            } else {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume
                            }
                        },
                        onDragEnd = { gestureLevel = null },
                        onDragCancel = { gestureLevel = null },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                            if (leftSide) {
                                activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) } }
                                gestureLevel = Icons.Rounded.BrightnessMedium to level
                            } else {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVolume).toInt(), 0)
                                gestureLevel = Icons.Rounded.VolumeUp to level
                            }
                        },
                    )
                },
        )

        seekFeedback?.let { (forward, secs) ->
            SeekBubble(forward, secs, Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart))
        }
        gestureLevel?.let { (icon, level) -> LevelIndicator(icon, level, Modifier.align(Alignment.Center)) }
        resizeHint?.let { ResizeHint(it, Modifier.align(Alignment.Center)) }
        if (boosting) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${boostedSpeed(baseSpeed)}x".replace(".0x", "x"), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if ((playback.buffering || playback.pendingSeek != null) && playback.error == null && !state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White, strokeWidth = 3.dp)
        }
        if (state.loading) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.watch_msg_preparing_episode, state.episode.number), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !state.loading && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                player = player,
                playback = playback,
                state = state,
                markers = segments.map { it.startMs to it.endMs } + listOfNotNull(opMark?.takeIf { it.isValid }?.let { it.startMs to it.endMs }),
                fullscreen = fullscreen,
                pipSupported = pipSupported,
                onLock = {
                    locked = true
                    showUnlock = false
                    controlsVisible = false
                },
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                onOpenSettings = onOpenSettings,
                onEnterPip = onEnterPip,
                onNext = onNext,
                onPrevious = onPrevious,
                onInteract = ::scheduleHide,
            )
        }

        val skip = if (!state.loading && playback.error == null && !playback.ended) {
            skipActionAt(playback.position, playback.duration, opMark, jumpAhead = settings.jumpAheadButton, segments = segments)
        } else null
        val autoTarget = (skip as? SkipAction.SkipOp)?.targetMs ?: (skip as? SkipSegmentAction)?.takeIf { it.kind == SegmentKind.OP }?.targetMs
        LaunchedEffect(autoTarget, state.episode.id) {
            if (skip != null && shouldAutoSkip(settings.autoSkipOp, skip, state.episode.id, autoSkipHandledFor)) {
                onAutoSkipped(state.episode.id)
                undoSkipTo = player.effectivePosition(playback)
                player.seekSafely(skip.targetMs, playback)
            }
        }
        LaunchedEffect(undoSkipTo) {
            if (undoSkipTo != null) {
                delay(5_000)
                undoSkipTo = null
            }
        }
        undoSkipTo?.let { from ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (fullscreen) 32.dp else 12.dp, bottom = if (controlsVisible) 72.dp else 16.dp),
            ) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.watch_intro_skipped), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        // Back to where the opening started; autoSkipHandledFor already stops a second skip.
                        player.seekSafely(from, playback)
                        undoSkipTo = null
                    }) { Text(stringResource(R.string.watch_undo), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (skip != null && skipButtonVisible(controlsVisible, fullscreen, locked)) {
            OutlinedButton(
                onClick = {
                    player.seekSafely(skip.targetMs, playback)
                    if (controlsVisible) scheduleHide()
                },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (fullscreen) 32.dp else 12.dp, bottom = if (controlsVisible) 72.dp else 16.dp),
            ) {
                Icon(Icons.Rounded.FastForward, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(skipLabel(skip)))
            }
        }

        val error = (state.error ?: playback.error)?.text()
        if (error != null && !state.loading) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.watch_back), color = Color.White) }
                    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }
        }

        if (playback.ended && !state.loading) {
            EndOverlay(
                next = state.next,
                nextSeason = offerNextSeason(state.anime, state.next, state.nextSeason),
                onOpenSeason = onOpenAnime,
                autoPlay = autoPlayAllowed(settings.autoPlayNext, sleep, sleepExpired),
                onNext = onNext,
                onReplay = {
                    player.seekTo(0)
                    player.play()
                },
            )
        }

        // Topmost, so it swallows every touch meant for the layers below.
        if (locked) {
            LockedLayer(
                showUnlock = showUnlock,
                onTap = { showUnlock = !showUnlock },
                onUnlock = {
                    locked = false
                    showUnlock = false
                    controlsVisible = true
                    scheduleHide()
                },
            )
        }
    }
}

@Composable
private fun Controls(
    player: ExoPlayer,
    playback: PlaybackState,
    state: PlayerUiState,
    /** Skippable ranges (AniSkip segments, the user's OP mark) drawn on the seekbar. */
    markers: List<Pair<Long, Long>>,
    fullscreen: Boolean,
    pipSupported: Boolean,
    onLock: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterPip: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onInteract: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.7f),
                    0.3f to Color.Black.copy(alpha = 0.25f),
                    0.7f to Color.Black.copy(alpha = 0.25f),
                    1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 4.dp, vertical = 4.dp)
                .then(if (fullscreen) Modifier.padding(horizontal = 24.dp, vertical = 8.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.watch_back), tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(
                    state.anime?.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.watch_episode_label, state.episode.number) + (state.source?.let { " • " + it.label() } ?: ""),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (fullscreen) {
                IconWithTooltip(stringResource(R.string.watch_lock_screen), Icons.Rounded.Lock, onLock, tint = Color.White)
            }
            if (pipSupported) {
                IconWithTooltip("Picture-in-Picture", Icons.Rounded.PictureInPictureAlt, onEnterPip, tint = Color.White)
            }
            IconWithTooltip(stringResource(R.string.watch_video_settings), Icons.Rounded.Settings, { onOpenSettings(); onInteract() }, tint = Color.White)
        }

        // Center transport
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 36.dp else 20.dp),
        ) {
            TransportButton(Icons.Rounded.SkipPrevious, stringResource(R.string.watch_prev_episode), enabled = state.previous != null, onClick = { onPrevious(); onInteract() })
            TransportButton(Icons.Rounded.FastRewind, stringResource(R.string.watch_rewind_10), onClick = { player.seekSafely(player.effectivePosition(playback) - 10_000, playback); onInteract() })
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(64.dp).clip(CircleShape).clickable {
                    if (player.isPlaying) player.pause() else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play()
                    }
                    onInteract()
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playback.isPlaying) R.string.watch_pause else R.string.watch_play),
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            TransportButton(Icons.Rounded.FastForward, stringResource(R.string.watch_forward_10), onClick = { player.seekSafely(player.effectivePosition(playback) + 10_000, playback); onInteract() })
            TransportButton(Icons.Rounded.SkipNext, stringResource(R.string.watch_next_episode), enabled = state.next != null, onClick = { onNext(); onInteract() })
        }

        // Bottom: time + seek bar
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .then(if (fullscreen) Modifier.padding(horizontal = 32.dp, vertical = 12.dp) else Modifier.padding(horizontal = 12.dp)),
        ) {
            val duration = playback.duration.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = scrubbing?.let { (it * duration).toLong() } ?: playback.position
                Text(
                    "${formatDuration(shown)} / ${formatDuration(playback.duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconWithTooltip(
                    stringResource(if (fullscreen) R.string.watch_exit_fullscreen else R.string.watch_fullscreen),
                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    onToggleFullscreen,
                    tint = Color.White,
                )
            }
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(
                    progress = { playback.buffered.toFloat() / duration },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(3.dp).clip(CircleShape),
                    color = Color.White.copy(alpha = 0.35f),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
                if (markers.isNotEmpty()) {
                    val markerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(4.dp)) {
                        markers.forEach { (start, end) ->
                            val x0 = size.width * (start.toFloat() / duration).coerceIn(0f, 1f)
                            val x1 = size.width * (end.toFloat() / duration).coerceIn(0f, 1f)
                            drawRect(markerColor, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
                        }
                    }
                }
                Slider(
                    value = scrubbing ?: (playback.position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = {
                        scrubbing = it
                        onInteract()
                    },
                    onValueChangeFinished = {
                        scrubbing?.let { player.seekSafely((it * duration).toLong(), playback) }
                        scrubbing = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )
            }
        }
    }
}

/** While locked, touches only reveal the unlock button; seeking, gestures and back are ignored. */
@Composable
private fun LockedLayer(showUnlock: Boolean, onTap: () -> Unit, onUnlock: () -> Unit) {
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(3_000)
            onTap()
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        if (showUnlock) {
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Icon(Icons.Rounded.LockOpen, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.watch_unlock))
            }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, description, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun SeekBubble(forward: Boolean, seconds: Int, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .fillMaxWidth(0.35f)
            .clip(if (forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White)
            Text(pluralStringResource(if (forward) R.plurals.watch_seek_forward else R.plurals.watch_seek_back, seconds, seconds), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResizeHint(resize: VideoResize, modifier: Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Text(
            stringResource(if (resize == VideoResize.ZOOM) R.string.watch_fill else R.string.watch_fit),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LevelIndicator(icon: ImageVector, level: Float, modifier: Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier.width(120.dp).height(4.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun EndOverlay(
    next: Episode?,
    nextSeason: SeasonEntry?,
    onOpenSeason: (Anime) -> Unit,
    autoPlay: Boolean,
    onNext: () -> Unit,
    onReplay: () -> Unit,
) {
    var remaining by remember(next?.id) { mutableIntStateOf(if (autoPlay && next != null) 6 else -1) }
    LaunchedEffect(next?.id, autoPlay) {
        if (!autoPlay || next == null) {
            // Autoplay can be switched off mid-countdown (sleep timer); drop the frozen count.
            remaining = -1
            return@LaunchedEffect
        }
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        if (remaining == 0) onNext()
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (next != null) {
            Text(stringResource(R.string.watch_up_next), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.watch_episode_label, next.number), color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remaining > 0) {
                    OutlinedButton(onClick = { remaining = -1 }) { Text(stringResource(R.string.watch_cancel), color = Color.White) }
                }
                Button(onClick = onNext) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (remaining > 0) stringResource(R.string.watch_play_countdown, remaining) else stringResource(R.string.watch_play_now))
                }
            }
        } else if (nextSeason != null) {
            Surface(
                onClick = { onOpenSeason(nextSeason.anime) },
                color = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = nextSeason.anime.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.watch_next_season, nextSeason.label.ifBlank { stringResource(R.string.watch_next_season_fallback) }, nextSeason.anime.title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
            }
        } else {
            Text(stringResource(R.string.watch_latest_reached), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onReplay) {
            Icon(Icons.Rounded.Replay, null, tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.watch_replay), color = Color.White)
        }
    }
}

@Composable
private fun EpisodeInfo(
    state: PlayerUiState,
    download: DownloadItem?,
    onSelectSource: (StreamSource) -> Unit,
    onPlay: (Episode) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDownload: (StreamSource) -> Unit,
    onDeleteDownload: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        item {
            Column(Modifier.padding(20.dp)) {
                Text(state.anime?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.watch_episode_label, state.episode.number),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Short "Ep N" labels so the text never wraps on narrow phones.
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = state.previous != null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            state.previous?.let { stringResource(R.string.watch_ep_short, it.number) } ?: stringResource(R.string.watch_previous),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.next != null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            state.next?.let { stringResource(R.string.watch_ep_short, it.number) } ?: stringResource(R.string.watch_next),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.SkipNext, null, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                DownloadRow(state, download, onDownload, onDeleteDownload)
            }
        }
        if (state.sources.size > 1) {
            item {
                Text(stringResource(R.string.watch_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.sources, key = { it.url }) { s ->
                        FilterChip(
                            selected = s.url == state.source?.url,
                            onClick = { onSelectSource(s) },
                            label = { Text(s.label()) },
                            leadingIcon = if (s.url == state.source?.url) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
        }
        if (state.nearby.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.watch_episodes_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                )
                val rowState = rememberLazyListState()
                LaunchedEffect(state.episode.id, state.nearby.size) {
                    val i = state.nearby.indexOfFirst { it.id == state.episode.id }
                    if (i >= 0) rowState.scrollToItem((i - 1).coerceAtLeast(0))
                }
                LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.nearby, key = { it.id }) { ep ->
                        val current = ep.id == state.episode.id
                        Column(
                            Modifier.width(150.dp).clip(MaterialTheme.shapes.small).clickable(enabled = !current) { onPlay(ep) },
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {
                                NetImage(ep.thumbnail, Modifier.fillMaxSize(), stringResource(R.string.watch_episode_label, ep.number))
                                if (current) {
                                    Box(
                                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(stringResource(R.string.watch_now_playing), color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Text(
                                stringResource(R.string.watch_episode_label, ep.number),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    sources: List<StreamSource>,
    current: StreamSource?,
    speed: Float,
    resize: VideoResize,
    positionMs: Long,
    opMark: OpMark?,
    sleep: SleepOption,
    onDismiss: () -> Unit,
    onSource: (StreamSource) -> Unit,
    onSpeed: (Float) -> Unit,
    onResize: (VideoResize) -> Unit,
    onMarkOpStart: () -> Unit,
    onMarkOpEnd: () -> Unit,
    onClearOpMark: () -> Unit,
    onSleep: (SleepOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SheetTitle(stringResource(R.string.watch_quality)) }
            items(sources, key = { it.url }) { s ->
                ListItem(
                    headlineContent = { Text(s.label()) },
                    supportingContent = if (s.isLocal() || s.server.isNotBlank()) {
                        { Text(if (s.isLocal()) stringResource(R.string.watch_server_device) else stringResource(R.string.watch_server, s.server)) }
                    } else null,
                    trailingContent = if (s.url == current?.url) {
                        { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSource(s) },
                )
            }
            item {
                SheetTitle(stringResource(R.string.watch_speed))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Speeds) { sp ->
                        FilterChip(selected = sp == speed, onClick = { onSpeed(sp) }, label = { Text("${sp}x".replace(".0x", "x")) })
                    }
                }
            }
            item {
                SheetTitle(stringResource(R.string.watch_display))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = resize == VideoResize.FIT,
                        onClick = { onResize(VideoResize.FIT) },
                        label = { Text(stringResource(R.string.watch_fit)) },
                    )
                    FilterChip(
                        selected = resize == VideoResize.ZOOM,
                        onClick = { onResize(VideoResize.ZOOM) },
                        label = { Text(stringResource(R.string.watch_fill)) },
                        leadingIcon = { Icon(Icons.Rounded.AspectRatio, null, Modifier.size(16.dp)) },
                    )
                }
            }
            item {
                SheetTitle(stringResource(R.string.watch_sleep_timer))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SleepOption.entries) { option ->
                        FilterChip(selected = option == sleep, onClick = { onSleep(option) }, label = { Text(option.minutes?.let { pluralStringResource(R.plurals.watch_sleep_minutes, it, it) } ?: stringResource(option.label)) })
                    }
                }
            }
            item {
                SheetTitle(stringResource(R.string.watch_opening))
                Text(
                    if (opMark?.isValid == true) {
                        stringResource(R.string.watch_op_marked, formatDuration(opMark.startMs), formatDuration(opMark.endMs))
                    } else {
                        stringResource(R.string.watch_op_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onMarkOpStart) { Text(stringResource(R.string.watch_op_start, formatDuration(positionMs))) }
                    OutlinedButton(onClick = onMarkOpEnd) { Text(stringResource(R.string.watch_op_end, formatDuration(positionMs))) }
                }
                if (opMark != null) {
                    TextButton(onClick = onClearOpMark, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.watch_op_clear)) }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun DownloadRow(
    state: PlayerUiState,
    download: DownloadItem?,
    onDownload: (StreamSource) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    when {
        download?.state == DownloadState.DONE -> OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.watch_saved_offline_quality, download.entity.quality))
        }

        download?.inProgress == true -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Downloading, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (download.state) {
                        DownloadState.QUEUED -> stringResource(R.string.watch_download_queued)
                        DownloadState.PAUSED -> stringResource(R.string.watch_download_paused)
                        else -> {
                            val res = LocalContext.current.resources
                            val percent = (download.progress * 100).toInt()
                            val done = formatBytes(res, download.downloadedBytes)
                            if (download.totalBytes > 0) stringResource(R.string.watch_download_progress_total, percent, done, formatBytes(res, download.totalBytes))
                            else stringResource(R.string.watch_download_progress, percent, done)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete) { Text(stringResource(R.string.watch_cancel)) }
            }
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
        }

        else -> Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = state.sources.isNotEmpty() && !state.offline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (download?.state == DownloadState.FAILED) R.string.watch_download_failed_retry else R.string.watch_download_offline))
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                state.sources.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.quality) },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = {
                            menuOpen = false
                            onDownload(s)
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.watch_delete_download_title)) },
            text = { Text(stringResource(R.string.watch_delete_download_msg)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.watch_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.watch_cancel)) } },
        )
    }
}
