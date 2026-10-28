package com.fuwanime

import com.fuwanime.ui.motion.StaggerState
import com.fuwanime.ui.motion.headerStillShown
import com.fuwanime.ui.motion.isReduceMotion
import com.fuwanime.ui.motion.sharedBackdropKey
import com.fuwanime.ui.motion.sharedEpisodeKey
import com.fuwanime.ui.motion.sharedPosterKey
import com.fuwanime.ui.motion.staggerDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun sharedKeys_includeOriginSoTheSameAnimeInTwoRowsDoesNotClash() {
        assertEquals("poster-123-home-popular", sharedPosterKey("123", "home-popular"))
        assertNotEquals(sharedPosterKey("123", "home-popular"), sharedPosterKey("123", "home-season"))
        assertEquals("episode-9-detail", sharedEpisodeKey("9", "detail"))
        assertNotEquals(sharedPosterKey("9", "detail"), sharedEpisodeKey("9", "detail"))
        assertEquals("backdrop-9-e1", sharedBackdropKey("9", "e1"))
        assertNotEquals(sharedBackdropKey("9", "e1"), sharedPosterKey("9", "e1"))
    }

    @Test
    fun staggerDelay_growsPerItemAndCaps() {
        assertEquals(0, staggerDelay(0))
        assertEquals(35, staggerDelay(1))
        assertEquals(280, staggerDelay(8))
        assertEquals(280, staggerDelay(40))
        assertEquals(0, staggerDelay(-3))
    }

    @Test
    fun staggerState_animatesEachEarlyItemOnlyOnce() {
        val state = StaggerState()
        assertTrue(state.shouldAnimate("a", 0, elapsedMs = 0))
        assertFalse(state.shouldAnimate("a", 0, elapsedMs = 10)) // scrolled back into view: no replay
        assertTrue(state.shouldAnimate("b", 11, elapsedMs = 100))
        assertFalse(state.shouldAnimate("c", 12, elapsedMs = 100)) // beyond the first screenful
    }

    @Test
    fun staggerState_itemsComposedLaterByScrollingAppearInstantly() {
        val state = StaggerState()
        assertTrue(state.shouldAnimate("a", 0, elapsedMs = 0))
        assertFalse(state.shouldAnimate("d", 3, elapsedMs = 2_000))
    }

    @Test
    fun staggerState_survivesSaveAndRestore() {
        val state = StaggerState()
        state.shouldAnimate("a", 0, elapsedMs = 0)
        val restored = StaggerState.restore(state.save())
        assertFalse(restored.shouldAnimate("a", 0, elapsedMs = 0)) // back from detail: no replay
        assertTrue(restored.shouldAnimate("b", 1, elapsedMs = 0))
    }

    @Test
    fun headerStillShown_onlyNearTheTopOfTheList() {
        assertTrue(headerStillShown(firstVisibleIndex = 0, firstVisibleOffsetPx = 0, maxOffsetPx = 400))
        assertTrue(headerStillShown(firstVisibleIndex = 0, firstVisibleOffsetPx = 399, maxOffsetPx = 400))
        assertFalse(headerStillShown(firstVisibleIndex = 0, firstVisibleOffsetPx = 400, maxOffsetPx = 400))
        assertFalse(headerStillShown(firstVisibleIndex = 3, firstVisibleOffsetPx = 0, maxOffsetPx = 400))
    }

    @Test
    fun reduceMotion_onlyWhenAnimationsAreOff() {
        assertTrue(isReduceMotion(0f))
        assertFalse(isReduceMotion(1f))
        assertFalse(isReduceMotion(0.5f))
    }
}
