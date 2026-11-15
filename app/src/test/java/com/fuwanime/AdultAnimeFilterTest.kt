package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.hasMatureGenre
import com.fuwanime.data.remote.AnimeDto
import com.fuwanime.data.remote.ApiResponse
import com.fuwanime.data.remote.MovieListData
import com.fuwanime.ui.explore.availableAnimeGenres
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultAnimeFilterTest {

    @Test fun onlyExplicitMatureGenresAreClassified() {
        assertTrue(Anime("1", "Ecchi", genres = listOf("Romance", "Ecchi")).hasMatureGenre())
        assertTrue(Anime("2", "Hentai", genres = listOf("Hentai")).hasMatureGenre())
        assertTrue(Anime("3", "R18", genres = listOf("R-18")).hasMatureGenre())
        assertFalse(Anime("4", "Harem", genres = listOf("Harem", "Seinen")).hasMatureGenre())
        assertFalse(Anime("5", "Yuri", genres = listOf("Yuri", "Girls Love")).hasMatureGenre())
    }

    @Test fun repositoryHidesMatureAnimeByDefault() = runBlocking {
        val api = catalogApi()
        val visible = AnimeRepository(api).explore(0).getOrThrow()

        assertEquals(listOf("safe", "harem"), visible.map { it.id })
    }

    @Test fun repositoryKeepsMatureAnimeWhenEnabled() = runBlocking {
        val visible = AnimeRepository(catalogApi(), mature = { true }).explore(0).getOrThrow()

        assertEquals(listOf("safe", "ecchi", "hentai", "harem"), visible.map { it.id })
    }

    @Test fun matureGenresOnlyAppearInPickerWhenEnabled() {
        assertFalse("Ecchi" in availableAnimeGenres(false))
        assertFalse("Hentai" in availableAnimeGenres(false))
        assertTrue("Ecchi" in availableAnimeGenres(true))
        assertTrue("Erotica" in availableAnimeGenres(true))
        assertTrue("Hentai" in availableAnimeGenres(true))
    }

    private fun catalogApi() = object : FakeAnimeApi() {
        override suspend fun explore(page: Int, sort: String, keyword: String) = ApiResponse(
            data = MovieListData(
                listOf(
                    AnimeDto(id = "safe", title = "Safe", genre = "Action,Fantasy"),
                    AnimeDto(id = "ecchi", title = "Ecchi", genre = "Comedy,Ecchi"),
                    AnimeDto(id = "hentai", title = "Hentai", genre = "Hentai"),
                    AnimeDto(id = "harem", title = "Harem", genre = "Harem,Seinen"),
                ),
            ),
        )
    }
}
