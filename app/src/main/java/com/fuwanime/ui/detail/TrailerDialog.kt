package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

/**
 * In-app trailer playback. Uses the system WebView (part of Android itself, NOT Google Play
 * services), so the embed works on phones without GMS. No new dependency — WebView is already
 * available; Media3/ExoPlayer cannot open YouTube/Dailymotion URLs directly, so an embed page
 * is the dependency-free in-app option.
 *
 * YouTube: the standard www.youtube.com/embed player. (The youtube-nocookie privacy embed is
 * rejected by YouTube inside WebViews with player-configuration "Error 153", which offers the
 * user a "Watch on YouTube" button — exactly the redirect we want to avoid.) Minimal safe
 * params: autoplay + inline playback. Dailymotion: their iframe player embed.
 *
 * Everything stays inside the dialog: external navigations (youtube.com/watch, share links,
 * the YouTube app) are swallowed by [TrailerWebViewClient], so the trailer can never bounce
 * the user out to YouTube.
 */
@Composable
fun TrailerPlayerDialog(url: String, onDismiss: () -> Unit) {
    val embed = embedUrl(url) ?: url
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { ctx ->
                WebView(ctx).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = TrailerWebViewClient()
                    // onShowCustomView hosts the player's real fullscreen surface; on hiding,
                    // restore the previous system UI and dialog window flags.
                    webChromeClient = TrailerChromeClient(ctx)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(embed)
                }
            },
            update = { /* the URL never changes while the dialog is open */ },
        )
    }
}

/**
 * Keeps playback in-app: any navigation away from the embed player (the "Watch on YouTube"
 * button, video-page links, app deep links) is cancelled instead of opened.
 */
internal class TrailerWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host?.removePrefix("www.").orEmpty()
        val allowed = host == "youtube.com" || host == "youtube-nocookie.com" ||
            host == "dailymotion.com" || host == "dm.com"
        return !allowed // true = WebView must not load it; the external open never happens
    }
}

/** Fullscreen support for the embed player's fullscreen button. */
internal class TrailerChromeClient(private val context: android.content.Context) : WebChromeClient() {
    private var customView: android.view.View? = null

    override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        (context as? Activity)?.window?.let { win ->
            win.addContentView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            win.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } ?: run {
            callback.onCustomViewHidden()
            customView = null
        }
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        customView = null
        (view.parent as? ViewGroup)?.removeView(view)
        (context as? Activity)?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
    }
}

/** Converts a YouTube watch/short URL or a Dailymotion video URL into an embeddable player URL; null when unsupported. */
internal fun embedUrl(url: String): String? {
    val clean = url.trim()
    return runCatching {
        val uri = URI(clean)
        when (uri.host?.removePrefix("www.").orEmpty()) {
            "youtube.com", "m.youtube.com" -> {
                val id = uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { q ->
                    q.split('&').firstOrNull { it.startsWith("v=") }?.substring(2)
                }
                if (!id.isNullOrBlank()) youTubeEmbed(id) else null
            }
            "youtu.be" -> {
                val id = uri.path?.trim('/')
                if (!id.isNullOrBlank()) youTubeEmbed(id) else null
            }
            "dailymotion.com" -> {
                val id = uri.path?.trim('/')?.substringAfterLast('/')
                if (!id.isNullOrBlank()) "https://www.dailymotion.com/embed/video/$id?autoplay=1" else null
            }
            else -> null
        }
    }.getOrNull()
}

/** The standard YouTube embed player; nocookie is avoided because it errors (153) in WebViews. */
internal fun youTubeEmbed(id: String): String =
    "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0"
