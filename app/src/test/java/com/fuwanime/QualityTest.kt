package com.fuwanime

import androidx.media3.common.PlaybackException
import com.fuwanime.data.StreamSource
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.nextLowerThan
import com.fuwanime.ui.player.preferredHeight
import com.fuwanime.ui.player.shouldFallBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityTest {
    private val p1080 = StreamSource("u1080", "1080p", "")
    private val p720 = StreamSource("u720", "720p", "")
    private val p480 = StreamSource("u480", "480p", "")
    private val p360 = StreamSource("u360", "360p", "")
    private val auto = StreamSource("uauto", "Auto", "")
    private val sources = listOf(p1080, p720, p480, p360, auto)

    @Test
    fun nextLowerThan_picksTheNearestLowerUntriedSource() {
        assertEquals(p480, sources.nextLowerThan(p720, emptySet()))
        assertEquals(p360, sources.nextLowerThan(p720, setOf("u480")))
        assertNull(sources.nextLowerThan(p360, emptySet()))
        assertNull(sources.nextLowerThan(auto, emptySet()))
    }

    @Test
    fun preferredHeight_sessionChoiceThenMobileDataThenDefault() {
        val s = AppSettings(preferredQuality = 720, mobileDataQuality = 360)
        assertEquals(1080, preferredHeight(s, metered = true, sessionHeight = 1080))
        assertEquals(360, preferredHeight(s, metered = true, sessionHeight = null))
        assertEquals(720, preferredHeight(s, metered = false, sessionHeight = null))
        assertEquals(720, preferredHeight(s.copy(mobileDataQuality = 0), metered = true, sessionHeight = null))
    }

    @Test
    fun shouldFallBack_onlyForErrorsALowerQualityCanFix() {
        assertTrue(shouldFallBack(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertTrue(shouldFallBack(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(shouldFallBack(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(shouldFallBack(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(shouldFallBack(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
