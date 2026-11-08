package com.fuwanime.ui.navigation

/** Tablets and unfolded foldables get a navigation rail instead of a bottom bar. */
fun useNavRail(widthDp: Int): Boolean = widthDp >= 600

/**
 * Bottom padding tab lists need: the floating bar's height on phones, the system navigation bar under the content
 * when a rail is shown, nothing when no tab is on screen.
 */
fun navContentPadding(rail: Boolean, tabVisible: Boolean, barHeightDp: Float, systemNavInsetDp: Float): Float = when {
    !tabVisible -> 0f
    rail -> systemNavInsetDp
    else -> barHeightDp
}

/** Font scale for bar/rail labels: they sit in a fixed slot under an icon, and past 1.3× "Beranda" wraps mid-word. */
fun navLabelFontScale(fontScale: Float): Float = minOf(fontScale, 1.3f)
