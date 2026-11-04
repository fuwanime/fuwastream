package com.fuwanime

import com.fuwanime.util.epochDay
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class EpochDayTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")

    private fun at(tz: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int, s: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min, s) }.timeInMillis

    @Test fun utcEpochZero() = assertEquals(0, epochDay(0, utc))

    @Test fun lastSecondVsMidnight() {
        val before = epochDay(at(jakarta, 2026, 11, 4, 23, 59, 59), jakarta)
        val after = epochDay(at(jakarta, 2026, 11, 5, 0, 0, 0), jakarta)
        assertEquals(1, after - before)
    }

    @Test fun jakartaShiftsUtc() {
        val t = at(utc, 2026, 11, 4, 20, 0, 0)
        assertEquals(epochDay(t, utc) + 1, epochDay(t, jakarta))
    }

    @Test fun negativeFloors() = assertEquals(-1, epochDay(-1, utc))
}
