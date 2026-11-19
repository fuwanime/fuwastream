package com.fuwanime.ui.player

import androidx.annotation.StringRes
import com.fuwanime.R

/** [label] is 0 for timed options, which show [minutes] through the watch_sleep_minutes plural. */
enum class SleepOption(@StringRes val label: Int, val minutes: Int?) {
    OFF(R.string.watch_sleep_off, null),
    M15(0, 15),
    M30(0, 30),
    M45(0, 45),
    M60(0, 60),
    /** Duration picked on the settings sheet slider; the minutes live in the UI state, not here. */
    CUSTOM(R.string.watch_sleep_custom, null),
    END_OF_EPISODE(R.string.watch_sleep_end, null),
}

/** Bounds and grid of the custom sleep slider, in minutes. */
const val CUSTOM_SLEEP_MIN_MINUTES = 5
const val CUSTOM_SLEEP_MAX_MINUTES = 180
const val CUSTOM_SLEEP_STEP_MINUTES = 5
const val CUSTOM_SLEEP_DEFAULT_MINUTES = 30

/** Wall-clock time at which playback pauses; null when the option is not time-based. */
fun sleepDeadline(option: SleepOption, nowMs: Long, customMinutes: Int = CUSTOM_SLEEP_DEFAULT_MINUTES): Long? {
    val minutes = if (option == SleepOption.CUSTOM) snapCustomMinutes(customMinutes) else option.minutes
    return minutes?.let { nowMs + it * 60_000L }
}

/** A slider value snapped to the 5-minute grid inside the custom range, so the deadline is always a whole slot. */
fun snapCustomMinutes(minutes: Int): Int =
    ((minutes + CUSTOM_SLEEP_STEP_MINUTES / 2) / CUSTOM_SLEEP_STEP_MINUTES * CUSTOM_SLEEP_STEP_MINUTES)
        .coerceIn(CUSTOM_SLEEP_MIN_MINUTES, CUSTOM_SLEEP_MAX_MINUTES)

/** Milliseconds left on an armed sleep timer, never negative; null when no timed sleep is set. */
fun sleepRemainingMs(sleepAtMs: Long?, nowMs: Long): Long? = sleepAtMs?.let { (it - nowMs).coerceAtLeast(0) }

/**
 * "Akhir episode" means stop there, so the next-episode countdown must not start; neither may it once a
 * timed sleep has fired (until the user plays again).
 */
fun autoPlayAllowed(autoPlayNext: Boolean, sleep: SleepOption, sleepExpired: Boolean = false): Boolean =
    autoPlayNext && sleep != SleepOption.END_OF_EPISODE && !sleepExpired

/** Speed while the video is held down; each [level] (0-2, from sliding right) adds 1x, capped at 4x. */
fun boostedSpeed(base: Float, level: Int = 0): Float = minOf(maxOf(base, 2f) + level.coerceIn(0, 2), 4f)
