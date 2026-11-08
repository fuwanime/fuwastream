package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.local.AnimeLinkDao
import com.fuwanime.data.local.AnimeLinkEntity
import com.fuwanime.data.meta.AniListApi
import com.fuwanime.data.meta.AnimeLinkRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnimeLinkRepositoryTest {
    private class FakeDao : AnimeLinkDao {
        val rows = mutableMapOf<String, AnimeLinkEntity>()
        override suspend fun get(id: String) = rows[id]
        override suspend fun put(e: AnimeLinkEntity) { rows[e.animeId] = e }
        override suspend fun clear() = rows.clear()
    }

    private val server = MockWebServer()
    private val dao = FakeDao()
    private val h = 3_600_000L
    private val now = 1_800_000_000_000L
    private lateinit var repo: AnimeLinkRepository

    @Before fun setUp() {
        server.start()
        repo = AnimeLinkRepository(dao, AniListApi.create(OkHttpClient(), server.url("/").toString())) { now }
    }

    @After fun tearDown() = server.shutdown()

    private val bnha2 = Anime(id = "a1", title = "Boku no Hero Academia S2", alternativeTitles = "My Hero Academia Season 2, BNHA 2, MHA S2, Hero Aca 2", airedStart = "2017-04-01")

    private fun media(id: Int, romaji: String, english: String?, year: Int) =
        """{"id":$id,"idMal":${id + 1000},"seasonYear":$year,"title":{"romaji":"$romaji","english":${english?.let { "\"$it\"" } ?: "null"}},"synonyms":[],"averageScore":80,"tags":[{"name":"Action","rank":90,"isMediaSpoiler":false}],"nextAiringEpisode":null}"""

    private fun page(vararg media: String) = MockResponse().setBody("""{"data":{"Page":{"media":[${media.joinToString(",")}]}}}""")
    private val empty get() = page()

    private fun row(state: String, fetchedAt: Long, anilist: Int? = 7, mal: Int? = 1007) =
        AnimeLinkEntity("a1", state, anilist, mal, 70, "Drama", null, null, fetchedAt)

    @Test fun linksOnTitleSearchAndStores() = runBlocking {
        server.enqueue(page(media(7, "Boku no Hero Academia S2", null, 2017)))
        val meta = repo.link(bnha2)
        assertEquals(80, meta?.score)
        assertEquals("LINKED", dao.rows["a1"]?.state)
        assertEquals(1007, dao.rows["a1"]?.malId)
        assertEquals(1, server.requestCount)
    }

    @Test fun fallsBackToSynonymsWithTolerance() = runBlocking {
        server.enqueue(page(media(8, "Boku no Hero Academia 2", "My Hero Academia Season 2", 2018)))
        server.enqueue(page(media(8, "Boku no Hero Academia 2", "My Hero Academia Season 2", 2018)))
        assertNotNull(repo.link(bnha2))
        assertEquals(8, dao.rows["a1"]?.anilistId)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("My Hero Academia Season 2"))
    }

    @Test fun storesNoneAfterAllSearchesMiss() = runBlocking {
        repeat(5) { server.enqueue(empty) }
        assertNull(repo.link(bnha2))
        assertEquals("NONE", dao.rows["a1"]?.state)
        assertEquals(now, dao.rows["a1"]?.fetchedAt)
        assertEquals(4, server.requestCount)
    }

    @Test fun networkErrorStoresNothingAndReturnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(repo.link(bnha2))
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun rateLimitStoresNothing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        assertNull(repo.link(bnha2))
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun schemaErrorStoresNothing() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"errors":[{"message":"x"}],"data":null}"""))
        assertNull(repo.link(bnha2))
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun unlinkedNeverSearches() = runBlocking {
        dao.rows["a1"] = row("UNLINKED", 0, null, null)
        assertNull(repo.link(bnha2))
        assertNull(repo.malIdFor("a1"))
        assertEquals(0, server.requestCount)
    }

    @Test fun unlinkStoresUnlinked() = runBlocking {
        dao.rows["a1"] = row("LINKED", now)
        repo.unlink("a1")
        assertEquals("UNLINKED", dao.rows["a1"]?.state)
        assertNull(dao.rows["a1"]?.malId)
        assertNull(repo.malIdFor("a1"))
    }

    @Test fun freshNoneSkipsSearch() = runBlocking {
        dao.rows["a1"] = row("NONE", now - 24 * h, null, null)
        assertNull(repo.link(bnha2))
        assertEquals(0, server.requestCount)
    }

    @Test fun staleLinkedRefreshesById() = runBlocking {
        dao.rows["a1"] = row("LINKED", now - 13 * h)
        server.enqueue(MockResponse().setBody("""{"data":{"Media":${media(7, "Boku no Hero Academia S2", null, 2017)}}}"""))
        assertEquals(80, repo.link(bnha2)?.score)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"id\":7"))
        assertEquals(now, dao.rows["a1"]?.fetchedAt)
    }

    @Test fun freshLinkedUsesCacheWithoutRequest() = runBlocking {
        dao.rows["a1"] = row("LINKED", now - h)
        val meta = repo.link(bnha2)
        assertEquals(70, meta?.score)
        assertEquals(listOf("Drama"), meta?.tags)
        assertEquals(1007, repo.malIdFor("a1"))
        assertEquals(0, server.requestCount)
    }

    @Test fun refreshFailureStillReturnsCachedMeta() = runBlocking {
        dao.rows["a1"] = row("LINKED", now - 13 * h)
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(70, repo.link(bnha2)?.score)
        assertEquals(now - 13 * h, dao.rows["a1"]?.fetchedAt)
    }
}
