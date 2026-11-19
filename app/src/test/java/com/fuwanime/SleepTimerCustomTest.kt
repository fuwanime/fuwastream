package com.fuwanime

import com.fuwanime.ui.player.CUSTOM_SLEEP_DEFAULT_MINUTES
import com.fuwanime.ui.player.CUSTOM_SLEEP_MAX_MINUTES
import com.fuwanime.ui.player.CUSTOM_SLEEP_MIN_MINUTES
import com.fuwanime.ui.player.SleepOption
import com.fuwanime.ui.player.autoPlayAllowed
import com.fuwanime.ui.player.sleepDeadline
import com.fuwanime.ui.player.sleepRemainingMs
import com.fuwanime.ui.player.snapCustomMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The custom sleep duration: slider grid, deadline arithmetic and the live countdown helper. */
class SleepTimerCustomTest {
    private val now = 1_000_000L

    @Test
    fun customDeadline_usesPickedMinutes() {
        assertEquals(now + 25 * 60_000L, sleepDeadline(SleepOption.CUSTOM, now, 25))
        assertEquals(now + 180 * 60_000L, sleepDeadline(SleepOption.CUSTOM, now, 180))
    }

    @Test
    fun customDeadline_defaultsWhenNoMinutesGiven() {
        assertEquals(now + CUSTOM_SLEEP_DEFAULT_MINUTES * 60_000L, sleepDeadline(SleepOption.CUSTOM, now))
    }

    @Test
    fun customDeadline_clampsToSliderRange() {
        assertEquals(now + CUSTOM_SLEEP_MIN_MINUTES * 60_000L, sleepDeadline(SleepOption.CUSTOM, now, 0))
        assertEquals(now + CUSTOM_SLEEP_MIN_MINUTES * 60_000L, sleepDeadline(SleepOption.CUSTOM, now, -10))
        assertEquals(now + CUSTOM_SLEEP_MAX_MINUTES * 60_000L, sleepDeadline(SleepOption.CUSTOM, now, 600))
    }

    @Test
    fun presets_ignoreCustomMinutes() {
        assertEquals(now + 15 * 60_000L, sleepDeadline(SleepOption.M15, now, 90))
        assertEquals(now + 60 * 60_000L, sleepDeadline(SleepOption.M60, now, 5))
        assertNull(sleepDeadline(SleepOption.OFF, now, 45))
        assertNull(sleepDeadline(SleepOption.END_OF_EPISODE, now, 45))
    }

    @Test
    fun snap_roundsToFiveMinuteGrid() {
        assertEquals(5, snapCustomMinutes(5))
        assertEquals(5, snapCustomMinutes(7))
        assertEquals(10, snapCustomMinutes(8))
        assertEquals(30, snapCustomMinutes(31))
        assertEquals(180, snapCustomMinutes(180))
        assertEquals(180, snapCustomMinutes(183))
        assertEquals(5, snapCustomMinutes(0))
        assertEquals(180, snapCustomMinutes(1000))
    }

    @Test
    fun remaining_countsDownAndNeverGoesNegative() {
        assertNull(sleepRemainingMs(null, now))
        assertEquals(90_000L, sleepRemainingMs(now + 90_000L, now))
        assertEquals(0L, sleepRemainingMs(now, now))
        assertEquals(0L, sleepRemainingMs(now - 5_000L, now))
    }

    @Test
    fun autoPlay_customBehavesLikeTimedPreset() {
        assertTrue(autoPlayAllowed(true, SleepOption.CUSTOM))
        assertFalse(autoPlayAllowed(true, SleepOption.CUSTOM, sleepExpired = true))
    }
}
