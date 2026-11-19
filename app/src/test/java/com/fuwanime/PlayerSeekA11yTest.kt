package com.fuwanime

import com.fuwanime.ui.player.seekProgressDescription
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack text for the seek slider, in each shipped locale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "id")
class PlayerSeekA11yTest {
    private val res get() = testResources()

    @Test fun positionOutOfDurationIndonesian() =
        assertEquals("01:23 dari 24:10", seekProgressDescription(res, 83_000, 1_450_000))

    @Test fun hoursFormat() =
        assertEquals("1:05:00 dari 1:30:00", seekProgressDescription(res, 3_900_000, 5_400_000))

    @Test fun negativePositionClampedToZero() =
        assertEquals("00:00 dari 01:00", seekProgressDescription(res, -5_000, 60_000))

    @Test fun unknownDurationStaysZero() =
        assertEquals("00:00 dari 00:00", seekProgressDescription(res, 12_000, 0))

    @Config(sdk = [35], application = android.app.Application::class, qualifiers = "en")
    @Test fun positionOutOfDurationEnglish() =
        assertEquals("01:23 of 24:10", seekProgressDescription(res, 83_000, 1_450_000))
}
