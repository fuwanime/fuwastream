package com.fuwanime

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fuwanime.data.local.VideoResize
import com.fuwanime.data.local.toSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSettingsTest {
    @Test fun defaults() {
        val s = emptyPreferences().toSettings()
        assertFalse(s.backgroundAudio)
        assertEquals(VideoResize.FIT, s.videoResize)
    }

    @Test fun readsStoredValues() {
        val s = mutablePreferencesOf(
            booleanPreferencesKey("background_audio") to true,
            stringPreferencesKey("video_resize") to "ZOOM",
        ).toSettings()
        assertTrue(s.backgroundAudio)
        assertEquals(VideoResize.ZOOM, s.videoResize)
    }

    @Test fun unknownResizeFallsBackToFit() =
        assertEquals(VideoResize.FIT, mutablePreferencesOf(stringPreferencesKey("video_resize") to "BOGUS").toSettings().videoResize)
}
