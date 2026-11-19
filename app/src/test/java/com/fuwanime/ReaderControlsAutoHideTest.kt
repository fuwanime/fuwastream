package com.fuwanime

import com.fuwanime.ui.manga.READER_CONTROLS_HIDE_DELAY_MS
import com.fuwanime.ui.manga.ReaderControlsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlsAutoHideTest {
    @Test
    fun controlsHideExactlyAfterTheDelay() {
        val s = ReaderControlsState(visible = true, lastInteractionMs = 1_000)
        assertTrue(s.tick(1_000 + READER_CONTROLS_HIDE_DELAY_MS - 1).visible)
        assertFalse(s.tick(1_000 + READER_CONTROLS_HIDE_DELAY_MS).visible)
    }

    @Test
    fun anInteractionResetsTheHideTimer() {
        val s = ReaderControlsState(visible = true, lastInteractionMs = 0)
        val touched = s.touched(2_000)
        assertTrue(touched.visible)
        assertEquals(2_000, touched.lastInteractionMs)
        assertTrue(touched.tick(2_000 + READER_CONTROLS_HIDE_DELAY_MS - 1).visible)
        assertFalse(touched.tick(2_000 + READER_CONTROLS_HIDE_DELAY_MS).visible)
    }

    @Test
    fun tappingTogglesVisibilityAndKeepsTheTimerFresh() {
        val visible = ReaderControlsState(visible = true, lastInteractionMs = 10)
        val hidden = visible.toggled(5_000)
        assertFalse(hidden.visible)
        assertEquals(5_000, hidden.lastInteractionMs)

        val shown = hidden.toggled(6_000)
        assertTrue(shown.visible)
        assertEquals(6_000, shown.lastInteractionMs)
        assertFalse(shown.tick(6_000 + READER_CONTROLS_HIDE_DELAY_MS).visible)
    }

    @Test
    fun theAccessibilityShowActionRevealsHiddenControls() {
        val hidden = ReaderControlsState(visible = false, lastInteractionMs = 100)
        val shown = hidden.shown(7_000)
        assertTrue(shown.visible)
        assertEquals(7_000, shown.lastInteractionMs)
        assertFalse(shown.tick(7_000 + READER_CONTROLS_HIDE_DELAY_MS).visible)
    }

    @Test
    fun tickingHiddenControlsDoesNotBringThemBack() {
        val hidden = ReaderControlsState(visible = false, lastInteractionMs = 0)
        assertFalse(hidden.tick(999_999).visible)
    }

    @Test
    fun remainingMsCountsDownAndNeverGoesNegative() {
        val s = ReaderControlsState(visible = true, lastInteractionMs = 1_000)
        assertEquals(READER_CONTROLS_HIDE_DELAY_MS, s.remainingMs(1_000))
        assertEquals(READER_CONTROLS_HIDE_DELAY_MS - 1_000, s.remainingMs(2_000))
        assertEquals(0, s.remainingMs(1_000 + READER_CONTROLS_HIDE_DELAY_MS + 5_000))
        assertEquals(0, ReaderControlsState(visible = false, lastInteractionMs = 0).remainingMs(999_999))
    }
}
