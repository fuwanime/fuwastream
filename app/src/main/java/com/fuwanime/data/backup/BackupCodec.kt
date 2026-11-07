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

/** A freshly installed (empty) phone must never replace a backup that is already in the folder. */
fun shouldWriteBackup(localEmpty: Boolean, fileExists: Boolean): Boolean = !(localEmpty && fileExists)
