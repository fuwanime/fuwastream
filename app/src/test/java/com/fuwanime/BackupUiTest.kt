package com.fuwanime

import com.fuwanime.data.local.BackupPrefs
import com.fuwanime.ui.settings.backupStatusLabel
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupUiTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val id = Locale("id")
    private val at = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, 7, 14, 5) }.timeInMillis

    @Test fun errorWins() =
        assertEquals("Folder tidak bisa diakses — pilih ulang", backupStatusLabel(BackupPrefs("uri", true, at, "Folder tidak bisa diakses — pilih ulang", false), utc, id))

    @Test fun lastBackupFormatted() = assertEquals("Terakhir: 7 Nov 2026 14.05", backupStatusLabel(BackupPrefs("uri", true, at, null, false), utc, id))

    @Test fun neverBackedUp() = assertEquals("Belum pernah", backupStatusLabel(BackupPrefs(null, true, null, null, false), utc, id))
}
