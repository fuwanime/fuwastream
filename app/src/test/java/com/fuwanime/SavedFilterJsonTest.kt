package com.fuwanime

import android.app.Application
import com.fuwanime.data.SortOrder
import com.fuwanime.ui.explore.ExploreFilters
import com.fuwanime.ui.explore.StatusFilter
import com.fuwanime.ui.explore.TypeFilter
import com.fuwanime.ui.explore.exploreFiltersFromJson
import com.fuwanime.ui.explore.toJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SavedFilterJsonTest {
    @Test fun roundTripRestoresEveryFilter() {
        val filters = ExploreFilters(
            query = "frieren",
            genres = setOf("Fantasy", "Slice of Life"),
            status = StatusFilter.ONGOING,
            type = TypeFilter.MOVIE,
            sort = SortOrder.TITLE,
        )
        assertEquals(filters, exploreFiltersFromJson(filters.toJson(), "frieren"))
    }

    @Test fun emptyFiltersRoundTrip() {
        val filters = ExploreFilters()
        assertEquals(filters, exploreFiltersFromJson(filters.toJson(), ""))
    }

    @Test fun corruptOrPartialJsonFallsBackToDefaults() {
        assertEquals(ExploreFilters(query = "q"), exploreFiltersFromJson("not json", "q"))
        assertEquals(ExploreFilters(query = "q"), exploreFiltersFromJson("{}", "q"))
        // Unknown enum names (e.g. from a newer app version) degrade instead of crashing.
        val restored = exploreFiltersFromJson("""{"status":"NOPE","type":"GONE","sort":"???","genres":["Action"]}""", "q")
        assertEquals(ExploreFilters(query = "q", genres = setOf("Action")), restored)
    }
}
