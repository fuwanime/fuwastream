package com.fuwanime.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Resources
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.local.VideoResize
import com.fuwanime.util.formatDuration

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
 * [twoPaneCapable] tablets get the two-pane layout in landscape instead of auto-fullscreen; only a manual
 * fullscreen request (the fullscreen button) takes over the whole window there.
 */
fun isFullscreen(landscape: Boolean, manual: Boolean?, inPip: Boolean, twoPaneCapable: Boolean = false): Boolean =
    (manual ?: (landscape && !twoPaneCapable)) && !inPip

/** Shortest window side at which the device counts as a tablet (same 600dp threshold as the nav rail). */
const val TABLET_MIN_SHORTEST_WIDTH_DP = 600

/** Expanded window width (Material 3) at and above which landscape gets a permanent side panel. */
const val TWO_PANE_MIN_WIDTH_DP = 840

fun isTabletWindow(widthDp: Int, heightDp: Int): Boolean =
    minOf(widthDp, heightDp) >= TABLET_MIN_SHORTEST_WIDTH_DP

/** Two-pane player (video + episode/info panel): landscape tablet at expanded width only; phones keep one pane. */
fun useTwoPanePlayer(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= TWO_PANE_MIN_WIDTH_DP && widthDp > heightDp && isTabletWindow(widthDp, heightDp)

/**
 * Orientation to request when leaving fullscreen: phones return to portrait as before; on a tablet the request is
 * cleared so the device keeps its natural (landscape) orientation and the two-pane layout instead of being forced
 * portrait.
 */
fun exitFullscreenRequestedOrientation(tablet: Boolean): Int =
    if (tablet) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

/** TalkBack state for the seek slider: current position out of the total, e.g. "01:23 dari 24:10".
 *  With an unknown total there is nothing to be "out of", so the position reads zero too. */
fun seekProgressDescription(res: Resources, positionMs: Long, durationMs: Long): String {
    val position = if (durationMs <= 0) 0L else positionMs.coerceAtLeast(0)
    return res.getString(R.string.watch_seek_position, formatDuration(position), formatDuration(durationMs))
}

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
