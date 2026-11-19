package com.fuwanime.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fuwanime.data.StreamSource
import com.fuwanime.data.remote.YT_APP_UA
import com.fuwanime.data.remote.resolveTrailerSources
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Trailer playback on its own page, like every other stream: the user taps the trailer card on
 * the detail page, the app navigates here, and the trailer plays in the app's Media3
 * (ExoPlayer) player — fullscreen, with the normal transport controls. No WebView in the happy
 * path, no external app.
 *
 * The trailer URL is resolved to direct stream URLs first (see resolveTrailerSources). The
 * stream is fetched through OkHttpDataSource with the YouTube app's User-Agent — googlevideo
 * rejects ExoPlayer's default UA with a silent HTTP 403, which otherwise shows as a dead black
 * screen. Two guards turn any playback failure into the WebView embed fallback instead of a
 * black rectangle: a Player-error listener, and a watchdog that fires when the player never
 * reports READY/playing within 15 seconds.
 */
@Composable
fun TrailerPlayerRoute(
    url: String,
    title: String,
    thumbnail: String,
    onBack: () -> Unit,
) {
    val client = remember { OkHttpClient() }
    // null = still resolving; empty = nothing resolved (fall back to the embed ladder).
    var sources by remember(url) { mutableStateOf<List<StreamSource>?>(null) }
    var mediaFailed by remember(url) { mutableStateOf(false) }
    var confirmedPlaying by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) { sources = resolveTrailerSources(url, client) }

    val resolved = sources.orEmpty()
    val source = resolved.maxByOrNull { it.height }

    // Watchdog: a resolved URL that never reaches READY (403, throttling, unsupported codec...)
    // must not leave a black screen — hand the slot to the WebView embed ladder.
    LaunchedEffect(source) {
        if (source == null) return@LaunchedEffect
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
            when {
                sources == null -> CircularProgressIndicator(Modifier.size(36.dp), color = Color.White)
                source != null && !mediaFailed -> TrailerExoPlayer(
                    url = source.url,
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
private fun TrailerExoPlayer(url: String, onConfirmed: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        val dataSource = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent(YT_APP_UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/"))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
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
