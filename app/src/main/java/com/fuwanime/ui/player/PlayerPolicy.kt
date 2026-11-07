package com.fuwanime.ui.player

import android.content.res.Resources
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.local.VideoResize

/** What the player does when the activity stops (screen off, home, PiP window closed). */
enum class StopAction { PAUSE, KEEP_PLAYING }

// Closing the PiP window (screen on) is an explicit stop, so it pauses even with background audio on.
fun stopAction(inPip: Boolean, screenOn: Boolean, backgroundAudio: Boolean): StopAction = when {
    inPip && screenOn -> StopAction.PAUSE
    backgroundAudio -> StopAction.KEEP_PLAYING
    else -> StopAction.PAUSE
}

/** PiP aspect ratio as (width, height); Android rejects ratios outside 1:2.39 .. 2.39:1. */
fun clampPipAspect(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    val r = width.toFloat() / height
    return when {
        r > 2.39f -> 239 to 100
        r < 100f / 239f -> 100 to 239
        else -> width to height
    }
}

/** New resize mode for a two-finger pinch of cumulative [scale], or null when it should not change. */
fun pinchResize(current: VideoResize, scale: Float): VideoResize? = when {
    scale >= 1.15f && current != VideoResize.ZOOM -> VideoResize.ZOOM
    scale <= 0.87f && current != VideoResize.FIT -> VideoResize.FIT
    else -> null
}

/**
 * [manual] is the user's last fullscreen choice since the last rotation (null: follow orientation). It keeps the
 * choice on devices that ignore orientation requests (Android 16, sw >= 600dp).
 */
fun isFullscreen(landscape: Boolean, manual: Boolean?, inPip: Boolean): Boolean = (manual ?: landscape) && !inPip

/** What the media notification, lock screen and Bluetooth devices show. */
data class NowPlaying(val title: String, val artist: String, val subtitle: String, val artworkUrl: String)

fun nowPlaying(res: Resources, episode: Episode, anime: Anime?): NowPlaying {
    val animeTitle = anime?.title.orEmpty()
    return NowPlaying(
        title = episode.title.ifBlank { animeTitle },
        artist = animeTitle,
        subtitle = if (episode.number.isBlank()) "" else res.getString(R.string.watch_episode_label, episode.number),
        artworkUrl = episode.thumbnail.ifBlank { anime?.poster.orEmpty() },
    )
}
