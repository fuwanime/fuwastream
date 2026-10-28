package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.data.rankSimilar
import com.fuwanime.data.remote.ApiResponse
import com.fuwanime.data.remote.GenreListData
import com.fuwanime.data.remote.MovieListData
import com.fuwanime.data.seedGenres
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarAnimeTest {
    private fun anime(id: String, genres: String, type: String = "SERIES", views: Long = 0) =
        Anime(id = id, title = "Title $id", type = type, views = views, genres = genres.split(",").map { it.trim() })

    private val onePiece = anime("op", "Action,Adventure,Comedy,Drama,Fantasy,Shounen,Super Power", views = 15_000_000)

    @Test
    fun broadPopularTitleDoesNotMatchOnASingleSharedGenre() {
        val romcom = anime("t", "Comedy,Romance,School")
        val schoolRomance = anime("a", "Romance,School,Drama", views = 10)
        assertEquals(listOf("a"), rankSimilar(romcom, listOf(onePiece, schoolRomance), emptySet()).map { it.id })
    }

    @Test
    fun closerGenreOverlapBeatsPopularity() {
        val target = anime("t", "Action,Mystery,Psychological,Thriller")
        val close = anime("close", "Mystery,Psychological,Thriller", views = 100)
        val loose = anime("loose", "Action,Mystery,Comedy,Adventure,Fantasy", views = 9_000_000)
        assertEquals(listOf("close", "loose"), rankSimilar(target, listOf(loose, close), emptySet()).map { it.id })
    }

    @Test
    fun sameTypeBreaksTies() {
        val target = anime("t", "Drama,Romance", type = "MOVIE")
        val series = anime("s", "Drama,Romance", type = "SERIES", views = 1_000)
        val movie = anime("m", "Drama,Romance", type = "MOVIE", views = 10)
        assertEquals(listOf("m", "s"), rankSimilar(target, listOf(series, movie), emptySet()).map { it.id })
    }

    @Test
    fun excludesItselfFranchiseAndDuplicates() {
        val target = anime("t", "Drama,Romance")
        val pool = listOf(anime("t", "Drama,Romance"), anime("s2", "Drama,Romance"), anime("x", "Drama,Romance"), anime("x", "Drama,Romance"))
        assertEquals(listOf("x"), rankSimilar(target, pool, exclude = setOf("s2")).map { it.id })
    }

    @Test
    fun singleGenreTargetNeedsOneSharedGenre() {
        val target = anime("t", "Music")
        assertEquals(listOf("m"), rankSimilar(target, listOf(anime("m", "Music,Drama"), onePiece), emptySet()).map { it.id })
    }

    @Test
    fun noGenresMeansNoSuggestions() {
        assertTrue(rankSimilar(Anime(id = "t", title = "T"), listOf(onePiece), emptySet()).isEmpty())
    }

    @Test
    fun seedGenres_preferSpecificOverBroadGenres() {
        assertEquals(listOf("School", "Romance"), seedGenres(listOf("Action", "Comedy", "Romance", "School")))
        assertEquals(listOf("Psychological", "Thriller"), seedGenres(listOf("Action", "Psychological", "Thriller")))
        assertEquals(listOf("Comedy"), seedGenres(listOf("Comedy")))
    }

    @Test
    fun similarCandidates_queryTheServerBySeedGenreIds() = runBlocking {
        val api = object : FakeAnimeApi() {
            override suspend fun genres() = Fixtures.response<GenreListData>("genres.json")
            override suspend fun exploreByGenre(genreId: String, page: Int, sort: String): ApiResponse<MovieListData> {
                calls += "exploreByGenre(id=$genreId)"
                return ApiResponse(data = MovieListData(emptyList()))
            }
        }
        AnimeRepository(api).similarCandidates(anime("t", "Action,Mystery,Psychological")).getOrThrow()
        // Mystery = 21, Psychological = 23 in the real genre list.
        assertEquals(setOf("exploreByGenre(id=21)", "exploreByGenre(id=23)"), api.calls.toSet())
    }
}
