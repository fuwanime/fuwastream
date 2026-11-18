package com.fuwanime

import com.fuwanime.data.Episode
import com.fuwanime.ui.detail.episodesInRange
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkDownloadRangeTest {
    private fun ep(n: String) = Episode(id = "e$n", number = n, title = "")
    private val all = listOf("1180", "12.5", "120", "90", "100", "91", "special", "89", "121").map(::ep)

    @Test fun rangeIsInclusiveAndOldestFirst() =
        assertEquals(listOf("90", "91", "100", "120"), episodesInRange(all, 90.0, 120.0).map { it.number })

    @Test fun openEndedBoundsTakeEverythingNumbered() =
        assertEquals(listOf("12.5", "89", "90", "91", "100", "120", "121", "1180"), episodesInRange(all, null, null).map { it.number })

    @Test fun fromOnlyAndToOnly() {
        assertEquals(listOf("121", "1180"), episodesInRange(all, 121.0, null).map { it.number })
        assertEquals(listOf("12.5", "89"), episodesInRange(all, null, 89.0).map { it.number })
    }

    @Test fun emptyWhenNothingMatches() = assertEquals(emptyList<Episode>(), episodesInRange(all, 500.0, 600.0))
}
