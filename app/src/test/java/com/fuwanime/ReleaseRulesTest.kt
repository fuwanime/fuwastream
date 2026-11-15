package com.fuwanime

import com.fuwanime.data.notify.episodeNumber
import com.fuwanime.data.notify.isNewerRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRulesTest {

    @Test fun episodeNumberParses() {
        assertEquals(5.0, episodeNumber("Episode 5")!!, 0.0)
        assertEquals(12.5, episodeNumber(" Episode 12.5 ")!!, 0.0)
        assertEquals(7.0, episodeNumber("Ep 7")!!, 0.0)
    }

    @Test fun episodeNumberNullWithoutDigit() {
        assertNull(episodeNumber(""))
        assertNull(episodeNumber("Movie"))
    }

    @Test fun isNewerReleaseSeedsOnNull() = assertFalse(isNewerRelease(3.0, null))

    @Test fun isNewerReleaseTrueOnIncrease() = assertTrue(isNewerRelease(4.0, 3.0))

    @Test fun isNewerReleaseFalseOnSameOrOlder() {
        assertFalse(isNewerRelease(3.0, 3.0))
        assertFalse(isNewerRelease(2.0, 3.0))
    }
}
