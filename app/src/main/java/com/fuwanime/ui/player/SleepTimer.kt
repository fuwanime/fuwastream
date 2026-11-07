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
    END_OF_EPISODE(R.string.watch_sleep_end, null),
}

/** Wall-clock time at which playback pauses; null when the option is not time-based. */
fun sleepDeadline(option: SleepOption, nowMs: Long): Long? = option.minutes?.let { nowMs + it * 60_000L }

/**
 * "Akhir episode" means stop there, so the next-episode countdown must not start; neither may it once a
 * timed sleep has fired (until the user plays again).
 */
fun autoPlayAllowed(autoPlayNext: Boolean, sleep: SleepOption, sleepExpired: Boolean = false): Boolean =
    autoPlayNext && sleep != SleepOption.END_OF_EPISODE && !sleepExpired

/** Speed while the video is held down. */
fun boostedSpeed(base: Float): Float = maxOf(base, 2f)
