package com.fuwanime.data.backup

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/** One DataStore preference; [value] is the string form of a "string", "boolean", "int", "long" or "double". */
@JsonClass(generateAdapter = true)
data class SettingEntry(val key: String, val type: String, val value: String)

@JsonClass(generateAdapter = true)
data class BackupFile(
    val format: Int = BackupCodec.FORMAT,
    val app: String = BackupCodec.APP,
    val createdAt: Long,
    val favorites: List<FavoriteEntity>,
    val history: List<WatchHistoryEntity>,
    val watchDaily: List<WatchDailyEntity>,
    val searches: List<SearchHistoryEntity>,
    val settings: List<SettingEntry>,
)

sealed interface BackupResult {
    data class Ok(val file: BackupFile) : BackupResult
    /** Written by a newer app version whose format this one can't be sure to read. */
    data object TooNew : BackupResult
    data object Corrupt : BackupResult
}

@JsonClass(generateAdapter = true)
internal data class BackupHeader(val format: Int?, val app: String?)

object BackupCodec {
    const val FORMAT = 1
    const val APP = "fuwanime"
    const val FILE_NAME = "fuwanime-backup.json"

    private val moshi = Moshi.Builder().build()
    private val fileAdapter = moshi.adapter(BackupFile::class.java)
    private val headerAdapter = moshi.adapter(BackupHeader::class.java)

    fun encode(file: BackupFile): String = fileAdapter.toJson(file)

    fun decode(json: String): BackupResult {
        // Check the header first so a newer format is reported as such rather than as a parse failure.
        val header = runCatching { headerAdapter.lenient().fromJson(json) }.getOrNull() ?: return BackupResult.Corrupt
        if (header.app != APP || header.format == null) return BackupResult.Corrupt
        if (header.format > FORMAT) return BackupResult.TooNew
        val file = runCatching { fileAdapter.fromJson(json) }.getOrNull() ?: return BackupResult.Corrupt
        return BackupResult.Ok(file)
    }
}

/** Bookkeeping keys that describe this install, not the user's choices, stay out of the backup. */
fun isBackedUpSetting(key: String): Boolean =
    key != "update_last_check_ms" && key != "update_dismissed_tag" &&
        !key.startsWith("notify_baseline_") && !key.startsWith("backup_")

/**
 * A backup already in the folder is only replaced once this install has adopted the folder (restored from it, or
 * written the first file there); otherwise a reinstall with one new favourite would wipe hundreds of rows.
 * An empty phone never replaces an existing file.
 */
fun shouldWriteBackup(localEmpty: Boolean, fileExists: Boolean, folderAdopted: Boolean): Boolean =
    !fileExists || (folderAdopted && !localEmpty)

/** Types the app reads each preference as; a backup entry with another type would crash the reader (ClassCastException). */
private val SETTING_TYPES = mapOf(
    "theme_mode" to "string", "video_resize" to "string", "favorite_sort" to "string",
    "dynamic_color" to "boolean", "amoled_black" to "boolean", "auto_play_next" to "boolean", "auto_pip" to "boolean",
    "download_wifi_only" to "boolean", "notify_new_episodes" to "boolean", "auto_skip_op" to "boolean",
    "jump_ahead_button" to "boolean", "age_confirmed" to "boolean", "background_audio" to "boolean",
    "preferred_quality" to "int", "double_tap_seek_seconds" to "int", "download_quality" to "int", "mobile_data_quality" to "int",
)
private val SUPPORTED_TYPES = setOf("string", "boolean", "int", "long", "double")

/** False for unknown types and for known keys stored under the wrong type; keys the app doesn't know are harmless. */
fun settingTypeAccepted(key: String, type: String): Boolean {
    if (type !in SUPPORTED_TYPES) return false
    val expected = SETTING_TYPES[key] ?: if (key.startsWith("op_start_") || key.startsWith("op_end_")) "long" else null
    return expected == null || expected == type
}
