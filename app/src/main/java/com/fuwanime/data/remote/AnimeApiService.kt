package com.fuwanime.data.remote

import com.fuwanime.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface AnimeApiService {

    @GET("3/2/explore/movie")
    suspend fun explore(
        @Query("page") page: Int = 0,
        @Query("sort") sort: String = "views",
        @Query("keyword") keyword: String = "",
    ): ApiResponse<MovieListData>

    /** Server-side type filter (SERIES, MOVIE, LIVE ACTION, …). Ignores any keyword. */
    @GET("3/2/explore/movie_type")
    suspend fun exploreByType(
        @Query("type") type: String,
        @Query("page") page: Int = 0,
        @Query("sort") sort: String = "views",
    ): ApiResponse<MovieListData>

    /** Titles with a recently released episode; `genre` holds the episode label, e.g. "Episode 5". */
    @GET("data/home/list_new_episode")
    suspend fun latestEpisodes(@Query("limit") limit: Int = 20): ApiResponse<MovieListData>

    /** All genres with the ids [exploreByGenre] expects. */
    @GET("3/2/explore/genre")
    suspend fun genres(): ApiResponse<GenreListData>

    @GET("3/2/explore/movie_genre")
    suspend fun exploreByGenre(
        @Query("id_genre") genreId: String,
        @Query("page") page: Int = 0,
        @Query("sort") sort: String = "views",
    ): ApiResponse<MovieListData>

    @GET("3/2/schedule/data")
    suspend fun schedule(@Query("day") day: String): ApiResponse<MovieListData>

    @GET("3/2/movie/detail/{id}")
    suspend fun detail(
        @Path("id") animeId: String,
        @Query("id_user") idUser: String = USER_ID,
        @Query("key_client") keyClient: String = CLIENT_KEY,
    ): ApiResponse<DetailData>

    /** Episodes come newest-first, [EPISODE_PAGE_SIZE] per page. */
    @GET("3/2/movie/episode/{id}")
    suspend fun episodes(
        @Path("id") animeId: String,
        @Query("page") page: Int = 0,
        @Query("search") search: String = "",
        @Query("id_user") idUser: String = USER_ID,
        @Query("key_client") keyClient: String = CLIENT_KEY,
    ): ApiResponse<EpisodeListData>

    @GET("3/2/episode/streamnew/{id}")
    suspend fun stream(@Path("id") episodeId: String): ApiResponse<StreamData>

    companion object {
        const val EPISODE_PAGE_SIZE = 30
        const val EXPLORE_PAGE_SIZE = 60
        const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 10; Infinix X680 Build/QP1A.190711.020)"
        private const val USER_ID = "396454"
        private const val CLIENT_KEY = "Di63beJZx6l7uPYKXZqzEdANGhsi2e77AFbUB8rgqSzXiRh2pi"

        fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .build(),
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

        fun create(client: OkHttpClient): AnimeApiService = Retrofit.Builder()
            .baseUrl("$API_HOST/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AnimeApiService::class.java)
    }
}
