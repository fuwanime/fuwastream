package com.fuwanime.data

import android.os.Build
import com.fuwanime.BuildConfig
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.remote.GitHubApi
import com.fuwanime.data.remote.GitHubAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * True when [candidate] is a newer release than [current].
 * - "build-N" vs "build-M": numeric comparison.
 * - "vX.Y.Z" vs "vA.B.C": SemVer numerical comparison; a "-suffix" is ignored.
 * - "vX.Y.Z" vs "build-N": always offered as an update (SemVer migration).
 * - Downgrades, identical tags, or unknown tag formats return false.
 */
fun isNewerRelease(candidate: String, current: String): Boolean {
    fun build(tag: String) = tag.removePrefix("build-").takeIf { tag.startsWith("build-") }?.toIntOrNull()
    // CI builds carry a pre-release suffix ("v2.1.0-ci.105"); they rank as the release they were built after.
    fun version(tag: String) = tag.takeIf { it.startsWith("v") }?.drop(1)?.substringBefore('-')?.split('.')?.map { it.toIntOrNull() ?: return null }

    val cBuild = build(candidate)
    val curBuild = build(current)
    if (cBuild != null && curBuild != null) return cBuild > curBuild

    val cVer = version(candidate)
    val curVer = version(current)
    if (cVer != null && curVer != null) {
        for (i in 0 until maxOf(cVer.size, curVer.size)) {
            val a = cVer.getOrElse(i) { 0 }
            val b = curVer.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    // SemVer transition: any valid SemVer release (vX.Y.Z) is newer than legacy build-N releases.
    if (cVer != null && curBuild != null) return true

    return false
}

/**
 * GitHub may attach one APK per ABI. Pick the APK that matches this device instead of the last upload,
 * otherwise an ARM phone could download the x86_64 build (or the 32-bit build on an arm64-only device).
 */
fun selectApkUrl(assets: List<GitHubAsset>): String? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    val preferred = when {
        abi.startsWith("arm64") -> "arm64-v8a"
        abi.startsWith("armeabi") || abi.startsWith("armv7") -> "armeabi-v7a"
        abi.startsWith("x86_64") -> "x86_64"
        abi.startsWith("x86") -> "x86"
        else -> null
    }
    return (
        apks.firstOrNull { preferred != null && it.name.contains(preferred) }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.first()
        ).url
}

/**
 * In-app update checker against the GitHub Releases page. The changelog (release body)
 * is shown inside the app so users never have to open a GitHub compare view.
 */
class UpdateRepository(private val settingsStore: SettingsStore) {

    data class UpdateInfo(
        val tag: String,
        val title: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
    )

    /** The release tag this build was produced from, e.g. "build-8" or "v2.1.0". */
    private fun currentTag(): String {
        val versionName = BuildConfig.VERSION_NAME
        return if (versionName.contains("build.")) {
            "build-" + versionName.substringAfterLast('.')
        } else {
            "v$versionName"
        }
    }

    /** Returns update info when a newer release exists, null when up to date (or on failure). */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val currentTag = currentTag()
        // Every Beranda open asks once; the 6 h throttle is for the background worker only, which shared it before
        // and so hid the banner whenever the worker had just run.
        val release = GitHubApi.latest() ?: return@withContext null
        if (!isNewerRelease(release.tagName, currentTag)) return@withContext null

        UpdateInfo(
            tag = release.tagName,
            title = release.name?.takeIf { it.isNotBlank() } ?: "FuwaStream ${release.tagName.removePrefix("v")}",
            notes = release.body.orEmpty().trim(),
            apkUrl = selectApkUrl(release.assets),
            pageUrl = release.htmlUrl,
        )
    }
}
