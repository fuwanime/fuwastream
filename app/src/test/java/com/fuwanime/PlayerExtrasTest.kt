package com.fuwanime

import com.fuwanime.ui.player.SleepOption
import com.fuwanime.ui.player.autoPlayAllowed
import com.fuwanime.ui.player.boostedSpeed
import com.fuwanime.ui.player.sleepDeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExtrasTest {
    @Test
    fun sleepDeadline_addsMinutesForTimedOptions() {
        val now = 1_000_000L
        assertEquals(now + 15 * 60_000, sleepDeadline(SleepOption.M15, now))
        assertEquals(now + 60 * 60_000, sleepDeadline(SleepOption.M60, now))
        assertNull(sleepDeadline(SleepOption.OFF, now))
        assertNull(sleepDeadline(SleepOption.END_OF_EPISODE, now))
    }

    @Test
    fun autoPlay_isSuppressedBySleepAtEndOfEpisode() {
        assertTrue(autoPlayAllowed(true, SleepOption.OFF))
        assertTrue(autoPlayAllowed(true, SleepOption.M30))
        assertFalse(autoPlayAllowed(true, SleepOption.END_OF_EPISODE))
        assertFalse(autoPlayAllowed(false, SleepOption.OFF))
    }

    @Test
    fun boostedSpeed_isAtLeastDouble() {
        assertEquals(2f, boostedSpeed(1f))
        assertEquals(2f, boostedSpeed(1.5f))
        assertEquals(2f, boostedSpeed(2f))
        assertEquals(2f, boostedSpeed(0.5f))
    }
}
