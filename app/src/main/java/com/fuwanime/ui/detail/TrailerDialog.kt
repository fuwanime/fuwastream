package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
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
 * YouTube: the youtube-nocookie privacy-enhanced embed, autoplay + inline (fullscreen button
 * available inside the player). Dailymotion: their iframe player embed.
 */
@Composable
fun TrailerPlayerDialog(url: String, onDismiss: () -> Unit) {
    val embed = embedUrl(url)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { context ->
                WebView(context).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // Unsupported or malformed URL: fall back to the original page.
                    loadUrl(embed ?: url)
                }
            },
            update = { /* the URL never changes while the dialog is open */ },
        )
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
                if (!id.isNullOrBlank()) "https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0" else null
            }
            "youtu.be" -> {
                val id = uri.path?.trim('/')
                if (!id.isNullOrBlank()) "https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&rel=0" else null
            }
            "dailymotion.com" -> {
                val id = uri.path?.trim('/')?.substringAfterLast('/')
                if (!id.isNullOrBlank()) "https://www.dailymotion.com/embed/video/$id?autoplay=1" else null
            }
            else -> null
        }
    }.getOrNull()
}
