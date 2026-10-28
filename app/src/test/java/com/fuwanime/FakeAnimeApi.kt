package com.fuwanime

import com.fuwanime.data.remote.AnimeApiService
import com.fuwanime.data.remote.ApiResponse
import com.fuwanime.data.remote.DetailData
import com.fuwanime.data.remote.EpisodeListData
import com.fuwanime.data.remote.GenreListData
import com.fuwanime.data.remote.MovieListData
import com.fuwanime.data.remote.StreamData

/** Serves fixtures and records which endpoints the repository called. */
open class FakeAnimeApi : AnimeApiService {
    val calls = mutableListOf<String>()

    override suspend fun explore(page: Int, sort: String, keyword: String): ApiResponse<MovieListData> {
        calls += "explore(page=$page, sort=$sort, keyword=$keyword)"
        return ApiResponse(data = MovieListData(emptyList()))
    }

    override suspend fun exploreByType(type: String, page: Int, sort: String): ApiResponse<MovieListData> {
        calls += "exploreByType(type=$type, page=$page, sort=$sort)"
        return ApiResponse(data = MovieListData(emptyList()))
    }

    override suspend fun genres(): ApiResponse<GenreListData> = ApiResponse(data = GenreListData(emptyList()))

    override suspend fun exploreByGenre(genreId: String, page: Int, sort: String): ApiResponse<MovieListData> =
        ApiResponse(data = MovieListData(emptyList()))

    override suspend fun latestEpisodes(limit: Int): ApiResponse<MovieListData> = ApiResponse(data = MovieListData(emptyList()))

    override suspend fun schedule(day: String): ApiResponse<MovieListData> = ApiResponse(data = MovieListData(emptyList()))

    override suspend fun detail(animeId: String, idUser: String, keyClient: String): ApiResponse<DetailData> {
        calls += "detail($animeId)"
        return Fixtures.response("detail.json")
    }

    override suspend fun episodes(
        animeId: String,
        page: Int,
        search: String,
        idUser: String,
        keyClient: String,
    ): ApiResponse<EpisodeListData> = ApiResponse(data = EpisodeListData(emptyList()))

    override suspend fun stream(episodeId: String): ApiResponse<StreamData> = ApiResponse(data = null)
}
