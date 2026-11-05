package com.fuwanime

import com.fuwanime.ui.detail.washAlpha
import com.fuwanime.ui.home.heroHeightDp
import com.fuwanime.ui.navigation.navContentPadding
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualFixesTest {
    @Test fun barModeUsesBarHeight() = assertEquals(80f, navContentPadding(rail = false, tabVisible = true, barHeightDp = 80f, systemNavInsetDp = 48f), 0f)
    @Test fun railModeUsesSystemNavInset() = assertEquals(48f, navContentPadding(rail = true, tabVisible = true, barHeightDp = 80f, systemNavInsetDp = 48f), 0f)
    @Test fun noTabNoPadding() {
        assertEquals(0f, navContentPadding(rail = false, tabVisible = false, barHeightDp = 80f, systemNavInsetDp = 48f), 0f)
        assertEquals(0f, navContentPadding(rail = true, tabVisible = false, barHeightDp = 80f, systemNavInsetDp = 48f), 0f)
    }

    @Test fun washOnlyOnDarkBackgrounds() {
        assertEquals(0.55f, washAlpha(0.004), 0f) // #0B0A0F
        assertEquals(0f, washAlpha(0.93), 0f) // #FDF7FF
    }

    @Test fun heroGrowsWithFontScale() {
        assertEquals(540f, heroHeightDp(1f), 0f)
        assertEquals(540f, heroHeightDp(0.85f), 0f)
        assertEquals(680f, heroHeightDp(2f), 0.01f)
    }
}
