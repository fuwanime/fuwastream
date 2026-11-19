package com.fuwanime.data.remote

import com.fuwanime.data.StreamSource
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
 * playable stream URLs so the trailer can play in the app's own Media3 player — the same
 * way regular episode streams play, with no WebView and no new dependency.
 *
 * YouTube: the InnerTube `player` endpoint with the ANDROID client. The ANDROID client is the
 * one client that still returns plain (non-ciphered) muxed `formats[]` URLs. NOTE: YouTube
 * bot-checks datacenter IPs ("Sign in to confirm you're not a bot"), so this works reliably
 * from a phone's mobile network but may fail from VPS-grade networks — callers must treat an
 * empty result as "fall back to the WebView embed ladder".
 *
 * Dailymotion: the player metadata endpoint, whose `qualities[]` carry direct MP4 URLs.
 */

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

/** Direct playable sources for a trailer URL; empty when nothing could be resolved. */
suspend fun resolveTrailerSources(url: String, client: OkHttpClient): List<StreamSource> =
    withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        trailerVideoId(url)?.let { id -> sources += youtubeSources(id, client) }
        dailymotionVideoId(url)?.let { id -> sources += dailymotionSources(id, client) }
        sources
    }

/** JSON helpers: the project's orEmpty() extensions for JSONArray live inside other files. */
private fun JSONArray?.orJsonArray(): JSONArray = this ?: JSONArray()

private fun youtubeSources(id: String, client: OkHttpClient): List<StreamSource> {
    return runCatching {
        val body = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "ANDROID")
                        .put("clientVersion", "19.09.37")
                        .put("androidSdkVersion", 30)
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
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val root = JSONObject(r.body?.string().orEmpty())
            if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return emptyList()
            val formats = root.optJSONObject("streamingData")?.optJSONArray("formats").orJsonArray()
            (0 until formats.length()).mapNotNull { i ->
                val f = formats.optJSONObject(i) ?: return@mapNotNull null
                val direct = f.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (f.has("signatureCipher")) return@mapNotNull null // ciphered: undecipherable without a JS extractor
                StreamSource(
                    url = direct,
                    quality = f.optString("qualityLabel").ifBlank { "itag ${f.optInt("itag")}" },
                    server = "YouTube",
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun dailymotionSources(id: String, client: OkHttpClient): List<StreamSource> {
    return runCatching {
        val req = Request.Builder()
            .url("https://www.dailymotion.com/player/metadata/video/$id")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/124.0 Mobile Safari/537.36")
            .header("Referer", "https://www.dailymotion.com/")
            .get()
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val root = JSONObject(r.body?.string().orEmpty())
            val qualities = root.optJSONArray("qualities").orJsonArray()
            (0 until qualities.length()).flatMap { i ->
                val q = qualities.optJSONObject(i) ?: return@flatMap emptyList()
                val label = q.optString("type").ifBlank { "video" }
                val urls = q.optJSONArray("url")
                val single = q.optString("url").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
                val list = if (urls != null) (0 until urls.length()).mapNotNull { urls.optString(it).takeIf(String::isNotBlank) } else single
                list.map { StreamSource(url = it, quality = label, server = "Dailymotion") }
            }
        }
    }.getOrDefault(emptyList())
}
