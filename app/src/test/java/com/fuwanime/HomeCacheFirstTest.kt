package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.ui.home.FeedResult
import com.fuwanime.ui.home.HomeFeed
import com.fuwanime.ui.home.HomeFeedLoader
import com.fuwanime.ui.home.OFFLINE_EMPTY
import com.fuwanime.ui.home.reduce
import com.fuwanime.ui.home.shouldAutoRefresh
import com.fuwanime.util.Day
import com.fuwanime.util.friendlyText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HomeCacheFirstTest {
    private val a1 = Anime("1", "Satu")
    private val a2 = Anime("2", "Dua")

    private fun result(popular: List<Anime> = emptyList()) =
        FeedResult(popular = popular, today = emptyList(), season = emptyList(), latest = emptyList(), error = null)

    private fun failure(e: Throwable) =
        FeedResult(popular = null, today = emptyList(), season = emptyList(), latest = null, error = e)

    @Test fun reduce_cacheShownWhileSyncing() {
        val f = HomeFeed().reduce(result(listOf(a1)), fromCache = true, online = true)!!
        assertFalse(f.loading)
        assertTrue(f.syncing)
        assertTrue(f.stale)
        assertEquals(listOf(a1), f.popular)
    }

    @Test fun reduce_networkFailureKeepsCachedData() {
        val cached = HomeFeed().reduce(result(listOf(a1)), fromCache = true, online = true)!!
        val f = cached.reduce(failure(IOException()), fromCache = false, online = false)!!
        assertNull(f.error)
        assertEquals(listOf(a1), f.popular)
        assertFalse(f.syncing)
        assertTrue(f.stale)
    }

    @Test fun reduce_networkSuccessReplacesCache() {
        val cached = HomeFeed().reduce(result(listOf(a1)), fromCache = true, online = true)!!
        val f = cached.reduce(result(listOf(a2)), fromCache = false, online = true)!!
        assertEquals(listOf(a2), f.popular)
        assertFalse(f.stale)
        assertFalse(f.syncing)
    }

    @Test fun reduce_noCacheOfflineShowsOfflineCopy() {
        val f = HomeFeed().reduce(failure(UnknownHostException()), fromCache = false, online = false)!!
        assertEquals(OFFLINE_EMPTY, f.error)
    }

    @Test fun reduce_noCacheOnlineShowsFriendlyMessage() {
        val e = SocketTimeoutException()
        val f = HomeFeed().reduce(failure(e), fromCache = false, online = true)!!
        assertEquals(e.friendlyText(), f.error)
    }

    @Test fun reduce_lateCacheIgnoredAfterNetwork() {
        val net = HomeFeed().reduce(result(listOf(a2)), fromCache = false, online = true)!!
        assertNull(net.reduce(result(listOf(a1)), fromCache = true, online = true))
    }

    @Test fun reduce_emptyCacheIgnored() {
        assertNull(HomeFeed().reduce(result(), fromCache = true, online = true))
    }

    @Test fun shouldAutoRefresh_onlyWhenStale() {
        val cached = HomeFeed().reduce(result(listOf(a1)), fromCache = true, online = true)!!
        val net = HomeFeed().reduce(result(listOf(a2)), fromCache = false, online = true)!!
        assertTrue(shouldAutoRefresh(cached))
        assertFalse(shouldAutoRefresh(net))
    }

    @Test fun loader_cacheOnlyUsesCachedApi() = runBlocking {
        val net = FakeAnimeApi()
        val cache = FakeAnimeApi()
        HomeFeedLoader(AnimeRepository(net, cache), Day.MONDAY).fetch(cacheOnly = true)
        assertTrue(net.calls.isEmpty())
        assertEquals(1 + Day.entries.size + 1, cache.calls.size)
    }

    @Test fun reduce_networkLoadServedFromCacheStaysStale() {
        val f = HomeFeed().reduce(result(listOf(a1)), fromCache = false, online = true, servedFromCache = true)!!
        assertTrue(f.stale)
        assertTrue(shouldAutoRefresh(f))
    }

    @Test fun networkStatus_countsCacheHits() {
        val status = com.fuwanime.data.remote.NetworkStatus()
        val before = status.cacheHits
        status.markCacheServed(1L)
        status.markCacheServed(2L)
        assertEquals(before + 2, status.cacheHits)
    }
}
