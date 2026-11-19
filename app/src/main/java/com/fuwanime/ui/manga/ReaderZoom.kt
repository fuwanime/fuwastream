package com.fuwanime.ui.manga

/** Zoom is clamped so the page never shrinks below its fitted size nor grows past this factor. */
const val READER_MIN_ZOOM = 1f
const val READER_MAX_ZOOM = 4f
/** A double tap zooms to this factor, centred on the tap. */
const val READER_DOUBLE_TAP_ZOOM = 2.5f

/** Zoom + pan of the reader content, in viewport pixels. */
data class ZoomState(
    val scale: Float = READER_MIN_ZOOM,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

fun clampZoom(scale: Float): Float = scale.coerceIn(READER_MIN_ZOOM, READER_MAX_ZOOM)

/** How far the content may be panned in each direction; zero while it fits the viewport. */
data class PanBounds(val maxX: Float, val maxY: Float)

fun panBounds(
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    contentWidth: Float = viewportWidth,
    contentHeight: Float = viewportHeight,
): PanBounds {
    val maxX = ((contentWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((contentHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)
    return PanBounds(maxX, maxY)
}

fun clampPan(
    state: ZoomState,
    viewportWidth: Float,
    viewportHeight: Float,
    contentWidth: Float = viewportWidth,
    contentHeight: Float = viewportHeight,
): ZoomState {
    val bounds = panBounds(state.scale, viewportWidth, viewportHeight, contentWidth, contentHeight)
    return state.copy(
        offsetX = state.offsetX.coerceIn(-bounds.maxX, bounds.maxX),
        offsetY = state.offsetY.coerceIn(-bounds.maxY, bounds.maxY),
    )
}

/**
 * Applies one pinch step: the new scale, plus the pan needed to keep the centroid point fixed on screen.
 * Result is clamped, so zooming back to 1x also resets the pan.
 */
fun applyPinch(
    state: ZoomState,
    zoomChange: Float,
    panX: Float,
    panY: Float,
    centroidX: Float,
    centroidY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ZoomState {
    val scale = clampZoom(state.scale * zoomChange)
    val cX = centroidX - viewportWidth / 2f
    val cY = centroidY - viewportHeight / 2f
    val next = state.copy(
        scale = scale,
        offsetX = state.offsetX + panX + cX * (1f - scale),
        offsetY = state.offsetY + panY + cY * (1f - scale),
    )
    return clampPan(next, viewportWidth, viewportHeight)
}

/** Dragging only pans while zoomed in; at 1x the gesture belongs to the pager/scroll. */
fun applyPan(
    state: ZoomState,
    dx: Float,
    dy: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ZoomState {
    if (state.scale <= READER_MIN_ZOOM) return state.copy(offsetX = 0f, offsetY = 0f)
    return clampPan(state.copy(offsetX = state.offsetX + dx, offsetY = state.offsetY + dy), viewportWidth, viewportHeight)
}

/** Double tap zooms to [READER_DOUBLE_TAP_ZOOM] at the tap point; a second tap resets to 1x. */
fun doubleTapZoom(
    state: ZoomState,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ZoomState {
    if (state.scale > READER_MIN_ZOOM) return ZoomState()
    val cX = tapX - viewportWidth / 2f
    val cY = tapY - viewportHeight / 2f
    val next = ZoomState(
        scale = READER_DOUBLE_TAP_ZOOM,
        offsetX = cX * (1f - READER_DOUBLE_TAP_ZOOM),
        offsetY = cY * (1f - READER_DOUBLE_TAP_ZOOM),
    )
    return clampPan(next, viewportWidth, viewportHeight)
}

/** The on-screen size of content with [aspectRatio] (width/height) fitted into the viewport, letterboxed. */
data class ContentBounds(val width: Float, val height: Float)

fun fitContentBounds(viewportWidth: Float, viewportHeight: Float, aspectRatio: Float): ContentBounds {
    if (aspectRatio.isNaN() || aspectRatio <= 0f) return ContentBounds(viewportWidth, viewportHeight)
    val base = minOf(viewportWidth, viewportHeight)
    return if (aspectRatio >= 1f) ContentBounds(base, base / aspectRatio) else ContentBounds(base * aspectRatio, base)
}
