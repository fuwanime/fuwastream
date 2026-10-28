package com.fuwanime.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.animation.core.tween
import com.fuwanime.ui.motion.rememberIsExiting
import com.fuwanime.ui.motion.rememberIsEntering
import com.fuwanime.ui.motion.LocalNavScope
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.Motion
import com.fuwanime.FuwaApp
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
import androidx.media3.datasource.DefaultDataSource
import com.fuwanime.data.Episode
import com.fuwanime.data.OpMark
import com.fuwanime.data.StreamSource
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.ui.components.NetImage
import com.fuwanime.util.formatDuration
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FuwaPlayer"

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val client = (context.applicationContext as FuwaApp).container.videoHttpClient
    val http = OkHttpDataSource.Factory(client).setUserAgent(AnimeApiService.USER_AGENT)
    // DefaultDataSource routes file:// (offline downloads) locally and everything else through OkHttp.
    val dataSource = DefaultDataSource.Factory(context, http)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
        // Request audio focus: music/podcast from another app pauses instead of overlapping.
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .setHandleAudioBecomingNoisy(true)
        .build()
}

private fun Context.isMeteredNetwork(): Boolean =
    (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    var error by mutableStateOf<String?>(null)

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
) {
    val appContext = LocalContext.current.applicationContext
    val vm: PlayerViewModel = fuwaViewModel { c, h ->
        PlayerViewModel(
            c.animeRepository, c.libraryRepository, c.downloadRepository, c.settingsStore, c.appScope,
            isMetered = { appContext.isMeteredNetwork() },
            saved = h,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            vm.messageShown()
            snackbar.showSnackbar(it)
        }
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val opMark by vm.opMark.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val pip = LocalPipController.current
    val inPip = pip?.isInPip?.value == true
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val player = remember { buildPlayer(context) }
    val initialEpisodeId = remember { state.episode.id }
    // The nav-arg thumbnail, which is what flew in; the stream may report a different URL for the same image.
    val initialThumbnail = remember { state.episode.thumbnail }
    val playback = remember { PlaybackState() }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
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
                if (s == Player.STATE_ENDED) vm.saveProgress(player.duration, player.duration)
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
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Koneksi terputus saat memutar video."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Server video lambat merespons. Coba lagi atau pilih kualitas lain."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sumber video tidak bisa diakses. Coba kualitas lain."
                    else -> "Video gagal diputar. Coba kualitas lain."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            vm.saveProgress(player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
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
        player.setMediaItem(MediaItem.fromUri(source.url), state.startPositionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = !sleepExpired
    }

    LaunchedEffect(player) {
        while (true) {
            playback.position = player.effectivePosition(playback).coerceAtLeast(0)
            playback.buffered = player.bufferedPosition.coerceAtLeast(0)
            playback.duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(250)
        }
    }

    // Persist progress periodically so a crash or swipe-away never loses more than a few seconds.
    LaunchedEffect(player, state.episode.id) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) vm.saveProgress(player.currentPosition, player.duration)
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
        snackbar.showSnackbar("Timer tidur: video dijeda")
    }
    // "Akhir episode" is one-shot: it covers the episode it was set on.
    LaunchedEffect(state.episode.id) {
        if (sleep == SleepOption.END_OF_EPISODE) sleep = SleepOption.OFF
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.saveProgress(player.currentPosition, player.duration)
        player.pause()
    }

    // --- PiP + fullscreen -----------------------------------------------------------------
    DisposableEffect(pip, playback.isPlaying, settings.autoPip, playback.videoWidth, playback.videoHeight) {
        pip?.setAutoEnter(playback.isPlaying && settings.autoPip, playback.videoWidth, playback.videoHeight)
        onDispose { pip?.setAutoEnter(false) }
    }

    val fullscreen = landscape && !inPip
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
            resizeMode = resizeMode,
            speed = speed,
            opMark = opMark,
            sleep = sleep,
            sleepExpired = sleepExpired,
            // Only the episode that was tapped has a thumbnail to fly in; later episodes just fade.
            thumbnailKey = sharedKey?.takeIf { state.episode.id == initialEpisodeId },
            placeholderUrl = if (state.episode.id == initialEpisodeId) initialThumbnail.ifBlank { state.episode.thumbnail } else state.episode.thumbnail,
            autoSkipHandledFor = autoSkipHandledFor,
            onAutoSkipped = { autoSkipHandledFor = it },
            fullscreen = fullscreen,
            inPip = inPip,
            onBack = { if (fullscreen) setFullscreen(false) else onBack() },
            onToggleFullscreen = { setFullscreen(!fullscreen) },
            onOpenSettings = { showSettings = true },
            onEnterPip = { pip?.enter() },
            pipSupported = pip?.isSupported == true,
            onNext = vm::playNext,
            onPrevious = vm::playPrevious,
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
            resizeMode = resizeMode,
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
            onResize = { resizeMode = it },
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
    resizeMode: Int,
    speed: Float,
    opMark: OpMark?,
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
            update = { it.resizeMode = resizeMode },
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
                Text("Menyiapkan Episode ${state.episode.number}…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
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
            skipActionAt(playback.position, playback.duration, opMark)
        } else null
        LaunchedEffect((skip as? SkipAction.SkipOp)?.targetMs, state.episode.id) {
            if (skip != null && shouldAutoSkip(settings.autoSkipOp, skip, state.episode.id, autoSkipHandledFor)) {
                onAutoSkipped(state.episode.id)
                player.seekSafely(skip.targetMs, playback)
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
                Text(if (skip is SkipAction.SkipOp) "Lewati OP" else "+85 dtk")
            }
        }

        val error = state.error ?: playback.error
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
                    OutlinedButton(onClick = onBack) { Text("Kembali", color = Color.White) }
                    Button(onClick = onRetry) { Text("Coba lagi") }
                }
            }
        }

        if (playback.ended && !state.loading) {
            EndOverlay(
                next = state.next,
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(
                    state.anime?.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Episode ${state.episode.number}" + (state.source?.quality?.let { " • $it" } ?: ""),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (fullscreen) {
                IconButton(onClick = onLock) { Icon(Icons.Rounded.Lock, "Kunci layar", tint = Color.White) }
            }
            if (pipSupported) {
                IconButton(onClick = onEnterPip) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White) }
            }
            IconButton(onClick = { onOpenSettings(); onInteract() }) { Icon(Icons.Rounded.Settings, "Pengaturan video", tint = Color.White) }
        }

        // Center transport
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 36.dp else 20.dp),
        ) {
            TransportButton(Icons.Rounded.SkipPrevious, "Episode sebelumnya", enabled = state.previous != null, onClick = { onPrevious(); onInteract() })
            TransportButton(Icons.Rounded.FastRewind, "Mundur 10 detik", onClick = { player.seekSafely(player.effectivePosition(playback) - 10_000, playback); onInteract() })
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
                        if (playback.isPlaying) "Jeda" else "Putar",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            TransportButton(Icons.Rounded.FastForward, "Maju 10 detik", onClick = { player.seekSafely(player.effectivePosition(playback) + 10_000, playback); onInteract() })
            TransportButton(Icons.Rounded.SkipNext, "Episode berikutnya", enabled = state.next != null, onClick = { onNext(); onInteract() })
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
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullscreen) "Keluar layar penuh" else "Layar penuh",
                        tint = Color.White,
                    )
                }
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
                Text("Buka kunci")
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
            Text("${if (forward) "+" else "-"}$seconds detik", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
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
private fun EndOverlay(next: Episode?, autoPlay: Boolean, onNext: () -> Unit, onReplay: () -> Unit) {
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
            Text("Selanjutnya", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
            Text("Episode ${next.number}", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remaining > 0) {
                    OutlinedButton(onClick = { remaining = -1 }) { Text("Batal", color = Color.White) }
                }
                Button(onClick = onNext) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (remaining > 0) "Putar ($remaining)" else "Putar sekarang")
                }
            }
        } else {
            Text("Kamu sudah di episode terbaru", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onReplay) {
            Icon(Icons.Rounded.Replay, null, tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("Putar ulang", color = Color.White)
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
                    "Episode ${state.episode.number}",
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
                            state.previous?.let { "Ep ${it.number}" } ?: "Sebelumnya",
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
                            state.next?.let { "Ep ${it.number}" } ?: "Berikutnya",
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
                Text("Kualitas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.sources, key = { it.url }) { s ->
                        FilterChip(
                            selected = s.url == state.source?.url,
                            onClick = { onSelectSource(s) },
                            label = { Text(s.quality) },
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
                    "Episode",
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
                                NetImage(ep.thumbnail, Modifier.fillMaxSize())
                                if (current) {
                                    Box(
                                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("Diputar", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Text(
                                "Episode ${ep.number}",
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
    resizeMode: Int,
    positionMs: Long,
    opMark: OpMark?,
    sleep: SleepOption,
    onDismiss: () -> Unit,
    onSource: (StreamSource) -> Unit,
    onSpeed: (Float) -> Unit,
    onResize: (Int) -> Unit,
    onMarkOpStart: () -> Unit,
    onMarkOpEnd: () -> Unit,
    onClearOpMark: () -> Unit,
    onSleep: (SleepOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SheetTitle("Kualitas") }
            items(sources, key = { it.url }) { s ->
                ListItem(
                    headlineContent = { Text(s.quality) },
                    supportingContent = if (s.server.isNotBlank()) {
                        { Text("Server ${s.server}") }
                    } else null,
                    trailingContent = if (s.url == current?.url) {
                        { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSource(s) },
                )
            }
            item {
                SheetTitle("Kecepatan")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Speeds) { sp ->
                        FilterChip(selected = sp == speed, onClick = { onSpeed(sp) }, label = { Text("${sp}x".replace(".0x", "x")) })
                    }
                }
            }
            item {
                SheetTitle("Tampilan")
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        onClick = { onResize(AspectRatioFrameLayout.RESIZE_MODE_FIT) },
                        label = { Text("Pas layar") },
                    )
                    FilterChip(
                        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        onClick = { onResize(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) },
                        label = { Text("Isi layar") },
                        leadingIcon = { Icon(Icons.Rounded.AspectRatio, null, Modifier.size(16.dp)) },
                    )
                }
            }
            item {
                SheetTitle("Timer tidur")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SleepOption.entries) { option ->
                        FilterChip(selected = option == sleep, onClick = { onSleep(option) }, label = { Text(option.label) })
                    }
                }
            }
            item {
                SheetTitle("Opening (OP)")
                Text(
                    if (opMark?.isValid == true) {
                        "Tertanda ${formatDuration(opMark.startMs)} – ${formatDuration(opMark.endMs)} untuk semua episode anime ini."
                    } else {
                        "Tandai awal dan akhir OP sekali, tombol Lewati OP muncul di episode lain."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onMarkOpStart) { Text("Awal di ${formatDuration(positionMs)}") }
                    OutlinedButton(onClick = onMarkOpEnd) { Text("Akhir di ${formatDuration(positionMs)}") }
                }
                if (opMark != null) {
                    TextButton(onClick = onClearOpMark, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Hapus tanda OP") }
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
            Text("Tersimpan offline • ${download.entity.quality}")
        }

        download?.inProgress == true -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Downloading, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (download.state) {
                        DownloadState.QUEUED -> "Menunggu antrean…"
                        DownloadState.PAUSED -> "Dijeda, menunggu jaringan…"
                        else -> "Mengunduh ${(download.progress * 100).toInt()}% • ${formatBytes(download.downloadedBytes)}" +
                            if (download.totalBytes > 0) " / ${formatBytes(download.totalBytes)}" else ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete) { Text("Batal") }
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
                Text(if (download?.state == DownloadState.FAILED) "Download gagal, coba lagi" else "Download untuk ditonton offline")
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
            title = { Text("Hapus download?") },
            text = { Text("File episode ini akan dihapus dari perangkat.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
