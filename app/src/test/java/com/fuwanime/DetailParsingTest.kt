package com.fuwanime

import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.remote.AnimeDto
import com.fuwanime.data.remote.DetailData
import com.fuwanime.data.toAnime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailParsingTest {
    @Test
    fun detailResponse_parsesSingleEpisodeObjectAndSeasons() {
        val data = checkNotNull(Fixtures.response<DetailData>("detail.json").data)
        assertEquals("4832", data.movie?.id)
        assertEquals(2, data.season?.size)
        assertEquals("S1", data.season?.first()?.season)
    }

    @Test
    fun detailWithSeasons_mapsAnimeAndSeasonLabels() = runBlocking {
        val repo = AnimeRepository(FakeAnimeApi())
        val detail = repo.detailWithSeasons("4832").getOrThrow()
        assertEquals("One Piece: Live Action", detail.anime.title)
        assertEquals(listOf("S1", "S2"), detail.seasons.map { it.label })
        assertEquals(listOf("4832", "6335"), detail.seasons.map { it.anime.id })
        assertEquals("One Piece: Live Action", repo.detail("4832").getOrThrow().title)
    }

    @Test
    fun toAnime_dashGenreMeansNone() {
        assertTrue(AnimeDto(id = "1", genre = "-").toAnime().genres.isEmpty())
    }
}
