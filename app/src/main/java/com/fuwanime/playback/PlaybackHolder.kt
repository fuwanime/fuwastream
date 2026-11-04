package com.fuwanime.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fuwanime.data.remote.AnimeApiService
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient

/**
 * Owns the one [ExoPlayer] the player screen uses. The screen gets it synchronously; [PlaybackService] wraps the
 * same instance in a MediaSession for the notification, lock screen, headset and PiP actions. Main thread only.
 */
class PlaybackHolder(private val app: Application, private val videoHttpClient: OkHttpClient) {

    var player: ExoPlayer? = null
        private set
    /** Set by [PlaybackService] while it runs, so the session can be released before the player. */
    internal var service: PlaybackService? = null
    private var controller: ListenableFuture<MediaController>? = null

    fun acquire(): ExoPlayer {
        releaseCurrent()
        val p = buildPlayer()
        player = p
        // A connected controller is what starts the service and lets Media3 manage the foreground notification.
        controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync()
            .also { f -> f.addListener({ runCatching { f.get() }.onFailure { Log.w(TAG, "controller failed", it) } }, app.mainExecutor) }
        return p
    }

    /** Releases [p]; a stale screen releasing after a newer one acquired never touches the newer player. */
    fun release(p: ExoPlayer) {
        if (p === player) releaseCurrent() else p.release()
    }

    private fun releaseCurrent() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        // Release the session first: otherwise stopping the player makes Media3 re-post the notification with
        // startForegroundService after stopService, recreating the service without a player (FGS-timeout crash).
        service?.releaseSession()
        player?.let {
            it.stop()
            it.release()
        }
        if (player != null) app.stopService(Intent(app, PlaybackService::class.java))
        player = null
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(videoHttpClient).setUserAgent(AnimeApiService.USER_AGENT)
        // DefaultDataSource routes file:// (offline downloads) locally and everything else through OkHttp.
        val dataSource = DefaultDataSource.Factory(app, http)
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            // Request audio focus: music/podcast from another app pauses instead of overlapping.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            // Keeps Wi-Fi and CPU awake so streaming survives the screen turning off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private companion object {
        const val TAG = "FuwaPlayback"
    }
}
