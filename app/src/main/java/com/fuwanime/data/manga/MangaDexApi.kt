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
        @Query("availableTranslatedLanguage[]") langs: List<String>,
        @Query("contentRating[]") ratings: List<String>,
        @Query("includedTags[]") tags: List<String>,
        @Query("status[]") status: List<String>,
        @Query("includes[]") includes: List<String>,
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
