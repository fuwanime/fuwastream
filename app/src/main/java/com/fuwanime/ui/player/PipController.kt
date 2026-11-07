package com.fuwanime.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.fuwanime.R
import com.fuwanime.playback.PlaybackService

/** Bridges Picture-in-Picture between the activity and the player composable. */
class PipController(private val activity: ComponentActivity) {

    private val _isInPip = mutableStateOf(false)
    val isInPip: State<Boolean> = _isInPip

    /** True from the moment the system starts shrinking into PiP (API 35+), so controls can hide early. */
    private val _isEnteringPip = mutableStateOf(false)
    val isEnteringPip: State<Boolean> = _isEnteringPip

    val isSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private var autoEnter = false
    private var isPlaying = false
    private var aspect = Rational(16, 9)

    private val listener = Consumer<PictureInPictureModeChangedInfo> {
        _isInPip.value = it.isInPictureInPictureMode
        _isEnteringPip.value = false
    }

    init {
        activity.addOnPictureInPictureModeChangedListener(listener)
    }

    /** Whether leaving the app (home gesture) should shrink the video into a floating window. */
    fun setAutoEnter(enabled: Boolean, width: Int = 16, height: Int = 9, isPlaying: Boolean = false) {
        autoEnter = enabled && isSupported
        this.isPlaying = isPlaying
        val (w, h) = clampPipAspect(width, height)
        aspect = Rational(w, h)
        if (isSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { activity.setPictureInPictureParams(buildParams()) }
        }
    }

    fun enter(): Boolean {
        if (!isSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching { activity.enterPictureInPictureMode(buildParams()) }.getOrDefault(false)
    }

    /** Pre-Android 12 has no auto-enter flag, so the activity forwards onUserLeaveHint here. */
    fun onUserLeaveHint() {
        if (autoEnter && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enter()
    }

    fun onPipUiState(transitioning: Boolean) {
        _isEnteringPip.value = transitioning
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(
                listOf(
                    action(1, R.drawable.ic_pip_rewind, activity.getString(R.string.watch_rewind_10), KeyEvent.KEYCODE_MEDIA_REWIND),
                    if (isPlaying) action(2, R.drawable.ic_pip_pause, activity.getString(R.string.watch_pause), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    else action(2, R.drawable.ic_pip_play, activity.getString(R.string.watch_play), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                    action(3, R.drawable.ic_pip_forward, activity.getString(R.string.watch_forward_10), KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
                ),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** A media-button intent to [PlaybackService]; Media3 routes it to the session's player. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(requestCode: Int, @DrawableRes icon: Int, title: String, keyCode: Int): RemoteAction {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(activity, PlaybackService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val pending = PendingIntent.getService(
            activity, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pending)
    }
}

val LocalPipController = staticCompositionLocalOf<PipController?> { null }
