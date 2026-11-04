package com.fuwanime.ui.player

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.local.VideoResize

/** What the player does when the activity stops (screen off, home, PiP window closed). */
enum class StopAction { PAUSE, KEEP_PLAYING }

fun stopAction(inPip: Boolean, backgroundAudio: Boolean): StopAction = TODO()

/** PiP aspect ratio as (width, height); Android rejects ratios outside 1:2.39 .. 2.39:1. */
fun clampPipAspect(width: Int, height: Int): Pair<Int, Int> = TODO()

/** New resize mode for a two-finger pinch of cumulative [scale], or null when it should not change. */
fun pinchResize(current: VideoResize, scale: Float): VideoResize? = TODO()

fun isFullscreen(landscape: Boolean, manual: Boolean, inPip: Boolean): Boolean = TODO()

/** What the media notification, lock screen and Bluetooth devices show. */
data class NowPlaying(val title: String, val artist: String, val subtitle: String, val artworkUrl: String)

fun nowPlaying(episode: Episode, anime: Anime?): NowPlaying = TODO()
