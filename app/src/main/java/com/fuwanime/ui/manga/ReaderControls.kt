package com.fuwanime.ui.manga

/** How long the reader bars stay up after the last interaction before auto-hiding. */
const val READER_CONTROLS_HIDE_DELAY_MS = 3_000L

/**
 * Visibility of the reader's own bars (top/bottom). Pure state + transitions, so the auto-hide
 * behaviour is testable without a clock: every method takes "now" in milliseconds.
 */
data class ReaderControlsState(
    val visible: Boolean = true,
    val lastInteractionMs: Long = 0L,
) {
    /** Hides the bars once the delay has elapsed; hidden bars stay hidden. */
    fun tick(nowMs: Long): ReaderControlsState =
        if (!visible || nowMs - lastInteractionMs >= READER_CONTROLS_HIDE_DELAY_MS) copy(visible = false) else this

    /** Any interaction keeps the bars up and restarts the timer. */
    fun touched(nowMs: Long): ReaderControlsState = copy(visible = true, lastInteractionMs = nowMs)

    /** A tap on the page toggles the bars and restarts the timer either way. */
    fun toggled(nowMs: Long): ReaderControlsState = copy(visible = !visible, lastInteractionMs = nowMs)

    /** The accessibility "show controls" action: reveals hidden bars and restarts the timer. */
    fun shown(nowMs: Long): ReaderControlsState = copy(visible = true, lastInteractionMs = nowMs)

    /** Milliseconds until the bars hide; 0 when already hidden or overdue. */
    fun remainingMs(nowMs: Long): Long =
        if (!visible) 0L else (READER_CONTROLS_HIDE_DELAY_MS - (nowMs - lastInteractionMs)).coerceAtLeast(0L)
}
