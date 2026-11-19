package com.fuwanime.ui.home

/**
 * Legacy full-bleed hero height in dp: the 540dp design grows with large font scales so its text and
 * button never get squeezed out. Kept stable for VisualFixesTest; the redesigned home carousel uses
 * [heroCardHeightDp] instead.
 */
fun heroHeightDp(fontScale: Float): Float = 540f + 140f * (fontScale - 1f).coerceAtLeast(0f)

/**
 * M3 Expressive hero card height in dp: the 252dp tonal card (poster + title + actions) grows with
 * large font scales so the title and the Tonton button never get squeezed out.
 */
fun heroCardHeightDp(fontScale: Float): Float = 252f + 150f * (fontScale - 1f).coerceAtLeast(0f)
