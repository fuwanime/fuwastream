package com.fuwanime

import com.fuwanime.ui.components.posterDescription
import com.fuwanime.ui.detail.lighten
import com.fuwanime.ui.detail.onColorFor
import com.fuwanime.ui.detail.posterTint
import com.fuwanime.ui.detail.relativeLuminance
import com.fuwanime.ui.navigation.navLabelFontScale
import com.fuwanime.ui.navigation.useNavRail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "id")
class VisualA11yTest {
    private val res get() = testResources()

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val indigo = 0xFF1A237E.toInt()
    private val amber = 0xFFFFD54F.toInt()
    private val teal = 0xFF00695C.toInt()

    @Test fun luminanceEnds() {
        assertEquals(0.0, relativeLuminance(black), 1e-6)
        assertEquals(1.0, relativeLuminance(white), 1e-6)
    }

    @Test fun tintPrefersDarkVibrantThenVibrantThenDominant() {
        assertEquals(indigo, posterTint(indigo, amber, teal))
        assertEquals(teal, posterTint(null, null, teal))
        assertNull(posterTint(null, null, null))
    }

    @Test fun tintIsDarkEnough() {
        val t = posterTint(null, amber, null)!!
        assertTrue(relativeLuminance(t) <= 0.25)
        // Same hue: red still the largest channel, blue the smallest.
        assertTrue((t shr 16 and 0xFF) >= (t and 0xFF))
        assertEquals(indigo, posterTint(indigo, null, null))
    }

    @Test fun lightenReachesTarget() {
        val l = relativeLuminance(lighten(indigo))
        assertTrue(l >= 0.45)
        assertTrue(l <= 1.0)
    }

    @Test fun onColorPicksReadable() {
        assertEquals(white, onColorFor(indigo))
        assertEquals(black, onColorFor(amber))
    }

    @Test fun descriptionOrder() = assertEquals(
        "Frieren, Episode 5, Baru, 2 baru, dipilih",
        posterDescription(res, "Frieren", "Episode 5", "2 baru", isNew = true, airing = true, selected = true),
    )

    @Test fun descriptionAiringWithoutNew() =
        assertEquals("Frieren, Sedang tayang", posterDescription(res, "Frieren", null, null, isNew = false, airing = true, selected = false))

    @Test fun descriptionSkipsBlankCaption() = assertEquals("Frieren", posterDescription(res, "Frieren", " ", null, false, false, false))

    @Config(sdk = [35], qualifiers = "en")
    @Test fun descriptionEnglish() = assertEquals(
        "Frieren, Episode 5, New, 2 new, selected",
        posterDescription(res, "Frieren", "Episode 5", "2 new", isNew = true, airing = true, selected = true),
    )

    @Test fun navLabelScaleCapsAt130Percent() {
        assertEquals(1.0f, navLabelFontScale(1.0f), 0f)
        assertEquals(1.3f, navLabelFontScale(2.0f), 0f)
    }

    @Test fun railThreshold() {
        assertFalse(useNavRail(599))
        assertTrue(useNavRail(600))
    }
}
