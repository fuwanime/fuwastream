package com.fuwanime.ui.detail

/** WCAG 2.x relative luminance of an ARGB colour (alpha ignored), 0 (black) … 1 (white). */
fun relativeLuminance(color: Int): Double = TODO()

/** A dark colour from the poster for the Detail gradient: dark vibrant → vibrant → dominant, darkened to luminance ≤ 0.25. */
fun posterTint(darkVibrant: Int?, vibrant: Int?, dominant: Int?): Int? = TODO()

/** [color] mixed with white until its luminance reaches [target]; for the play button. */
fun lighten(color: Int, target: Double = 0.45): Int = TODO()

/** White or black, whichever contrasts more with [color]. */
fun onColorFor(color: Int): Int = TODO()
