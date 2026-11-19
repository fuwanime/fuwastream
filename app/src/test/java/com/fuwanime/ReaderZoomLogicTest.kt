package com.fuwanime

import com.fuwanime.ui.manga.READER_DOUBLE_TAP_ZOOM
import com.fuwanime.ui.manga.READER_MAX_ZOOM
import com.fuwanime.ui.manga.READER_MIN_ZOOM
import com.fuwanime.ui.manga.ZoomState
import com.fuwanime.ui.manga.applyPan
import com.fuwanime.ui.manga.applyPinch
import com.fuwanime.ui.manga.clampPan
import com.fuwanime.ui.manga.clampZoom
import com.fuwanime.ui.manga.doubleTapZoom
import com.fuwanime.ui.manga.fitContentBounds
import com.fuwanime.ui.manga.panBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderZoomLogicTest {
    private fun assertState(scale: Float, x: Float, y: Float, actual: ZoomState) {
        assertEquals("scale", scale, actual.scale, 0.001f)
        assertEquals("x", x, actual.offsetX, 0.001f)
        assertEquals("y", y, actual.offsetY, 0.001f)
    }

    @Test
    fun clampZoomKeepsTheReaderBetweenOneAndMax() {
        assertEquals(READER_MIN_ZOOM, clampZoom(0.5f), 0f)
        assertEquals(READER_MAX_ZOOM, clampZoom(99f), 0f)
        assertEquals(2f, clampZoom(2f), 0f)
    }

    @Test
    fun panBoundsAreZeroUntilContentIsLargerThanViewport() {
        assertEquals(0f, panBounds(1f, 100f, 200f).maxX, 0f)
        assertEquals(0f, panBounds(1f, 100f, 200f).maxY, 0f)
        assertEquals(50f, panBounds(2f, 100f, 200f).maxX, 0f)
        assertEquals(100f, panBounds(2f, 100f, 200f).maxY, 0f)
    }

    @Test
    fun panBoundsUseTheFittedContentSize() {
        val b = panBounds(2f, 100f, 200f, contentWidth = 100f, contentHeight = 100f)
        assertEquals(50f, b.maxX, 0f)
        assertEquals(0f, b.maxY, 0f)
    }

    @Test
    fun clampPanCentersContentWhenNotZoomed() {
        assertState(1f, 0f, 0f, clampPan(ZoomState(1f, 999f, -999f), 100f, 200f))
    }

    @Test
    fun clampPanStopsAtTheEdges() {
        assertState(2f, 50f, -100f, clampPan(ZoomState(2f, 999f, -999f), 100f, 200f))
    }

    @Test
    fun clampPanRespectsFittedContentBounds() {
        assertState(
            2f, 50f, 0f,
            clampPan(ZoomState(2f, 999f, 999f), 100f, 200f, contentWidth = 100f, contentHeight = 100f),
        )
    }

    @Test
    fun pinchAtCenterZoomsWithoutMovingTheContent() {
        assertState(2f, 0f, 0f, applyPinch(ZoomState(), zoomChange = 2f, panX = 0f, panY = 0f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun pinchOffCenterKeepsTheFingerPointInPlace() {
        // c = centroid - center = (25, 0); ratio 2 => offset = c - c*2 = (-25, 0)
        assertState(2f, -25f, 0f, applyPinch(ZoomState(), zoomChange = 2f, panX = 0f, panY = 0f, centroidX = 75f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun pinchAddsPanAndClampsToSensibleBounds() {
        assertState(2f, 20f, -10f, applyPinch(ZoomState(), zoomChange = 2f, panX = 20f, panY = -10f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
        assertState(2f, 50f, 50f, applyPinch(ZoomState(), zoomChange = 2f, panX = 500f, panY = 500f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun pinchNeverGoesBelowOneOrAboveMax() {
        assertState(1f, 0f, 0f, applyPinch(ZoomState(2f), zoomChange = 0.1f, panX = 0f, panY = 0f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
        assertState(READER_MAX_ZOOM, 0f, 0f, applyPinch(ZoomState(), zoomChange = 99f, panX = 0f, panY = 0f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun zoomingBackOutResetsThePan() {
        assertState(1f, 0f, 0f, applyPinch(ZoomState(2f, 30f, 40f), zoomChange = 0.5f, panX = 0f, panY = 0f, centroidX = 50f, centroidY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun applyPanOnlyMovesWhenZoomed() {
        assertState(1f, 0f, 0f, applyPan(ZoomState(), dx = 30f, dy = 30f, viewportWidth = 100f, viewportHeight = 100f))
        assertState(2f, 30f, -30f, applyPan(ZoomState(2f), dx = 30f, dy = -30f, viewportWidth = 100f, viewportHeight = 100f))
        assertState(2f, 50f, 50f, applyPan(ZoomState(2f), dx = 500f, dy = 500f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun doubleTapZoomsToTapAndSecondTapResets() {
        val zoomed = doubleTapZoom(ZoomState(), tapX = 50f, tapY = 50f, viewportWidth = 100f, viewportHeight = 100f)
        assertState(READER_DOUBLE_TAP_ZOOM, 0f, 0f, zoomed)
        assertState(1f, 0f, 0f, doubleTapZoom(zoomed, tapX = 50f, tapY = 50f, viewportWidth = 100f, viewportHeight = 100f))
    }

    @Test
    fun doubleTapNearTheEdgeStaysWithinBounds() {
        val zoomed = doubleTapZoom(ZoomState(), tapX = 75f, tapY = 50f, viewportWidth = 100f, viewportHeight = 100f)
        assertState(READER_DOUBLE_TAP_ZOOM, -37.5f, 0f, zoomed)
        val corner = doubleTapZoom(ZoomState(), tapX = 0f, tapY = 0f, viewportWidth = 100f, viewportHeight = 100f)
        assertState(READER_DOUBLE_TAP_ZOOM, 75f, 75f, corner)
    }

    @Test
    fun fitContentBoundsLetterboxesWithoutCropping() {
        val tall = fitContentBounds(viewportWidth = 100f, viewportHeight = 200f, aspectRatio = 0.5f)
        assertEquals(50f, tall.width, 0f)
        assertEquals(100f, tall.height, 0f)

        val wide = fitContentBounds(viewportWidth = 100f, viewportHeight = 200f, aspectRatio = 2f)
        assertEquals(100f, wide.width, 0f)
        assertEquals(50f, wide.height, 0f)

        val missing = fitContentBounds(viewportWidth = 100f, viewportHeight = 200f, aspectRatio = Float.NaN)
        assertEquals(100f, missing.width, 0f)
        assertEquals(200f, missing.height, 0f)
    }
}
