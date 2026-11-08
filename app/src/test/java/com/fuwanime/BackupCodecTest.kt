package com.fuwanime

import com.fuwanime.data.backup.BackupCodec
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.backup.SettingEntry
import com.fuwanime.data.backup.isBackedUpSetting
import com.fuwanime.data.backup.settingTypeAccepted
import com.fuwanime.data.backup.shouldWriteBackup
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val file = BackupFile(
        createdAt = 1_762_500_000_000,
        favorites = listOf(FavoriteEntity("a", "Frieren", "p", "c", "TV", "ONGOING", "2026", "Action, Drama", 5)),
        history = listOf(WatchHistoryEntity("a-1", "a", "Frieren", "p", "c", "1", "Ep 1", "t", 900, 1000, 7)),
        watchDaily = listOf(WatchDailyEntity(20399, 1_200_000)),
        searches = listOf(SearchHistoryEntity("frieren", 9)),
        settings = listOf(SettingEntry("theme_mode", "string", "DARK"), SettingEntry("auto_pip", "boolean", "true")),
    )

    @Test fun roundTripKeepsEverything() = assertEquals(BackupResult.Ok(file), BackupCodec.decode(BackupCodec.encode(file)))

    @Test fun newerFormatIsTooNew() =
        assertEquals(BackupResult.TooNew, BackupCodec.decode(BackupCodec.encode(file).replace("\"format\":1", "\"format\":2")))

    @Test fun truncatedIsCorrupt() = assertEquals(BackupResult.Corrupt, BackupCodec.decode(BackupCodec.encode(file).dropLast(10)))

    @Test fun otherAppIsCorrupt() =
        assertEquals(BackupResult.Corrupt, BackupCodec.decode(BackupCodec.encode(file).replace("\"app\":\"fuwanime\"", "\"app\":\"lain\"")))

    @Test fun emptyStringIsCorrupt() = assertEquals(BackupResult.Corrupt, BackupCodec.decode(""))

    @Test fun settingFilter() {
        assertTrue(isBackedUpSetting("theme_mode"))
        assertTrue(isBackedUpSetting("op_start_123"))
        assertTrue(isBackedUpSetting("favorite_sort"))
        assertFalse(isBackedUpSetting("update_last_check_ms"))
        assertFalse(isBackedUpSetting("update_dismissed_tag"))
        assertFalse(isBackedUpSetting("notify_baseline_9"))
        assertFalse(isBackedUpSetting("backup_tree_uri"))
    }

    @Test fun existingBackupIsOnlyOverwrittenAfterThisInstallAdoptedTheFolder() {
        // Folder without a backup: always write.
        assertTrue(shouldWriteBackup(localEmpty = true, fileExists = false, folderAdopted = false))
        assertTrue(shouldWriteBackup(localEmpty = false, fileExists = false, folderAdopted = false))
        // Folder with someone's backup, not yet restored/written by this install: never overwrite, even with local data.
        assertFalse(shouldWriteBackup(localEmpty = false, fileExists = true, folderAdopted = false))
        assertFalse(shouldWriteBackup(localEmpty = true, fileExists = true, folderAdopted = false))
        // Adopted folder: keep it current, but an empty phone still never replaces it.
        assertTrue(shouldWriteBackup(localEmpty = false, fileExists = true, folderAdopted = true))
        assertFalse(shouldWriteBackup(localEmpty = true, fileExists = true, folderAdopted = true))
    }

    @Test fun importRejectsKnownKeyWithWrongType() {
        assertTrue(settingTypeAccepted("preferred_quality", "int"))
        assertFalse(settingTypeAccepted("preferred_quality", "string"))
        assertTrue(settingTypeAccepted("theme_mode", "string"))
        assertFalse(settingTypeAccepted("auto_pip", "int"))
        assertTrue(settingTypeAccepted("op_start_42", "long"))
        assertFalse(settingTypeAccepted("op_end_42", "string"))
        assertTrue(settingTypeAccepted("some_future_key", "string"))
        assertFalse(settingTypeAccepted("theme_mode", "blob"))
    }
}
