package com.fuwanime.data

import com.fuwanime.BuildConfig
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.remote.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * True when [candidate] is a newer release than [current].
 * - "build-N" vs "build-M": numeric comparison.
 * - "vX.Y.Z" vs "vA.B.C": SemVer numerical comparison.
 * - "vX.Y.Z" vs "build-N": always offered as an update (SemVer migration).
 * - Downgrades, identical tags, or unknown tag formats return false.
 */
fun isNewerRelease(candidate: String, current: String): Boolean {
    fun build(tag: String) = tag.removePrefix("build-").takeIf { tag.startsWith("build-") }?.toIntOrNull()
    fun version(tag: String) = tag.takeIf { it.startsWith("v") }?.drop(1)?.split('.')?.map { it.toIntOrNull() ?: return null }

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
        if (!settingsStore.shouldCheckUpdate(currentTag)) return@withContext null
        val release = GitHubApi.latest() ?: return@withContext null
        settingsStore.markUpdateChecked()
        if (!isNewerRelease(release.tagName, currentTag)) return@withContext null

        UpdateInfo(
            tag = release.tagName,
            title = release.name?.takeIf { it.isNotBlank() } ?: "FuwaStream ${release.tagName.removePrefix("v")}",
            notes = release.body.orEmpty().trim(),
            apkUrl = release.assets.lastOrNull { it.name.endsWith(".apk") }?.url
                ?: release.assets.firstOrNull()?.url,
            pageUrl = release.htmlUrl,
        )
    }
}
