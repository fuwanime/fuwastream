package com.fuwanime

import android.app.Application
import com.fuwanime.data.Anime
import com.fuwanime.data.manga.titleKey
import com.fuwanime.util.typeLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en", application = Application::class)
class CatalogLabelsTest {
    @Test fun yearComesFromTheAiredDateWhenKnown() {
        // Xian Ni: the catalog says 2026, the first episode aired 2023-09-25.
        assertEquals("2023", Anime(id = "1", title = "Xian Ni", year = "2026", airedStart = "2023-09-25").displayYear)
        assertEquals("2026", Anime(id = "2", title = "X", year = "2026", airedStart = "").displayYear)
        assertEquals("2026", Anime(id = "3", title = "X", year = "2026", airedStart = "unknown").displayYear)
        assertEquals("", Anime(id = "4", title = "X", year = "UNKNOWN").displayYear)
    }

    @Test fun differentSpellingsOfOneTitleShareAKey() {
        assertEquals(titleKey("Mushen Ji"), titleKey("Mu Shen Ji"))
        assertEquals(titleKey("Naruto: Shippuden"), titleKey("Naruto Shippuden"))
        assertEquals("", titleKey("   "))
    }

    @Test fun apiTypesBecomeLocalisedNames() {
        val res = testResources()
        assertEquals("Series", typeLabel(res, "SERIES"))
        assertEquals("Live Action", typeLabel(res, "LIVE ACTION"))
        assertEquals("Live Action", typeLabel(res, "live-action"))
        assertEquals("ONA", typeLabel(res, "ONA"))
        assertEquals("Tv special", typeLabel(res, "TV SPECIAL"))
    }
}
