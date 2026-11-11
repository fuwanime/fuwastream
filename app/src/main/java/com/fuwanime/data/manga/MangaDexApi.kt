package com.fuwanime.data.manga

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** MangaDex v5. Array params use the literal `name[]` keys; Retrofit repeats the key per list item. */
interface MangaDexApi {
    @GET("manga")
    suspend fun mangaList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("title") title: String?,
        @Query("order[followedCount]") byFollowed: String?,
        @Query("order[latestUploadedChapter]") byLatest: String?,
        @Query("order[relevance]") byRelevance: String?,
        @Query("availableTranslatedLanguage[]") langs: List<String>,
        @Query("contentRating[]") ratings: List<String>,
        @Query("includedTags[]") tags: List<String>,
        @Query("status[]") status: List<String>,
        @Query("includes[]") includes: List<String>,
        @Query("hasAvailableChapters") hasAvailableChapters: Boolean = true,
        @Query("originalLanguage[]") originalLanguages: List<String> = emptyList(),
    ): MangaListDto

    @GET("manga/{id}")
    suspend fun manga(@Path("id") id: String, @Query("includes[]") includes: List<String>): MangaDetailDto

    @GET("manga/{id}/feed")
    suspend fun feed(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("translatedLanguage[]") langs: List<String>,
        @Query("contentRating[]") ratings: List<String>,
        @Query("includes[]") includes: List<String>,
    ): FeedDto

    /**
     * Newest chapters across all manga, each with its manga included; feeds the release notifications.
     *
     * Ordered by `readableAt`, not `publishAt`: some old uploads carry a far-future `publishAt`
     * (e.g. 2037-12-31), which would pin the first pages of the feed to those chapters forever and
     * hide every real release behind them.
     */
    @GET("chapter")
    suspend fun latestChapters(
        @Query("limit") limit: Int,
        @Query("order[readableAt]") byReadableAt: String = "desc",
        @Query("translatedLanguage[]") langs: List<String>,
        @Query("contentRating[]") ratings: List<String>,
        @Query("includes[]") includes: List<String> = listOf("manga"),
    ): FeedDto

    @GET("at-home/server/{chapterId}")
    suspend fun atHome(@Path("chapterId") chapterId: String): AtHomeDto

    @GET("manga/tag")
    suspend fun tags(): TagListDto

    companion object {
        fun create(client: OkHttpClient, baseUrl: String = "https://api.mangadex.org/"): MangaDexApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(MangaDexApi::class.java)
    }
}
