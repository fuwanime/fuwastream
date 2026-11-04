package com.fuwanime.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity

/** Exposes the player screen's [PlaybackHolder.player] to the system: media notification, lock screen, headset. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Restarted by the system with no player screen open: nothing to control.
        val player = (application as FuwaApp).container.playbackHolder.player ?: return stopSelf()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping the app away from Recents ends playback, even with background audio on.
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The holder owns the player; only the session is released here.
        session?.release()
        session = null
        super.onDestroy()
    }
}
