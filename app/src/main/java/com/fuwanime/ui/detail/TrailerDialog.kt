package com.fuwanime.ui.detail

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fuwanime.R
import com.fuwanime.ui.components.NetImage
import java.net.URI

/** Desktop-style UA: YouTube answers WebView mobile UAs with 153/152 instead of playing. */
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"

/** Chrome mobile UA — used for the m.youtube.com watch page and the mobile embed attempt. */
internal const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

/**
 * In-app trailer playback used when a trailer has no direct stream (or the Media3 attempt failed):
 * the player is the system WebView, part of Android itself, so it works without Google Play
 * services and without new dependencies — Media3 cannot open YouTube or Dailymotion page URLs.
 *
 * A bare `loadUrl(embed)` does not work. YouTube rejects a WebView without a real origin
 * ("Error 153", then "Error 152-4" with a "Tonton video di YouTube" button). So the player is an
 * iframe inside a tiny wrapper page loaded with `loadDataWithBaseURL("https://www.youtube.com", …)`,
 * the iframe carries `enablejsapi=1&origin=https://www.youtube.com`, and the wrapper runs the
 * postMessage `{event:'listening'}` handshake the JS-API player requires before it starts.
 *
 * Playback never leaves the app: [TrailerWebViewClient] cancels MAIN-frame navigation to anything
 * outside the player domains (the error screen's "Watch on YouTube" button, share links, the
 * YouTube app), while sub-frame/media requests (google.com, googlevideo.com) pass through.
 * An embed-disabled video surfaces `onError` through [TrailerBridge] and shows a clean message
 * instead of YouTube's raw error page.
 */
@Composable
fun InlineTrailerPlayer(
    thumbnail: String,
    fallbackThumbnail: String,
    trailerUrl: String,
    modifier: Modifier = Modifier,
) {
    // Ladder: watch pages first (YouTube's own site may play where embeds are refused with
    // 152-4), then the wrapper embed. A watchdog advances on silent failures; the embed
    // strategies additionally report onError through the bridge.
    val attempts = remember(trailerUrl) { trailerAttempts(trailerUrl) }
    var strategy by remember(trailerUrl) { mutableIntStateOf(0) }
    var embedError by remember(trailerUrl) { mutableStateOf(attempts.isEmpty()) }
    androidx.compose.runtime.LaunchedEffect(attempts, strategy) {
        if (embedError || strategy >= attempts.lastIndex) return@LaunchedEffect
        kotlinx.coroutines.delay(14_000)
        // No error signal is itself a signal: advance to the next attempt. The LAST attempt is
        // left alone — a slowly-starting player must not be killed by the watchdog.
        if (!embedError) strategy++
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (embedError) {
            NetImage(thumbnail.ifBlank { fallbackThumbnail }, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.trailer_embed_error),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            androidx.compose.runtime.key(strategy) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        trailerWebView(ctx, attempts[strategy]) { embedError = true }
                    },
                    update = { /* the trailer URL never changes for this item */ },
                )
            }
        }
    }
}

/** One WebView loading attempt: a URL, the UA to present, and optionally a wrapper page. */
internal class TrailerAttempt(val url: String, val ua: String, val html: String?, val baseUrl: String?)

/** Watch pages first, then the JS-API wrapper embed, then a bare embed load. */
internal fun trailerAttempts(trailerUrl: String): List<TrailerAttempt> {
    val embed = embedUrl(trailerUrl)
    val id = com.fuwanime.data.remote.trailerVideoId(trailerUrl)
    return buildList {
        id?.let {
            add(TrailerAttempt("https://m.youtube.com/watch?v=$it", MOBILE_UA, null, null))
            add(TrailerAttempt("https://www.youtube.com/watch?v=$it", DESKTOP_UA, null, null))
        }
        if (embed != null) {
            add(TrailerAttempt(embed, DESKTOP_UA, wrapperHtml(embed), PLAYER_ORIGIN))
            add(TrailerAttempt(embed, MOBILE_UA, null, null))
        }
    }
}

/** The WebView that hosts the current attempt; shared by the inline player and any dialog use. */
@SuppressLint("SetJavaScriptEnabled")
internal fun trailerWebView(context: Context, attempt: TrailerAttempt, onEmbedError: () -> Unit): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString = attempt.ua
        webViewClient = TrailerWebViewClient()
        webChromeClient = TrailerChromeClient(context)
        setBackgroundColor(android.graphics.Color.BLACK)
        addJavascriptInterface(TrailerBridge(onEmbedError), "FuwaTrailer")
        if (attempt.html != null && attempt.baseUrl != null) {
            loadDataWithBaseURL(attempt.baseUrl, attempt.html, "text/html", "utf-8", null)
        } else {
            loadUrl(attempt.url, mapOf("Referer" to "https://www.youtube.com/"))
        }
    }

private const val PLAYER_ORIGIN = "https://www.youtube.com"

/** Receives the wrapper's onError signal so YouTube's raw error page is replaced by a clean message. */
internal class TrailerBridge(private val onError: () -> Unit) {
    @JavascriptInterface
    fun onError(code: String?) {
        onError.invoke()
    }
}

/**
 * Keeps playback in-app: MAIN-frame navigation to anything but the player domains is cancelled
 * (true = do not load, and do not hand off to an external app). Sub-frame loads are left alone —
 * the player hops through google.com/googlevideo.com while streaming.
 */
internal class TrailerWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val host = request.url.host?.removePrefix("www.").orEmpty()
        val playerHost = host == "youtube.com" || host == "youtube-nocookie.com" ||
            host == "dailymotion.com" || host == "dm.com" ||
            // Watch pages redirect through consent/sign-in hosts before playing; blocking these
            // would leave a blank page instead of a video.
            host.endsWith("google.com") || host == "consent.youtube.com" || host == "googlevideo.com" ||
            host.endsWith("gstatic.com") || host.endsWith("ytimg.com")
        return !playerHost
    }
}

/** Hosts the embed player's fullscreen surface and restores the window when it closes. */
internal class TrailerChromeClient(private val context: Context) : WebChromeClient() {
    private var customView: View? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val activity = context as? Activity
        if (customView != null || activity == null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        activity.window.addContentView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        customView = null
        (view.parent as? ViewGroup)?.removeView(view)
        (context as? Activity)?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

/** The wrapper page: iframe player on a real origin plus the JS-API handshake (needed for 152-4). */
internal fun wrapperHtml(embedUrl: String): String {
    val src = embedUrl.replace("&", "&amp;").replace("\"", "&quot;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
          <style>
            html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
            iframe { width: 100%; height: 100%; border: 0; display: block; }
          </style>
        </head>
        <body>
          <iframe id="player" src="$src"
            allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
          <script>
            var player = document.getElementById('player');
            function handshake() {
              try {
                player.contentWindow.postMessage(
                  JSON.stringify({ event: 'listening', id: 1, channel: 'widget' }), '*');
              } catch (e) {}
            }
            player.onload = function () {
              handshake();
              setTimeout(handshake, 600);
              setTimeout(handshake, 1800);
            };
            window.addEventListener('message', function (e) {
              var data = e.data;
              try { if (typeof data === 'string') data = JSON.parse(data); } catch (err) { return; }
              if (data && data.event === 'onError' && window.FuwaTrailer) {
                window.FuwaTrailer.onError(String(data.info || ''));
              }
            });
          </script>
        </body>
        </html>
    """.trimIndent()
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

/**
 * The JS-API embed: `enablejsapi=1` + `origin` let the wrapper's handshake start playback.
 * youtube-nocookie is avoided — WebViews get 153/152 from it.
 */
internal fun youTubeEmbed(id: String): String =
    "https://www.youtube.com/embed/$id" +
        "?autoplay=1&playsinline=1&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
