package com.fuwanime.data.local

import com.fuwanime.data.backup.settingTypeAccepted
import kotlinx.coroutines.flow.first
import com.fuwanime.data.backup.isBackedUpSetting
import com.fuwanime.data.backup.SettingEntry
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fuwanime.data.OpMark
import com.fuwanime.ui.library.FavoriteSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the video fills its area: whole picture (letterboxed) or cropped to fill. */
enum class VideoResize { FIT, ZOOM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val amoledBlack: Boolean = false,
    /** Preferred stream height (e.g. 720); the closest available source is picked. */
    val preferredQuality: Int = 720,
    val autoPlayNext: Boolean = true,
    val autoPip: Boolean = true,
    val doubleTapSeekSeconds: Int = 10,
    /** Downloads default lower than streaming to save storage. */
    val downloadQuality: Int = 480,
    val downloadWifiOnly: Boolean = false,
    /** Periodic background check for new episodes of favorited, ongoing anime. */
    val notifyNewEpisodes: Boolean = true,
    /** Skip a marked opening without waiting for a tap. */
    val autoSkipOp: Boolean = false,
    /** Generic "+85 dtk" jump for anime whose opening is not marked yet. */
    val jumpAheadButton: Boolean = true,
    /** Stream height on metered networks; 0 follows [preferredQuality]. */
    val mobileDataQuality: Int = 0,
    /** Keep the sound playing when the screen is off or the app is left without PiP. */
    val backgroundAudio: Boolean = false,
    val videoResize: VideoResize = VideoResize.FIT,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

internal object Keys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val amoledBlack = booleanPreferencesKey("amoled_black")
    val preferredQuality = intPreferencesKey("preferred_quality")
    val autoPlayNext = booleanPreferencesKey("auto_play_next")
    val autoPip = booleanPreferencesKey("auto_pip")
    val doubleTapSeekSeconds = intPreferencesKey("double_tap_seek_seconds")
    val downloadQuality = intPreferencesKey("download_quality")
    val downloadWifiOnly = booleanPreferencesKey("download_wifi_only")
    val notifyNewEpisodes = booleanPreferencesKey("notify_new_episodes")
    val autoSkipOp = booleanPreferencesKey("auto_skip_op")
    val jumpAheadButton = booleanPreferencesKey("jump_ahead_button")
    val mobileDataQuality = intPreferencesKey("mobile_data_quality")
    val updateLastCheckMs = longPreferencesKey("update_last_check_ms")
    val updateDismissedTag = stringPreferencesKey("update_dismissed_tag")
    /** Age confirmation is a consent record, kept when user data is wiped. */
    val ageConfirmed = booleanPreferencesKey("age_confirmed")
    val backgroundAudio = booleanPreferencesKey("background_audio")
    val videoResize = stringPreferencesKey("video_resize")
    val backupTreeUri = stringPreferencesKey("backup_tree_uri")
    val backupAuto = booleanPreferencesKey("backup_auto")
    val backupLastMs = longPreferencesKey("backup_last_ms")
    val backupError = stringPreferencesKey("backup_error")
    val backupPromptDismissed = booleanPreferencesKey("backup_prompt_dismissed")
    val backupFolderAdopted = booleanPreferencesKey("backup_folder_adopted")
}

/** Where and how the backup file is written; all keys start with "backup_" so the backup itself skips them. */
data class BackupPrefs(
    val treeUri: String?,
    val auto: Boolean,
    val lastMs: Long?,
    val error: String?,
    val promptDismissed: Boolean,
    /** This install restored from, or wrote the first file into, the picked folder; see shouldWriteBackup. */
    val folderAdopted: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val backup: Flow<BackupPrefs> = context.dataStore.data.map {
        BackupPrefs(it[Keys.backupTreeUri], it[Keys.backupAuto] ?: true, it[Keys.backupLastMs], it[Keys.backupError], it[Keys.backupPromptDismissed] ?: false, it[Keys.backupFolderAdopted] ?: false)
    }

    suspend fun setBackupTree(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.backupTreeUri) else { it[Keys.backupTreeUri] = uri }
        it.remove(Keys.backupError)
        it.remove(Keys.backupFolderAdopted)
    }
    suspend fun setBackupAuto(enabled: Boolean) = context.dataStore.edit { it[Keys.backupAuto] = enabled }

    /** A success clears the last error; a failure keeps the last successful time. */
    suspend fun setBackupResult(lastMs: Long?, error: String?) = context.dataStore.edit {
        if (lastMs != null) { it[Keys.backupLastMs] = lastMs }
        if (error == null) it.remove(Keys.backupError) else { it[Keys.backupError] = error }
    }
    suspend fun dismissBackupPrompt() = context.dataStore.edit { it[Keys.backupPromptDismissed] = true }
    suspend fun adoptBackupFolder() = context.dataStore.edit { it[Keys.backupFolderAdopted] = true }

    /** Every user preference as typed strings, minus install bookkeeping (see isBackedUpSetting). */
    suspend fun exportSettings(): List<SettingEntry> =
        context.dataStore.data.first().asMap().mapNotNull { (key, value) ->
            if (!isBackedUpSetting(key.name)) return@mapNotNull null
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Double -> "double"
                else -> return@mapNotNull null
            }
            SettingEntry(key.name, type, value.toString())
        }

    /** Writes backed-up preferences; unknown types and unparseable values are skipped, not guessed. */
    suspend fun importSettings(entries: List<SettingEntry>) = context.dataStore.edit { prefs ->
        entries.filter { isBackedUpSetting(it.key) && settingTypeAccepted(it.key, it.type) }.forEach { e ->
            when (e.type) {
                "string" -> { prefs[stringPreferencesKey(e.key)] = e.value }
                "boolean" -> e.value.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(e.key)] = it }
                "int" -> e.value.toIntOrNull()?.let { prefs[intPreferencesKey(e.key)] = it }
                "long" -> e.value.toLongOrNull()?.let { prefs[longPreferencesKey(e.key)] = it }
                "double" -> e.value.toDoubleOrNull()?.let { prefs[doublePreferencesKey(e.key)] = it }
            }
        }
    }



    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    /** False until the user confirms they are 13+ on the first launch. */
    val ageConfirmed: Flow<Boolean> = context.dataStore.data.map { it[Keys.ageConfirmed] ?: false }

    suspend fun confirmAge() = context.dataStore.edit { it[Keys.ageConfirmed] = true }

    /**
     * Deletes every user-created preference (theme, qualities, OP marks, update bookkeeping).
     * The age confirmation is kept so the gate does not reappear after a data wipe.
     */
    suspend fun clearUserData() = context.dataStore.edit { prefs ->
        val age = prefs[Keys.ageConfirmed]
        prefs.clear()
        if (age != null) prefs[Keys.ageConfirmed] = age
    }


    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setAmoledBlack(enabled: Boolean) = context.dataStore.edit { it[Keys.amoledBlack] = enabled }
    suspend fun setPreferredQuality(height: Int) = context.dataStore.edit { it[Keys.preferredQuality] = height }
    suspend fun setAutoPlayNext(enabled: Boolean) = context.dataStore.edit { it[Keys.autoPlayNext] = enabled }
    suspend fun setAutoPip(enabled: Boolean) = context.dataStore.edit { it[Keys.autoPip] = enabled }
    suspend fun setDoubleTapSeekSeconds(seconds: Int) = context.dataStore.edit { it[Keys.doubleTapSeekSeconds] = seconds }
    suspend fun setDownloadQuality(height: Int) = context.dataStore.edit { it[Keys.downloadQuality] = height }
    suspend fun setDownloadWifiOnly(enabled: Boolean) = context.dataStore.edit { it[Keys.downloadWifiOnly] = enabled }
    suspend fun setNotifyNewEpisodes(enabled: Boolean) = context.dataStore.edit { it[Keys.notifyNewEpisodes] = enabled }
    suspend fun setAutoSkipOp(enabled: Boolean) = context.dataStore.edit { it[Keys.autoSkipOp] = enabled }
    suspend fun setJumpAheadButton(enabled: Boolean) = context.dataStore.edit { it[Keys.jumpAheadButton] = enabled }
    suspend fun setMobileDataQuality(height: Int) = context.dataStore.edit { it[Keys.mobileDataQuality] = height }
    suspend fun setBackgroundAudio(enabled: Boolean) = context.dataStore.edit { it[Keys.backgroundAudio] = enabled }
    suspend fun setVideoResize(mode: VideoResize) = context.dataStore.edit { it[Keys.videoResize] = mode.name }

    /** Opening marked by the user for an anime; the same range applies to all its episodes. */
    fun opMark(animeId: String): Flow<OpMark?> = context.dataStore.data.map { prefs ->
        val start = prefs[longPreferencesKey("op_start_$animeId")]
        val end = prefs[longPreferencesKey("op_end_$animeId")]
        if (start != null && end != null) OpMark(start, end) else null
    }

    suspend fun setOpMark(animeId: String, mark: OpMark) = context.dataStore.edit {
        it[longPreferencesKey("op_start_$animeId")] = mark.startMs
        it[longPreferencesKey("op_end_$animeId")] = mark.endMs
    }

    suspend fun clearOpMark(animeId: String) = context.dataStore.edit {
        it.remove(longPreferencesKey("op_start_$animeId"))
        it.remove(longPreferencesKey("op_end_$animeId"))
    }

    /** Latest episode number already seen/notified for a favorited anime (per-anime key). */
    suspend fun notifyBaseline(animeId: String): Double? =
        context.dataStore.data.map { it[doublePreferencesKey("notify_baseline_$animeId")] }.firstOrNull()

    suspend fun setNotifyBaseline(animeId: String, episode: Double) {
        context.dataStore.edit { it[doublePreferencesKey("notify_baseline_$animeId")] = episode }
    }

    /** Koleksi → Favorit order; unknown stored names fall back to [FavoriteSort.ADDED]. */
    val favoriteSort: Flow<FavoriteSort> = context.dataStore.data.map { prefs ->
        FavoriteSort.entries.firstOrNull { it.name == prefs[stringPreferencesKey("favorite_sort")] } ?: FavoriteSort.ADDED
    }

    suspend fun setFavoriteSort(sort: FavoriteSort) = context.dataStore.edit { it[stringPreferencesKey("favorite_sort")] = sort.name }

    /** Every notify baseline, keyed by animeId. */
    val notifyBaselines: Flow<Map<String, Double>> = context.dataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val id = key.name.removePrefix("notify_baseline_")
            if (id != key.name && value is Double) id to value else null
        }.toMap()
    }

    /** Update check runs at most once every 6 hours (unless the installed tag changed). */
    suspend fun shouldCheckUpdate(currentTag: String): Boolean {
        val last = context.dataStore.data.map { it[Keys.updateLastCheckMs] ?: 0L }.firstOrNull() ?: 0L
        return System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L
    }

    suspend fun markUpdateChecked() {
        context.dataStore.edit { it[Keys.updateLastCheckMs] = System.currentTimeMillis() }
    }

    suspend fun dismissedUpdateTag(): String? =
        context.dataStore.data.map { it[Keys.updateDismissedTag] }.firstOrNull()

    suspend fun dismissUpdate(tag: String) = context.dataStore.edit { it[Keys.updateDismissedTag] = tag }
}

internal fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
        dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
        amoledBlack = this[Keys.amoledBlack] ?: d.amoledBlack,
        preferredQuality = this[Keys.preferredQuality] ?: d.preferredQuality,
        autoPlayNext = this[Keys.autoPlayNext] ?: d.autoPlayNext,
        autoPip = this[Keys.autoPip] ?: d.autoPip,
        doubleTapSeekSeconds = this[Keys.doubleTapSeekSeconds] ?: d.doubleTapSeekSeconds,
        downloadQuality = this[Keys.downloadQuality] ?: d.downloadQuality,
        downloadWifiOnly = this[Keys.downloadWifiOnly] ?: d.downloadWifiOnly,
        notifyNewEpisodes = this[Keys.notifyNewEpisodes] ?: d.notifyNewEpisodes,
        autoSkipOp = this[Keys.autoSkipOp] ?: d.autoSkipOp,
        jumpAheadButton = this[Keys.jumpAheadButton] ?: d.jumpAheadButton,
        mobileDataQuality = this[Keys.mobileDataQuality] ?: d.mobileDataQuality,
        backgroundAudio = this[Keys.backgroundAudio] ?: d.backgroundAudio,
        videoResize = this[Keys.videoResize]?.let { runCatching { VideoResize.valueOf(it) }.getOrNull() } ?: d.videoResize,
    )
}
