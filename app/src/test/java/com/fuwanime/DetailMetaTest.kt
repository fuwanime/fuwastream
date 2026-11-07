package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.SeasonEntry
import com.fuwanime.ui.detail.airingLabel
import com.fuwanime.ui.detail.nextInFranchise
import com.fuwanime.ui.detail.scoreLabel
import com.fuwanime.ui.detail.watchOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "id")
class DetailMetaTest {
    private val res get() = testResources()

    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
    private val min = 60_000L

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(jakarta).apply {
        clear()
        set(year, month - 1, day, hour, minute)
    }.timeInMillis

    // Wednesday 4 Nov 2026, 10:00 WIB.
    private val now = at(2026, 11, 4, 10, 0)

    @Test fun airingUnderAnHour() = assertEquals("Ep 6 · 45m", airingLabel(res, 6, now + 45 * min, now, jakarta))
    @Test fun airingUnderADay() = assertEquals("Ep 6 · 2j 14m", airingLabel(res, 6, now + 134 * min, now, jakarta))
    @Config(sdk = [35], application = android.app.Application::class, qualifiers = "en")
    @Test fun airingEnglish() {
        assertEquals("Ep 6 · 2h 14m", airingLabel(res, 6, now + 134 * min, now, jakarta))
        assertEquals("Ep 6 · Sat 23.00", airingLabel(res, 6, at(2026, 11, 7, 23, 0), now, jakarta))
    }

    @Test fun airingLater() = assertEquals("Ep 6 · Sab 23.00", airingLabel(res, 6, at(2026, 11, 7, 23, 0), now, jakarta))
    @Test fun airingLaterPadsMinutes() = assertEquals("Ep 12 · Jum 08.05", airingLabel(res, 12, at(2026, 11, 6, 8, 5), now, jakarta))

    @Test fun scoreFormatsOneDecimalWithComma() {
        assertEquals("★ 8,4", scoreLabel(84))
        assertEquals("★ 7,0", scoreLabel(70))
        assertNull(scoreLabel(0))
        assertNull(scoreLabel(null))
    }

    private fun entry(id: String, label: String, aired: String) = SeasonEntry(Anime(id = id, title = "T$id", airedStart = aired), label)

    @Test fun watchOrderSortsByAiredStartIncludingCurrent() {
        val s1 = entry("1", "S1", "2019-04-06")
        val order = watchOrder(s1.anime, listOf(entry("3", "S2", "2021-12-05"), entry("2", "Movie", "2020-10-16"), s1))
        assertEquals(listOf("1", "2", "3"), order.map { it.anime.id })
    }

    @Test fun watchOrderPutsUndatedLastInSourceOrder() {
        val s1 = entry("1", "S1", "2019-04-06")
        val order = watchOrder(s1.anime, listOf(entry("x", "OVA", ""), s1, entry("y", "Spesial", ""), entry("2", "S2", "2020-01-01")))
        assertEquals(listOf("1", "2", "x", "y"), order.map { it.anime.id })
    }

    @Test fun watchOrderAddsCurrentWhenSourceOmitsIt() {
        val current = Anime(id = "2", title = "T2", airedStart = "2020-10-16")
        val order = watchOrder(current, listOf(entry("3", "S2", "2021-12-05"), entry("1", "S1", "2019-04-06")))
        assertEquals(listOf("1", "2", "3"), order.map { it.anime.id })
    }

    @Test fun watchOrderEmptyWhenNoOtherEntry() {
        val s1 = entry("1", "S1", "2019-04-06")
        assertTrue(watchOrder(s1.anime, listOf(s1)).isEmpty())
        assertTrue(watchOrder(s1.anime, emptyList()).isEmpty())
    }

    @Test fun nextInFranchiseIsFollowingEntry() {
        val ordered = listOf(entry("1", "S1", "2019"), entry("2", "Movie", "2020"), entry("3", "S2", "2021"))
        assertEquals("3", nextInFranchise("2", ordered)?.anime?.id)
        assertNull(nextInFranchise("3", ordered))
        assertNull(nextInFranchise("zzz", ordered))
    }
}
