package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.fuwanime.ui.components.NetImage
import java.net.URI

/**
 * Inline trailer player for the detail page: shows the AniList poster frame with a play badge
 * and swaps to the embedded player *in place* when tapped — no dialog, no navigation, the
 * trailer keeps playing while the user scrolls the detail content around it.
 *
 * Uses the system WebView (part of Android itself, NOT Google Play services), so it works on
 * phones without GMS. No new dependency; Media3/ExoPlayer cannot open YouTube/Dailymotion
 * URLs directly, so an embedded player page is the dependency-free in-app option.
 *
 * WebView specifics that make YouTube accept the embed (without these the player fails with
 * "Error 153" / "Terjadi error pada konfigurasi pemutar video"):
 * - The embed must come from https://www.youtube.com — the youtube-nocookie.com variant is
 *   rejected in WebViews by the same player-configuration check.
 * - The page is loaded via loadDataWithBaseURL() with the player's own domain as base URL,
 *   giving the player iframe a real origin instead of a bare-WebView page with no referer.
 * - A desktop-style User-Agent avoids the mobile-WebV path that also triggers 153.
 * - Navigations away from the player domains are cancelled, so a "Watch on YouTube" button
 *   or a share link can never bounce the user out of the app.
 */
@Composable
fun InlineTrailerPlayer(
    thumbnail: String,
    fallbackThumbnail: String,
    trailerUrl: String,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
    ) {
        if (!playing) {
            NetImage(
                thumbnail.ifBlank { fallbackThumbnail },
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { playing = true },
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(34.dp), tint = Color.White)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // Desktop UA: the mobile-WebV player path rejects third-party embeds (Error 153).
                        settings.userAgentString =
                            "Mozilla/5.0 (X11; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // Cancel anything that is not the player itself — this is what
                                // keeps playback strictly in-app ("Watch on YouTube" included).
                                val host = request.url.host?.removePrefix("www.").orEmpty()
                                return host !in setOf("youtube.com", "youtube-nocookie.com", "dailymotion.com", "dm.com")
                            }
                        }
                        webChromeClient = WebChromeClient()
                        setBackgroundColor(android.graphics.Color.BLACK)
                        val page = embedPage(trailerUrl)
                        if (page != null) {
                            // Base URL = player origin; without it YouTube reports Error 153.
                            loadDataWithBaseURL(page.baseUrl, page.html, "text/html", "utf-8", null)
                        } else {
                            loadUrl(trailerUrl)
                        }
                    }
                },
                update = { /* the URL never changes while the card is playing */ },
            )
        }
    }
}

/** An HTML wrapper page plus the base URL that gives the embedded player a valid origin. */
internal class EmbedPage(val baseUrl: String, val html: String)

private fun playerHtml(embedSrc: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="referrer" content="origin">
<style>
  html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
  iframe { width: 100%; height: 100%; border: 0; }
</style>
</head>
<body>
<iframe src="$embedSrc"
        allow="autoplay; encrypted-media; picture-in-picture"
        allowfullscreen frameborder="0"></iframe>
</body>
</html>
""".trimIndent()

/** Converts a YouTube watch/short URL or a Dailymotion video URL into an embed page; null when unsupported. */
internal fun embedPage(url: String): EmbedPage? {
    val clean = url.trim()
    return runCatching {
        val uri = URI(clean)
        when (uri.host?.removePrefix("www.").orEmpty()) {
            "youtube.com", "m.youtube.com", "youtu.be" -> {
                val id = if (uri.host.orEmpty().endsWith("youtu.be")) {
                    uri.path?.trim('/')
                } else {
                    uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { q ->
                        q.split('&').firstOrNull { it.startsWith("v=") }?.substring(2)?.takeIf(String::isNotBlank)
                    } ?: uri.path?.takeIf { it.startsWith("/shorts/") || it.startsWith("/embed/") }?.trim('/')
                }
                if (id.isNullOrBlank()) return@runCatching null
                // www.youtube.com — the youtube-nocookie.com variant is rejected in WebViews (Error 153).
                // "origin" posts the iframe's real origin (from the base URL) back to YouTube.
                val origin = URI("https://www.youtube.com").toASCIIString()
                val src = "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&origin=$origin"
                EmbedPage("https://www.youtube.com", playerHtml(src))
            }
            "dailymotion.com" -> {
                val id = uri.path?.trim('/')?.substringAfterLast('/')
                if (id.isNullOrBlank()) return@runCatching null
                val src = "https://www.dailymotion.com/embed/video/$id?autoplay=1"
                EmbedPage("https://www.dailymotion.com", playerHtml(src))
            }
            else -> null
        }
    }.getOrNull()
}
