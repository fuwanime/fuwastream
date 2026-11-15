package com.fuwanime.data.notify

/** Slack when comparing episode/chapter numbers, so float noise never re-notifies a title. */
const val RELEASE_EPS = 0.001

private val LABEL_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

/** The number in a feed episode label ("Episode 5", "Ep. 12.5"), or null when the label carries none. */
fun episodeNumber(label: String): Double? =
    LABEL_NUMBER.find(label)?.value?.replace(',', '.')?.toDoubleOrNull()

/** True when [newest] is worth notifying against [baseline]; a null baseline only seeds the first run. */
fun isNewerRelease(newest: Double, baseline: Double?): Boolean =
    baseline != null && newest > baseline + RELEASE_EPS

/** A number as shown in notification text: "5" for 5.0, "12.5" for 12.5. */
fun episodeLabelText(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
