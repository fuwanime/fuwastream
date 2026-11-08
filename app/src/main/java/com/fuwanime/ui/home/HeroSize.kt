package com.fuwanime.ui.home

/** Hero height in dp: the 540dp design grows with large font scales so its text and button never get squeezed out. */
fun heroHeightDp(fontScale: Float): Float = 540f + 140f * (fontScale - 1f).coerceAtLeast(0f)
