package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
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
 * YouTube's embed behaves differently depending on how the WebView presents it (referer,
 * origin, JS-API handshake, desktop vs mobile UA) and different devices reject different
 * configurations ("Error 153", "Error 152-4", dead black player). Since no single recipe
 * works everywhere, the player is loaded through a retry ladder — on a player error the next
 * strategy is tried automatically, and the "open in YouTube" fallback is only shown once all
 * of them fail (or the video is genuinely embed-disabled: codes 101/150).
 */
private const val YT_BASE = "https://www.youtube.com"

/** Chrome desktop UA — avoids the mobile-WebV embed path that YouTube rejects. */
private const val UA_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** Chrome mobile UA — the mobile embed path works on some devices where the desktop one fails. */
private const val UA_MOBILE =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** The embed URL (already embed-specific) plus how the WebView should load it. */
internal class EmbedAttempt(val userAgent: String, val baseUrl: String?, val url: String, val html: String?)

private fun playerHtml(embedSrc: String, withJsApi: Boolean): String = buildString {
    append("""<!DOCTYPE html><html><head><meta charset="utf-8">""")
    append("""<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">""")
    append("""<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}""")
    append("""iframe{width:100%;height:100%;border:0}</style></head><body>""")
    append("""<iframe id="p" src="$embedSrc" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen frameborder="0"></iframe>""")
    if (withJsApi) {
        // The enablejsapi player waits for the host's "listening" handshake before it plays.
        append("""<script>
          var p = document.getElementById('p');
          p.onload = function () { p.contentWindow.postMessage(JSON.stringify({event: 'listening'}), '*'); };
          window.addEventListener('message', function (e) {
            try {
              var d = JSON.parse(e.data);
              if (d && (d.event === 'onError' || d.event === 'error')) {
                var code = (typeof d.info === 'number') ? d.info : ((d.info && d.info.errorCode) || 1);
                window.AndroidHook.playerError(code);
              }
            } catch (_) {}
          });
        </script>""")
    }
    append("</body></html>")
}

/** Builds the retry ladder for a trailer URL; empty list when the URL is not YouTube/Dailymotion. */
internal fun embedAttempts(url: String): List<EmbedAttempt> {
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
                if (id.isNullOrBlank()) return@runCatching emptyList()
                // www.youtube.com — the youtube-nocookie.com variant is rejected in WebViews (Error 153).
                listOf(
                    // 1: JS-API embed with origin + listening handshake (most desktop-like).
                    EmbedAttempt(
                        UA_DESKTOP, YT_BASE,
                        "$YT_BASE/embed/$id?autoplay=1&playsinline=1&rel=0&enablejsapi=1&origin=$YT_BASE",
                        playerHtml("$YT_BASE/embed/$id?autoplay=1&playsinline=1&rel=0&enablejsapi=1&origin=$YT_BASE", withJsApi = true),
                    ),
                    // 2: plain mobile embed without JS-API — works on many Android WebViews.
                    EmbedAttempt(
                        UA_MOBILE, YT_BASE,
                        "$YT_BASE/embed/$id?autoplay=1&playsinline=1&rel=0",
                        playerHtml("$YT_BASE/embed/$id?autoplay=1&playsinline=1&rel=0", withJsApi = false),
                    ),
                    // 3: direct load with a Referer header — the classic Error-153 fix.
                    EmbedAttempt(UA_MOBILE, null, "$YT_BASE/embed/$id?autoplay=1&playsinline=1&rel=0", null),
                )
            }
            "dailymotion.com" -> {
                val id = uri.path?.trim('/')?.substringAfterLast('/')
                if (id.isNullOrBlank()) return@runCatching emptyList()
                val base = "https://www.dailymotion.com"
                listOf(EmbedAttempt(UA_DESKTOP, base, "$base/embed/video/$id?autoplay=1", playerHtml("$base/embed/video/$id?autoplay=1", withJsApi = false)))
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

@Composable
fun InlineTrailerPlayer(
    thumbnail: String,
    fallbackThumbnail: String,
    trailerUrl: String,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    // -1 = still trying; 101/150 = the uploader disabled embedding (final fallback); other
    // codes advance the retry ladder until it is exhausted.
    var lastError by remember { mutableIntStateOf(-1) }
    var strategy by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val attempts = remember(trailerUrl) { embedAttempts(trailerUrl) }

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
                            lastError = -1
                            strategy = 0
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
            (strategy >= attempts.size || lastError == 101 || lastError == 150) -> {
                // Every strategy failed, or the video cannot be embedded at all.
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
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, trailerUrl.toUri())) }
                    }) {
                        Text("Buka di YouTube")
                    }
                }
            }
            else -> {
                // Loading a page + autoplay takes a moment; a spinner beats a black rectangle.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = Color.White)
                }
                // key forces a fresh WebView per strategy — settings/UA cannot change in place.
                androidx.compose.runtime.key(strategy) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val attempt = attempts[strategy]
                            val handler = Handler(Looper.getMainLooper())
                            WebView(ctx).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.userAgentString = attempt.userAgent
                                addJavascriptInterface(
                                    PlayerErrorHook { code ->
                                        handler.post {
                                            if (code == 101 || code == 150) {
                                                // Uploader disabled embedding (or video gone): no
                                                // strategy will ever help — show the final fallback.
                                                lastError = code
                                            } else if (strategy < attempts.size - 1) {
                                                // Config-type error (152/153/2/5/...): try the next
                                                // loading strategy; key(strategy) recreates the WebView.
                                                strategy++
                                            } else {
                                                lastError = code
                                            }
                                        }
                                    },
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
                                if (attempt.html != null && attempt.baseUrl != null) {
                                    // Base URL = player origin: the iframe shares it with the player,
                                    // which the origin= parameter (and the embed check) requires.
                                    loadDataWithBaseURL(attempt.baseUrl, attempt.html, "text/html", "utf-8", null)
                                } else {
                                    loadUrl(attempt.url, mapOf("Referer" to "$YT_BASE/"))
                                }
                            }
                        },
                        update = { /* recreated per strategy via key */ },
                    )
                }
            }
        }
    }
}

/** JS bridge: the wrapper page forwards the YouTube player's onError event to this hook. */
private class PlayerErrorHook(private val onError: (Int) -> Unit) {
    @JavascriptInterface
    fun playerError(code: Int) = onError(code)
}
