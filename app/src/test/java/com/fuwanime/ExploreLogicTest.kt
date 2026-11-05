package com.fuwanime

import com.fuwanime.data.Anime
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
}
