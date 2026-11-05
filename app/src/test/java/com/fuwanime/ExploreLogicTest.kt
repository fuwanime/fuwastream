package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.ui.explore.headerOffsetAfterScroll
import com.fuwanime.ui.explore.matchesGenres
import com.fuwanime.ui.explore.pickRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExploreLogicTest {
    private fun anime(id: String = "a", genres: List<String> = emptyList(), synopsis: String = "") =
        Anime(id = id, title = id, genres = genres, synopsis = synopsis)

    @Test fun emptySetMatchesAll() = assertTrue(matchesGenres(anime(genres = listOf("Drama")), emptySet()))

    @Test fun allGenresRequired() {
        assertTrue(matchesGenres(anime(genres = listOf("Action", "Fantasy", "Comedy")), setOf("action", "Fantasy")))
        assertFalse(matchesGenres(anime(genres = listOf("Action")), setOf("Action", "Fantasy")))
    }

    @Test fun isekaiFromSynopsis() =
        assertTrue(matchesGenres(anime(genres = listOf("Action"), synopsis = "Dia terlempar ke dunia isekai"), setOf("Action", "Isekai")))

    @Test fun randomFromResults() {
        assertNull(pickRandom(emptyList()))
        val list = listOf(anime("a"), anime("b"), anime("c"))
        val seed = (0..100).first { Random(it).nextInt(list.size) == 1 }
        assertEquals("b", pickRandom(list, Random(seed))?.id)
    }

    // Header offset in px: 0 = fully shown, -height = fully hidden. Follows what the grid actually scrolled.
    @Test fun headerHidesAsGridScrollsDown() = assertEquals(-50f, headerOffsetAfterScroll(0f, -50f, 300f), 0f)

    @Test fun headerStopsWhenFullyHidden() = assertEquals(-300f, headerOffsetAfterScroll(-280f, -100f, 300f), 0f)

    @Test fun headerReturnsOnScrollUp() {
        assertEquals(-260f, headerOffsetAfterScroll(-300f, 40f, 300f), 0f)
        assertEquals(0f, headerOffsetAfterScroll(-20f, 100f, 300f), 0f)
    }
}
