package com.fuwanime.data.local

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    /** Stream height on metered networks; 0 follows [preferredQuality]. */
    val mobileDataQuality: Int = 0,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
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
        val mobileDataQuality = intPreferencesKey("mobile_data_quality")
        val updateLastCheckMs = longPreferencesKey("update_last_check_ms")
        val updateDismissedTag = stringPreferencesKey("update_dismissed_tag")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
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
            mobileDataQuality = this[Keys.mobileDataQuality] ?: d.mobileDataQuality,
        )
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
    suspend fun setMobileDataQuality(height: Int) = context.dataStore.edit { it[Keys.mobileDataQuality] = height }

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
