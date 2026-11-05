package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.SeasonEntry
import com.fuwanime.ui.player.offerNextSeason
import com.fuwanime.ui.player.segmentSourceReady
import com.fuwanime.ui.player.shouldTrySegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentFetchTest {
    @Test fun sourceReadyOnlyForTheLoadedItem() {
        assertTrue(segmentSourceReady(true, "https://v/2.m3u8", "https://v/2.m3u8"))
        // Episode switched: the player still holds the previous item and its duration.
        assertFalse(segmentSourceReady(true, "https://v/1.m3u8", "https://v/2.m3u8"))
        assertFalse(segmentSourceReady(false, "https://v/2.m3u8", "https://v/2.m3u8"))
        assertFalse(segmentSourceReady(true, null, null))
    }

    private fun tryAt(doneFor: String? = null, inFlight: Boolean = false, lastTry: Long? = null, now: Long = 100_000, current: String = "e1", duration: Long = 1_420_000) =
        shouldTrySegments("e1", current, duration, doneFor, inFlight, lastTry, now)

    @Test fun firstAttemptGoes() = assertTrue(tryAt())
    @Test fun doneEpisodeNeverAgain() = assertFalse(tryAt(doneFor = "e1"))
    @Test fun nextEpisodeTriesAgain() = assertTrue(shouldTrySegments("e2", "e2", 1_420_000, "e1", false, 99_000, 100_000))
    @Test fun oneAttemptAtATime() = assertFalse(tryAt(inFlight = true))
    @Test fun notLinkedYetRetriesEveryFiveSeconds() {
        assertFalse(tryAt(lastTry = 96_000, now = 100_000))
        assertTrue(tryAt(lastTry = 95_000, now = 100_000))
    }
    @Test fun unknownDurationOrOtherEpisodeWaits() {
        assertFalse(tryAt(duration = 0))
        assertFalse(tryAt(current = "e2"))
    }

    private val s2 = SeasonEntry(Anime(id = "s2", title = "S2"), "S2")

    @Test fun nextSeasonOnlyAfterAFinishedSeason() {
        assertEquals(s2, offerNextSeason(Anime(id = "s1", title = "S1", status = "FINISHED"), null, s2))
        assertNull(offerNextSeason(Anime(id = "s1", title = "S1", status = "ONGOING"), null, s2))
        assertNull(offerNextSeason(Anime(id = "s1", title = "S1", status = "FINISHED"), Episode(id = "e", number = "2", title = ""), s2))
        assertNull(offerNextSeason(Anime(id = "s1", title = "S1"), null, null))
    }
}
