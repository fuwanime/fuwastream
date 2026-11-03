package com.fuwanime

import com.fuwanime.data.AnimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheOnlyRepositoryTest {
    @Test fun cacheOnlyCallsGoToCachedApi() = runBlocking {
        val net = FakeAnimeApi()
        val cache = FakeAnimeApi()
        val repo = AnimeRepository(net, cache)
        repo.explore(0, cacheOnly = true)
        repo.schedule("Senin", cacheOnly = true)
        repo.latestReleases(20, cacheOnly = true)
        assertEquals(3, cache.calls.size)
        assertTrue(net.calls.isEmpty())
    }

    @Test fun defaultCallsGoToNetworkApi() = runBlocking {
        val net = FakeAnimeApi()
        val cache = FakeAnimeApi()
        val repo = AnimeRepository(net, cache)
        repo.explore(0)
        repo.schedule("Senin")
        repo.latestReleases(20)
        assertEquals(3, net.calls.size)
        assertTrue(cache.calls.isEmpty())
    }
}
