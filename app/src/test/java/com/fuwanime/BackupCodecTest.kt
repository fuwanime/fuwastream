package com.fuwanime

import com.fuwanime.data.backup.BackupCodec
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.backup.SettingEntry
import com.fuwanime.data.backup.isBackedUpSetting
import com.fuwanime.data.backup.settingTypeAccepted
import com.fuwanime.data.backup.shouldWriteBackup
import com.fuwanime.data.local.ContentPreferenceEntity
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
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
        mangaFavorites = listOf(MangaFavoriteEntity("m1", "Chainsaw", null, 3)),
        mangaHistory = listOf(MangaHistoryEntity("c1", "m1", "Chainsaw", "u", "Ch 1", 4, 20, 8)),
        contentPreferences = listOf(ContentPreferenceEntity("ANIME:a", "ANIME", "a", notifyMode = "ALL", updatedAt = 10)),
    )

    @Test fun roundTripKeepsEverything() = assertEquals(BackupResult.Ok(file), BackupCodec.decode(BackupCodec.encode(file)))

    @Test fun newerFormatIsTooNew() =
        assertEquals(BackupResult.TooNew, BackupCodec.decode(BackupCodec.encode(file).replace("\"format\":3", "\"format\":4")))

    @Test fun formatOneStillDecodes() {
        val v1 = """{"format":1,"app":"fuwanime","createdAt":1,"favorites":[],"history":[],"watchDaily":[],"searches":[],"settings":[]}"""
        val r = BackupCodec.decode(v1) as BackupResult.Ok
        assertEquals(emptyList<MangaFavoriteEntity>(), r.file.mangaFavorites)
        assertEquals(emptyList<MangaHistoryEntity>(), r.file.mangaHistory)
        assertEquals(emptyList<ContentPreferenceEntity>(), r.file.contentPreferences)
    }

    @Test fun formatTwoWithoutPreferencesStillDecodes() {
        val v2 = """{"format":2,"app":"fuwanime","createdAt":1,"favorites":[],"history":[],"watchDaily":[],"searches":[],"settings":[],"mangaFavorites":[],"mangaHistory":[]}"""
        val r = BackupCodec.decode(v2) as BackupResult.Ok
        assertEquals(emptyList<ContentPreferenceEntity>(), r.file.contentPreferences)
    }

    @Test fun formatTwoMangaFavoriteWithoutCollectionStatusUsesDefault() {
        val v2 = """{"format":2,"app":"fuwanime","createdAt":1,"favorites":[],"history":[],"watchDaily":[],"searches":[],"settings":[],"mangaFavorites":[{"mangaId":"m1","title":"Old","coverUrl":null,"addedAt":3}],"mangaHistory":[]}"""
        val r = BackupCodec.decode(v2) as BackupResult.Ok
        assertEquals("PLANNED", r.file.mangaFavorites.single().collectionStatus)
    }

    @Test fun formatTwoFavoriteWithoutCollectionStatusUsesDefault() {
        val v2 = """{"format":2,"app":"fuwanime","createdAt":1,"favorites":[{"animeId":"a1","title":"Old","poster":"p","cover":"c","type":"TV","status":"ONGOING","year":"2020","genres":"","addedAt":3}],"history":[],"watchDaily":[],"searches":[],"settings":[],"mangaFavorites":[],"mangaHistory":[]}"""
        val r = BackupCodec.decode(v2) as BackupResult.Ok
        assertEquals("PLANNED", r.file.favorites.single().collectionStatus)
    }

    @Test fun roundTripCurrentFormat() {
        val json = BackupCodec.encode(file)
        assertTrue(json.contains("\"format\":3"))
        assertEquals(BackupResult.Ok(file), BackupCodec.decode(json))
    }

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
        assertFalse(isBackedUpSetting("home_last_open_ms"))
        assertFalse(isBackedUpSetting("notify_baseline_9"))
        assertFalse(isBackedUpSetting("notify_release_9"))
        assertFalse(isBackedUpSetting("notify_manga_9"))
        assertFalse(isBackedUpSetting("download_baseline_9"))
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
        assertTrue(settingTypeAccepted("notify_ongoing_anime", "boolean"))
        assertFalse(settingTypeAccepted("notify_new_chapters", "int"))
        assertTrue(settingTypeAccepted("some_future_key", "string"))
        assertFalse(settingTypeAccepted("theme_mode", "blob"))
    }
}
