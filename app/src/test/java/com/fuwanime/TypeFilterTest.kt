package com.fuwanime

import com.fuwanime.ui.explore.ExploreFilters
import com.fuwanime.ui.explore.TypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeFilterTest {
    @Test
    fun all_matchesEveryType() {
        listOf("SERIES", "MOVIE", "LIVE ACTION", "", "UNKNOWN").forEach { assertTrue(TypeFilter.ALL.matches(it)) }
    }

    @Test
    fun matches_apiTypeIgnoringCaseSpacingAndHyphens() {
        assertTrue(TypeFilter.MOVIE.matches("MOVIE"))
        assertTrue(TypeFilter.MOVIE.matches("movie"))
        assertTrue(TypeFilter.LIVE_ACTION.matches("LIVE ACTION"))
        assertTrue(TypeFilter.LIVE_ACTION.matches(" Live-Action "))
    }

    @Test
    fun matches_rejectsOtherTypes() {
        assertFalse(TypeFilter.MOVIE.matches("SERIES"))
        assertFalse(TypeFilter.MOVIE.matches(""))
        assertFalse(TypeFilter.SERIES.matches("LIVE ACTION"))
        assertFalse(TypeFilter.LIVE_ACTION.matches("MOVIE"))
    }

    @Test
    fun activeCount_includesType() {
        assertEquals(0, ExploreFilters().activeCount)
        assertEquals(1, ExploreFilters(type = TypeFilter.MOVIE).activeCount)
        assertEquals(2, ExploreFilters(genre = "Action", type = TypeFilter.LIVE_ACTION).activeCount)
    }
}
