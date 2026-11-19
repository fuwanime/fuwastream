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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fuwanime.data.StreamSource
import com.fuwanime.data.remote.resolveTrailerSources
import okhttp3.OkHttpClient

/**
 * Trailer playback on its own page, like every other stream: the user taps the trailer card on
 * the detail page, the app navigates here, and the trailer plays in the app's Media3
 * (ExoPlayer) player — fullscreen, with the normal transport controls. No WebView in the happy
 * path, no external app.
 *
 * The trailer URL is resolved to direct stream URLs first (see resolveTrailerSources). When the
 * resolver comes up empty — YouTube bot-checks datacenter IPs, Dailymotion occasionally hides
 * its metadata — the page falls back to the WebView embed ladder in place, and only if that
 * also fails does it offer to open the trailer externally.
 */
@Composable
fun TrailerPlayerRoute(
    url: String,
    title: String,
    thumbnail: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { OkHttpClient() }
    // null = still resolving; empty = nothing resolved (fall back to the embed ladder).
    var sources by remember(url) { mutableStateOf<List<StreamSource>?>(null) }
    LaunchedEffect(url) { sources = resolveTrailerSources(url, client) }

    val resolved = sources.orEmpty()
    val source = resolved.maxByOrNull { it.height }

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
                source != null -> TrailerExoPlayer(source.url)
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

/** Media3 player for the resolved trailer stream: controllers on, fit-to-screen, released on dispose. */
@Composable
private fun TrailerExoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
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
