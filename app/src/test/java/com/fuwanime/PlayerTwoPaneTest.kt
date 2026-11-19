package com.fuwanime

import android.content.pm.ActivityInfo
import com.fuwanime.ui.player.TWO_PANE_MIN_WIDTH_DP
import com.fuwanime.ui.player.exitFullscreenRequestedOrientation
import com.fuwanime.ui.player.isFullscreen
import com.fuwanime.ui.player.isTabletWindow
import com.fuwanime.ui.player.useTwoPanePlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure layout/orientation policy for the tablet two-pane player. Plain JVM, no Robolectric. */
class PlayerTwoPaneTest {

    // --- isTabletWindow: shortest side >= 600dp (same threshold as the nav rail) ---
    @Test fun phonePortraitIsNotTablet() = assertFalse(isTabletWindow(411, 914))
    @Test fun phoneLandscapeIsNotTablet() = assertFalse(isTabletWindow(914, 411))
    @Test fun tabletPortraitIsTablet() = assertTrue(isTabletWindow(800, 1280))
    @Test fun tabletLandscapeIsTablet() = assertTrue(isTabletWindow(1280, 800))
    @Test fun shortestSideBoundary() {
        assertFalse(isTabletWindow(1280, 599))
        assertTrue(isTabletWindow(1280, 600))
    }

    // --- useTwoPanePlayer: expanded width AND landscape AND tablet ---
    @Test fun tabletLandscapeTwoPane() = assertTrue(useTwoPanePlayer(1280, 800))
    @Test fun tabletLandscapeAtBreakpoint() = assertTrue(useTwoPanePlayer(TWO_PANE_MIN_WIDTH_DP, 600))
    @Test fun tabletPortraitNotTwoPane() = assertFalse(useTwoPanePlayer(800, 1280))
    @Test fun widePhoneLandscapeNotTwoPane() = assertFalse(useTwoPanePlayer(914, 411))
    @Test fun narrowLandscapeNotTwoPane() = assertFalse(useTwoPanePlayer(839, 600))
    @Test fun squareWindowNotTwoPane() = assertFalse(useTwoPanePlayer(900, 900))

    // --- isFullscreen: tablets in landscape are NOT auto-fullscreen (two-pane instead) ---
    @Test fun phoneLandscapeStillAutoFullscreen() = assertTrue(isFullscreen(landscape = true, manual = null, inPip = false))
    @Test fun tabletLandscapeIsNotAutoFullscreen() =
        assertFalse(isFullscreen(landscape = true, manual = null, inPip = false, twoPaneCapable = true))
    @Test fun tabletManualFullscreenWins() =
        assertTrue(isFullscreen(landscape = true, manual = true, inPip = false, twoPaneCapable = true))
    @Test fun tabletManualExitStaysNonFullscreen() =
        assertFalse(isFullscreen(landscape = true, manual = false, inPip = false, twoPaneCapable = true))
    @Test fun tabletPortraitNeverAutoFullscreen() =
        assertFalse(isFullscreen(landscape = false, manual = null, inPip = false, twoPaneCapable = true))
    @Test fun pipStillNeverFullscreen() =
        assertFalse(isFullscreen(landscape = true, manual = true, inPip = true, twoPaneCapable = true))

    // --- exiting fullscreen must not force portrait on a tablet ---
    @Test fun phoneExitFullscreenForcesPortrait() =
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, exitFullscreenRequestedOrientation(tablet = false))
    @Test fun tabletExitFullscreenLeavesOrientationUnspecified() =
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, exitFullscreenRequestedOrientation(tablet = true))
}
