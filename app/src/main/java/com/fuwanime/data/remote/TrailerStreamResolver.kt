package com.fuwanime.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Resolves a trailer URL (YouTube watch/short/youtu.be or Dailymotion video) into direct,
 * playable stream URLs so the trailer can play in the app's own Media3 player — the same way
 * regular episode streams play, with no WebView and no new dependency.
 *
 * YouTube: the InnerTube `player` endpoint with the **ANDROID_VR** client — the only client
 * family that still hands out plain (non-ciphered) stream URLs. It returns video and audio as
 * separate adaptive streams, so [TrailerStreams] carries them apart and the player merges them.
 * YouTube bot-checks datacenter IPs, so this mostly succeeds from a phone's mobile network;
 * callers must treat null as "fall back to the watch-page/embed ladder".
 *
 * Dailymotion: the player metadata endpoint, whose `qualities[]` carry direct MP4 URLs.
 */

/** The YouTube app User-Agent — googlevideo stream URLs are tied to it. */
const val YT_APP_UA = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"

/** ANDROID_VR client identity, the current source of un-ciphered streams. */
private const val YT_VR_UA =
    "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; GB) gzip"

/** A resolved trailer: one playable video URL, plus a separate audio URL when the source is split. */
data class TrailerStreams(val video: String, val audio: String?, val label: String) {
    /** What Media3 should fetch with — googlevideo ties the URL to the requesting client. */
    val userAgent: String get() = if (label.contains("Dailymotion")) YT_APP_UA else YT_VR_UA
}

/** Extracts the YouTube video id from watch/short/youtu.be/embed URLs; null when it is not YouTube. */
internal fun trailerVideoId(url: String): String? {
    val clean = url.trim()
    return runCatching {
        val uri = URI(clean)
        when (uri.host?.removePrefix("www.").orEmpty()) {
            "youtu.be" -> uri.path?.trim('/')?.takeIf(String::isNotBlank)
            "youtube.com", "m.youtube.com" -> {
                uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { q ->
                    q.split('&').firstOrNull { it.startsWith("v=") }?.substring(2)?.takeIf(String::isNotBlank)
                } ?: uri.path?.takeIf { it.startsWith("/shorts/") || it.startsWith("/embed/") }?.trim('/')?.takeIf(String::isNotBlank)
            }
            else -> null
        }
    }.getOrNull()
}

/** Extracts the Dailymotion video id from a video URL; null when it is not Dailymotion. */
internal fun dailymotionVideoId(url: String): String? = runCatching {
    val uri = URI(url.trim())
    if (uri.host?.removePrefix("www.").orEmpty() != "dailymotion.com") return@runCatching null
    uri.path?.trim('/')?.substringAfterLast('/')?.takeIf(String::isNotBlank)
}.getOrNull()

/** Direct playable stream for a trailer URL; null when nothing could be resolved. */
suspend fun resolveTrailerStreams(url: String, client: OkHttpClient): TrailerStreams? =
    withContext(Dispatchers.IO) {
        trailerVideoId(url)?.let { id -> youtubeStreams(id, client)?.let { return@withContext it } }
        dailymotionVideoId(url)?.let { id -> dailymotionStreams(id, client)?.let { return@withContext it } }
        null
    }

/** JSON helpers: the project's orEmpty() extensions for JSONArray live inside other files. */
private fun JSONArray?.orJsonArray(): JSONArray = this ?: JSONArray()

private fun youtubeStreams(id: String, client: OkHttpClient): TrailerStreams? {
    return runCatching {
        val body = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "ANDROID_VR")
                        .put("clientVersion", "1.62.27")
                        .put("deviceMake", "Oculus")
                        .put("deviceModel", "Quest 3")
                        .put("androidSdkVersion", 32)
                        .put("osName", "Android")
                        .put("osVersion", "12")
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            )
            put("videoId", id)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", YT_VR_UA)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val root = JSONObject(r.body?.string().orEmpty())
            if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null
            val streaming = root.optJSONObject("streamingData") ?: return null

            // Some clients still return a muxed (video+audio) format; prefer it, it needs no merge.
            val muxed = streaming.optJSONArray("formats").orJsonArray()
            for (i in 0 until muxed.length()) {
                val f = muxed.optJSONObject(i) ?: continue
                val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                return TrailerStreams(url, null, f.optString("qualityLabel", "YouTube"))
            }

            // ANDROID_VR style: adaptive video-only + audio-only streams.
            val adaptive = streaming.optJSONArray("adaptiveFormats").orJsonArray()
            var bestVideo: JSONObject? = null
            var bestAudio: JSONObject? = null
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                if (f.optString("url").isBlank() || f.has("signatureCipher")) continue
                val mime = f.optString("mimeType")
                when {
                    mime.startsWith("video/mp4") && !mime.contains("av01") -> {
                        if (bestVideo == null || f.optInt("height") > bestVideo!!.optInt("height")) bestVideo = f
                    }
                    mime.startsWith("audio/mp4") -> {
                        val rate = f.optInt("bitrate")
                        if (bestAudio == null || rate > bestAudio!!.optInt("bitrate")) bestAudio = f
                    }
                }
            }
            val video = bestVideo?.optString("url")?.takeIf { it.isNotBlank() } ?: return null
            val label = bestVideo?.optString("qualityLabel").orEmpty().ifBlank { "YouTube" }
            TrailerStreams(video, bestAudio?.optString("url")?.takeIf { it.isNotBlank() }, label)
        }
    }.getOrNull()
}

private fun dailymotionStreams(id: String, client: OkHttpClient): TrailerStreams? {
    return runCatching {
        val req = Request.Builder()
            .url("https://www.dailymotion.com/player/metadata/video/$id")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/124.0 Mobile Safari/537.36")
            .header("Referer", "https://www.dailymotion.com/")
            .get()
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val root = JSONObject(r.body?.string().orEmpty())
            val qualities = root.optJSONArray("qualities").orJsonArray()
            for (i in 0 until qualities.length()) {
                val q = qualities.optJSONObject(i) ?: continue
                val urls = q.optJSONArray("url")
                val first = if (urls != null && urls.length() > 0) urls.optString(0) else q.optString("url")
                if (!first.isNullOrBlank()) return TrailerStreams(first, null, "Dailymotion")
            }
            null
        }
    }.getOrNull()
}
