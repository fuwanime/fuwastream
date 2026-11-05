package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.ui.schedule.AnimeSeason
import com.fuwanime.ui.schedule.DaySchedule
import com.fuwanime.ui.schedule.SeasonTab
import com.fuwanime.ui.schedule.seasonOf
import com.fuwanime.ui.schedule.seasonTabState
import com.fuwanime.ui.schedule.splitBySeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonLogicTest {
    @Test fun seasons() {
        assertEquals(AnimeSeason("Musim Dingin", 2026, 1), seasonOf(2026, 3))
        assertEquals(AnimeSeason("Musim Semi", 2026, 4), seasonOf(2026, 4))
        assertEquals(AnimeSeason("Musim Panas", 2026, 7), seasonOf(2026, 9))
        assertEquals(AnimeSeason("Musim Gugur", 2026, 10), seasonOf(2026, 12))
    }

    private fun a(id: String, title: String, aired: String) = Anime(id = id, title = title, airedStart = aired)

    @Test fun splitByAiredStart() {
        val autumn = AnimeSeason("Musim Gugur", 2026, 10)
        val (fresh, continuing) = splitBySeason(
            listOf(
                a("1", "Zeta", "2026-10-04"), a("2", "Alpha", "2026-12-31"), a("3", "Mid", "2026-09-30"),
                a("4", "Beta", "2025-10-05"), a("5", "Gamma", ""), a("1", "Zeta", "2026-10-04"),
            ),
            autumn,
        )
        assertEquals(listOf("Alpha", "Zeta"), fresh.map { it.title })
        assertEquals(listOf("Beta", "Gamma", "Mid"), continuing.map { it.title })
    }

    private val ok = DaySchedule(items = listOf(a("1", "A", "")), loaded = true)
    private val ok2 = DaySchedule(items = listOf(a("1", "A", ""), a("2", "B", "")), loaded = true)
    private val failed = DaySchedule(error = "x")

    @Test fun tabReadyWithPartialFailure() {
        val s = seasonTabState(listOf(ok, ok2) + List(5) { failed })
        assertTrue(s is SeasonTab.Ready)
        assertEquals(listOf("1", "2"), (s as SeasonTab.Ready).all.map { it.id })
    }

    @Test fun tabErrorOnlyWhenAllFail() = assertTrue(seasonTabState(List(7) { failed }) is SeasonTab.Error)

    @Test fun tabLoadingUntilFirstReady() =
        assertEquals(SeasonTab.Loading, seasonTabState(listOf(DaySchedule(loading = true)) + List(6) { DaySchedule() }))
}
