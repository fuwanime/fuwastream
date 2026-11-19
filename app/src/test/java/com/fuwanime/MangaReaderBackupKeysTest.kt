package com.fuwanime

import com.fuwanime.data.backup.settingTypeAccepted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The new keep-screen-on reader pref must survive a backup round-trip with its stored type. */
class MangaReaderBackupKeysTest {
    @Test
    fun keepScreenOnIsAcceptedAsBoolean() {
        assertTrue(settingTypeAccepted("manga_keep_screen_on", "boolean"))
    }

    @Test
    fun keepScreenOnRejectsWrongTypes() {
        assertFalse(settingTypeAccepted("manga_keep_screen_on", "string"))
        assertFalse(settingTypeAccepted("manga_keep_screen_on", "int"))
    }
}
