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
    private val holder get() = (application as FuwaApp).container.playbackHolder

    override fun onCreate() {
        super.onCreate()
        holder.service = this
        ensureSession()
    }

    /**
     * Wraps the holder's current player. Null when no player screen is open (system restart, a stale media-button
     * intent): Media3's onStartCommand then stops the service safely, posting a foreground notification first if it
     * was started as a foreground service. A plain stopSelf() here would crash with the FGS timeout.
     */
    private fun ensureSession(): MediaSession? {
        session?.let { return it }
        val player = holder.player ?: return null
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return MediaSession.Builder(this, player).setSessionActivity(open).build().also { session = it }
    }

    // A service instance can outlive one player screen (a late unbind); the next screen's controller gets a fresh session.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = ensureSession()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart after the process was killed has nothing to play.
        if (intent == null && ensureSession() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Swiping the app away from Recents ends playback, even with background audio on.
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.pause()
        stopSelf()
    }

    /** Detaches the session from the player; the holder calls this before it stops and releases the player. */
    fun releaseSession() {
        session?.release()
        session = null
    }

    override fun onDestroy() {
        // The holder owns the player; only the session is released here.
        releaseSession()
        if (holder.service === this) holder.service = null
        super.onDestroy()
    }
}
