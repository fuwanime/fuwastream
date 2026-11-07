package com.fuwanime

import android.app.Application
import com.fuwanime.data.local.BackupPrefs
import com.fuwanime.ui.settings.backupErrorLabel
import com.fuwanime.ui.settings.backupStatusLabel
import com.fuwanime.ui.stats.recapMonthYear
import com.fuwanime.ui.stats.weekdayLabel
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "id", application = Application::class)
class BackupUiTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val id = Locale("id")
    private val at = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, 7, 14, 5) }.timeInMillis

    @Test fun folderErrorCodeWins() =
        assertEquals("Folder tidak bisa diakses — pilih ulang", backupStatusLabel(testResources(), BackupPrefs("uri", true, at, "folder", false), utc, id))

    @Test fun otherErrorCodeCarriesDetail() =
        assertEquals("Gagal mencadangkan: disk penuh", backupErrorLabel(testResources(), "other:disk penuh"))

    @Test fun legacySavedMessageIsShownAsIs() =
        assertEquals("Folder tidak bisa diakses — pilih ulang", backupErrorLabel(testResources(), "Folder tidak bisa diakses — pilih ulang"))

    @Test fun lastBackupFormatted() = assertEquals("Terakhir: 7 Nov 2026 14.05", backupStatusLabel(testResources(), BackupPrefs("uri", true, at, null, false), utc, id))

    @Test fun neverBackedUp() = assertEquals("Belum pernah", backupStatusLabel(testResources(), BackupPrefs(null, true, null, null, false), utc, id))

    @Test fun recapMonthIsIndonesian() = assertEquals("November 2026", recapMonthYear(id, at))

    @Test fun weekdayFollowsGivenLocale() {
        val monday = 20_766 // epoch day of 2026-11-09, a Monday
        assertEquals("Sen", weekdayLabel(monday, id))
        assertEquals("Mon", weekdayLabel(monday, Locale.ENGLISH))
    }

    @Config(qualifiers = "en")
    @Test fun englishStatusAndErrors() {
        val res = testResources()
        assertEquals("Folder is not accessible — pick it again", backupErrorLabel(res, "folder"))
        assertEquals("Last: 7 Nov 2026 14:05", backupStatusLabel(res, BackupPrefs("uri", true, at, null, false), utc, Locale.ENGLISH))
        assertEquals("Never", backupStatusLabel(res, BackupPrefs(null, true, null, null, false), utc, Locale.ENGLISH))
    }
}
