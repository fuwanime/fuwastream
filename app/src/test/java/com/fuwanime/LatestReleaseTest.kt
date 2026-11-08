package com.fuwanime

import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.SortOrder
import com.fuwanime.data.remote.ApiResponse
import com.fuwanime.data.remote.AnimeDto
import com.fuwanime.data.remote.EpisodeDto
import com.fuwanime.data.remote.EpisodeListData
import com.fuwanime.data.remote.MovieListData
import com.fuwanime.data.toEpisode
import com.fuwanime.util.episodeShortLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LatestReleaseTest {
    @Test
    fun toEpisode_readsIsNewFlag() {
        assertTrue(EpisodeDto(id = "1", isNew = "1").toEpisode().isNew)
        assertTrue(EpisodeDto(id = "1", isNew = 1.0).toEpisode().isNew)
        assertFalse(EpisodeDto(id = "1", isNew = "0").toEpisode().isNew)
        assertFalse(EpisodeDto(id = "1").toEpisode().isNew)
    }

    @Test
    fun episodeList_parsesIsNewFromApi() {
        val episodes = checkNotNull(Fixtures.response<EpisodeListData>("episodes.json").data?.episode).map { it.toEpisode() }
        assertEquals(listOf(true, false), episodes.map { it.isNew })
    }

    @Test
    fun latestReleases_mapsEpisodeLabelAndDropsHijackedGenre() = runBlocking {
        val api = object : FakeAnimeApi() {
            override suspend fun latestEpisodes(limit: Int) = Fixtures.response<MovieListData>("latest.json")
        }
        val releases = AnimeRepository(api).latestReleases(20).getOrThrow()
        assertEquals(listOf("Episode 5", "Episode 4", "Episode 164"), releases.map { it.episodeLabel })
        assertEquals("2026-10-26 20:10:10", releases.first().updatedAt)
        assertTrue(releases.all { it.anime.genres.isEmpty() })
    }

    @Test
    fun latestReleases_keepGenresCachedFromOtherLists() = runBlocking {
        val api = object : FakeAnimeApi() {
            override suspend fun explore(page: Int, sort: String, keyword: String) =
                ApiResponse(data = MovieListData(listOf(AnimeDto(id = "6622", title = "Goblin", genre = "Fantasy,Isekai"))))

            override suspend fun latestEpisodes(limit: Int) = Fixtures.response<MovieListData>("latest.json")
        }
        val repo = AnimeRepository(api)
        repo.explore(0, SortOrder.POPULAR).getOrThrow()
        repo.latestReleases(20).getOrThrow()
        assertEquals(listOf("Fantasy", "Isekai"), repo.cachedAnime("6622")?.genres)
    }

    @Test
    fun episodeShortLabel_shortensEpisodeWord() {
        assertEquals("Ep 5", episodeShortLabel("Episode 5"))
        assertEquals("Ep 12.5", episodeShortLabel("episode 12.5"))
        assertEquals("", episodeShortLabel(""))
    }
}
