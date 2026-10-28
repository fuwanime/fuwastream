package com.fuwanime.ui.player

enum class SleepOption(val label: String, val minutes: Int?) {
    OFF("Mati", null),
    M15("15 menit", 15),
    M30("30 menit", 30),
    M45("45 menit", 45),
    M60("60 menit", 60),
    END_OF_EPISODE("Akhir episode", null),
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
