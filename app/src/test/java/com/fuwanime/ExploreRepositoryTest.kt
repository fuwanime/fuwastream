package com.fuwanime

import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreRepositoryTest {
    @Test
    fun typeWithoutKeyword_usesServerTypeEndpoint() = runBlocking {
        val api = FakeAnimeApi()
        AnimeRepository(api).explore(2, SortOrder.TITLE, "  ", type = "LIVE ACTION").getOrThrow()
        assertEquals(listOf("exploreByType(type=LIVE ACTION, page=2, sort=alphabet)"), api.calls)
    }

    @Test
    fun typeWithKeyword_usesKeywordEndpoint() = runBlocking {
        val api = FakeAnimeApi()
        AnimeRepository(api).explore(0, SortOrder.POPULAR, "naruto", type = "MOVIE").getOrThrow()
        assertEquals(listOf("explore(page=0, sort=views, keyword=naruto)"), api.calls)
    }

    @Test
    fun noType_usesKeywordEndpoint() = runBlocking {
        val api = FakeAnimeApi()
        AnimeRepository(api).explore(1, SortOrder.POPULAR, "").getOrThrow()
        assertEquals(listOf("explore(page=1, sort=views, keyword=)"), api.calls)
    }
}
