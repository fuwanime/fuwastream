package com.fuwanime.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fuwanime.data.remote.TrailerStreams
import com.fuwanime.data.remote.resolveTrailerStreams
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Trailer playback on its own page, like every other stream: the user taps the trailer card on
 * the detail page, the app navigates here, and the trailer plays in the app's Media3
 * (ExoPlayer) player — fullscreen, with the normal transport controls. No WebView in the happy
 * path, no external app.
 *
 * Three layers, each handing over to the next on failure:
 * 1. Direct stream from the resolver (YouTube ANDROID_VR / Dailymotion), played through
 *    OkHttpDataSource with the *requesting client's* User-Agent — googlevideo ties the URL to
 *    that UA and rejects ExoPlayer's default one with a silent 403 (dead black screen).
 *    ANDROID_VR returns split video/audio, so they are merged with MergingMediaSource.
 * 2. If resolution fails, or the player errors / never reaches READY within 15 s, the
 *    watch-page/embed WebView ladder takes over in the same slot (see InlineTrailerPlayer).
 * 3. Only if that also fails: the "open in YouTube" fallback.
 */
@Composable
fun TrailerPlayerRoute(
    url: String,
    title: String,
    thumbnail: String,
    onBack: () -> Unit,
) {
    val client = remember { OkHttpClient() }
    // null = still resolving; null after the effect means "nothing resolved".
    var streams by remember(url) { mutableStateOf<TrailerStreams?>(null) }
    var resolved by remember(url) { mutableStateOf(false) }
    var mediaFailed by remember(url) { mutableStateOf(false) }
    var confirmedPlaying by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        streams = resolveTrailerStreams(url, client)
        resolved = true
    }

    // Watchdog: a resolved URL that never reaches READY (403, throttling, unsupported codec...)
    // must not leave a black screen — hand the slot to the WebView ladder.
    LaunchedEffect(streams) {
        val s = streams ?: return@LaunchedEffect
        confirmedPlaying = false
        delay(15_000)
        if (!confirmedPlaying) mediaFailed = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                title.ifBlank { "Trailer" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val s = streams
            when {
                !resolved -> CircularProgressIndicator(Modifier.size(36.dp), color = Color.White)
                s != null && !mediaFailed -> TrailerExoPlayer(
                    streams = s,
                    onConfirmed = { confirmedPlaying = true },
                    onFailed = { mediaFailed = true },
                )
                else -> InlineTrailerPlayer(
                    thumbnail = thumbnail,
                    fallbackThumbnail = thumbnail,
                    trailerUrl = url,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Media3 player for the resolved trailer stream; reports READY/playing and errors back up. */
@Composable
private fun TrailerExoPlayer(streams: TrailerStreams, onConfirmed: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(streams) {
        val dataSource = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent(streams.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/"))
        val factory = ProgressiveMediaSource.Factory(dataSource)
        val mediaSource = streams.audio?.let { audio ->
            // ANDROID_VR hands out video-only + audio-only streams; play them as one item.
            MergingMediaSource(
                factory.createMediaSource(MediaItem.fromUri(streams.video)),
                factory.createMediaSource(MediaItem.fromUri(audio)),
            )
        } ?: factory.createMediaSource(MediaItem.fromUri(streams.video))
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) onConfirmed()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) onConfirmed()
                    }

                    override fun onPlayerError(error: PlaybackException) = onFailed()
                })
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
    )
}
