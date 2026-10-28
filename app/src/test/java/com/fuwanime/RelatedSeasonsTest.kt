package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.SeasonEntry
import com.fuwanime.ui.detail.relatedSeasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedSeasonsTest {
    private fun entry(id: String, label: String) = SeasonEntry(Anime(id = id, title = "Title $id"), label)

    @Test
    fun relatedSeasons_dropsCurrentAndKeepsOrder() {
        val seasons = listOf(entry("1", "S1"), entry("2", "Movie"), entry("3", "S2"))
        assertEquals(listOf("2", "3"), relatedSeasons("1", seasons).map { it.anime.id })
    }

    @Test
    fun relatedSeasons_dedupesByIdAndDropsBlankIds() {
        val seasons = listOf(entry("2", "S2"), entry("", "?"), entry("2", "S2"), entry("3", ""))
        assertEquals(listOf("2", "3"), relatedSeasons("1", seasons).map { it.anime.id })
    }

    @Test
    fun relatedSeasons_emptyWhenOnlyCurrent() {
        assertTrue(relatedSeasons("1", listOf(entry("1", "S1"))).isEmpty())
    }
}
