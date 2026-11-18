package com.fuwanime.data.remote

import com.fuwanime.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Minimal GitHub Releases client for the in-app update checker. */
@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "assets") val assets: List<GitHubAsset> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name") val name: String = "",
    @Json(name = "browser_download_url") val url: String = "",
)

/** The tag at the end of a ".../releases/tag/<tag>" redirect target, or null. */
fun tagFromReleaseLocation(location: String?): String? =
    location?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.trim('/')?.takeIf { it.isNotEmpty() }

/** A release rebuilt from its tag alone: no notes, and the APK URL follows the release workflow's file name. */
fun releaseFromTag(repo: String, tag: String): GitHubRelease {
    val version = tag.removePrefix("v")
    val base = "https://github.com/$repo/releases"
    return GitHubRelease(
        tagName = tag,
        name = "FuwaStream $version",
        htmlUrl = "$base/tag/$tag",
        assets = listOf(GitHubAsset("FuwaStream-$version.apk", "$base/download/$tag/FuwaStream-$version.apk")),
    )
}

object GitHubApi {
    private val LATEST_URL = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient = client.newBuilder().followRedirects(false).build()

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(GitHubRelease::class.java)

    /** Returns the latest release, or null on any failure (offline, ...). */
    fun latest(): GitHubRelease? = fromApi() ?: fromRedirect()

    // The anonymous API allows 60 requests an hour per IP, so on shared or mobile networks it often answers 403.
    private fun fromRedirect(): GitHubRelease? = runCatching {
        val request = Request.Builder().url("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest").build()
        noRedirectClient.newCall(request).execute().use { response ->
            tagFromReleaseLocation(response.header("Location"))?.let { releaseFromTag(BuildConfig.UPDATE_REPO, it) }
        }
    }.getOrNull()

    private fun fromApi(): GitHubRelease? = runCatching {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.source()?.let { source -> adapter.fromJson(source) }
        }
    }.getOrNull()
}
