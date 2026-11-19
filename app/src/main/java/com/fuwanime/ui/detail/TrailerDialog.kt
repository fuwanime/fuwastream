package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.fuwanime.ui.components.NetImage
import java.net.URI

/**
 * Inline trailer player for the detail page: shows the AniList poster frame with a play badge
 * and swaps to the embedded player *in place* when tapped — no dialog, no navigation.
 *
 * Uses the system WebView (part of Android itself, NOT Google Play services), so it works on
 * phones without GMS. No new dependency; Media3/ExoPlayer cannot open YouTube/Dailymotion
 * URLs directly, so an embedded player page is the dependency-free in-app option.
 *
 * WebView specifics that make YouTube accept the embed:
 * - The iframe embed comes from https://www.youtube.com with enablejsapi + origin. A bare
 *   WebView page has no referer and no JS-API handshake, which makes the desktop player
 *   reject playback ("Error 153", later "Error 152-4"). The wrapper page below performs the
 *   standard postMessage handshake ("listening") right after the iframe loads.
 * - The wrapper itself is loaded via loadDataWithBaseURL() with the player's domain as base
 *   URL, so the iframe shares a real origin with the player (required by the origin param).
 * - A desktop-style User-Agent avoids the mobile-WebV path that also triggers errors.
 * - Only MAIN-frame navigations away from the player domains are cancelled (the player's
 *   iframe itself hops through google.com/googlevideo.com while streaming and must be
 *   allowed), so a "Watch on YouTube" jump can never bounce the user out of the app.
 * - Player error events (e.g. a video whose owner disabled embedding) are surfaced through a
 *   JS bridge and shown as a clean fallback with an "open in browser" button.
 */
@Composable
fun InlineTrailerPlayer(
    thumbnail: String,
    fallbackThumbnail: String,
    trailerUrl: String,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    var playerError by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
    ) {
        when {
            !playing -> {
                NetImage(
                    thumbnail.ifBlank { fallbackThumbnail },
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable {
                            playerError = -1
                            playing = true
                        },
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
            }
            playerError >= 0 -> {
                // The player itself reported it cannot play this video (embed disabled, removed,
                // region-locked...). Clean fallback instead of YouTube's raw error screen.
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Trailer tidak dapat diputar di dalam aplikasi",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        "Coba buka di browser atau aplikasi YouTube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, trailerUrl.toUri())) }
                    }) {
                        Text("Buka di YouTube")
                    }
                }
            }
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val handler = Handler(Looper.getMainLooper())
                        WebView(ctx).apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Desktop Chrome UA: the mobile-WebV player path rejects third-party embeds.
                            settings.userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            addJavascriptInterface(
                                PlayerErrorHook { code -> handler.post { playerError = code } },
                                "AndroidHook",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    // Only MAIN-frame navigations are fenced: the player iframe
                                    // itself makes sub-frame hops through google.com/googlevideo.com
                                    // while streaming, and cancelling those would leave a dead
                                    // player. A main-frame jump to YouTube's app/browser links is
                                    // cancelled, so playback can never leave the app.
                                    if (!request.isForMainFrame) return false
                                    val host = request.url.host?.removePrefix("www.").orEmpty()
                                    return host !in setOf("youtube.com", "youtube-nocookie.com", "dailymotion.com", "dm.com")
                                }
                            }
                            webChromeClient = WebChromeClient()
                            setBackgroundColor(android.graphics.Color.BLACK)
                            val page = embedPage(trailerUrl)
                            if (page != null) {
                                // Base URL = player origin: the iframe shares it with the player,
                                // which the origin= parameter (and the embed check) requires.
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
}

/** JS bridge: the wrapper page forwards the YouTube player's onError event to this hook. */
private class PlayerErrorHook(private val onError: (Int) -> Unit) {
    @JavascriptInterface
    fun playerError(code: Int) = onError(code)
}

/** An HTML wrapper page plus the base URL that gives the embedded player a valid origin. */
internal class EmbedPage(val baseUrl: String, val html: String)

private fun playerHtml(embedSrc: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
  iframe { width: 100%; height: 100%; border: 0; }
</style>
</head>
<body>
<iframe id="p" src="$embedSrc" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen frameborder="0"></iframe>
<script>
  var p = document.getElementById('p');
  // The enablejsapi player waits for the host's "listening" handshake; without it playback
  // is refused with "Kode error 152-4".
  p.onload = function () {
    p.contentWindow.postMessage(JSON.stringify({event: 'listening'}), '*');
  };
  // Surface player errors (embed disabled, removed video, ...) to the app.
  window.addEventListener('message', function (e) {
    try {
      var d = JSON.parse(e.data);
      if (d && (d.event === 'onError' || d.event === 'error')) {
        var code = (typeof d.info === 'number') ? d.info : ((d.info && d.info.errorCode) || 1);
        window.AndroidHook.playerError(code);
      }
    } catch (_) {}
  });
</script>
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
                // enablejsapi + origin: the player performs the postMessage handshake with its
                // embedding page; without it the desktop player refuses playback (152-4).
                val baseUrl = "https://www.youtube.com"
                val src = "$baseUrl/embed/$id?autoplay=1&playsinline=1&rel=0&enablejsapi=1&origin=$baseUrl"
                EmbedPage(baseUrl, playerHtml(src))
            }
            "dailymotion.com" -> {
                val id = uri.path?.trim('/')?.substringAfterLast('/')
                if (id.isNullOrBlank()) return@runCatching null
                val baseUrl = "https://www.dailymotion.com"
                EmbedPage(baseUrl, playerHtml("$baseUrl/embed/video/$id?autoplay=1"))
            }
            else -> null
        }
    }.getOrNull()
}
