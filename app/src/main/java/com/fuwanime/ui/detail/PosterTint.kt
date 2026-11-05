package com.fuwanime.ui.detail

import kotlin.math.pow

private fun channel(c: Int): Double {
    val s = c / 255.0
    return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}

private fun argb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/** WCAG 2.x relative luminance of an ARGB colour (alpha ignored), 0 (black) … 1 (white). */
fun relativeLuminance(color: Int): Double =
    0.2126 * channel(color shr 16 and 0xFF) + 0.7152 * channel(color shr 8 and 0xFF) + 0.0722 * channel(color and 0xFF)

private fun scaled(color: Int, f: Double): Int =
    argb(((color shr 16 and 0xFF) * f).toInt(), ((color shr 8 and 0xFF) * f).toInt(), ((color and 0xFF) * f).toInt())

private fun mixedWithWhite(color: Int, t: Double): Int {
    fun mix(c: Int) = (c + (255 - c) * t).toInt()
    return argb(mix(color shr 16 and 0xFF), mix(color shr 8 and 0xFF), mix(color and 0xFF))
}

/** A dark colour from the poster for the Detail gradient: dark vibrant → vibrant → dominant, darkened to luminance ≤ 0.25. */
fun posterTint(darkVibrant: Int?, vibrant: Int?, dominant: Int?): Int? {
    val base = (darkVibrant ?: vibrant ?: dominant) ?: return null
    if (relativeLuminance(base) <= 0.25) return base or (0xFF shl 24)
    // Scale all channels by one factor (keeps the hue); binary search for the largest factor that is dark enough.
    var lo = 0.0
    var hi = 1.0
    repeat(20) {
        val mid = (lo + hi) / 2
        if (relativeLuminance(scaled(base, mid)) <= 0.25) lo = mid else hi = mid
    }
    return scaled(base, lo)
}

/** [color] mixed with white until its luminance reaches [target]; for the play button. */
fun lighten(color: Int, target: Double = 0.45): Int {
    if (relativeLuminance(color) >= target) return color or (0xFF shl 24)
    var lo = 0.0
    var hi = 1.0
    repeat(20) {
        val mid = (lo + hi) / 2
        if (relativeLuminance(mixedWithWhite(color, mid)) >= target) hi = mid else lo = mid
    }
    return mixedWithWhite(color, hi)
}

private fun contrast(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

/** White or black, whichever contrasts more with [color]. */
fun onColorFor(color: Int): Int {
    val l = relativeLuminance(color)
    return if (contrast(l, 1.0) >= contrast(l, 0.0)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
}
