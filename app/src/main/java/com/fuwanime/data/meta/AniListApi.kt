package com.fuwanime.data.meta

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** AniList GraphQL; used lazily per Detail screen only (their terms forbid bulk collection). */
interface AniListApi {
    @POST("/")
    suspend fun query(@Body body: GraphQlRequest): GraphQlResponse

    companion object {
        fun create(client: OkHttpClient, baseUrl: String = "https://graphql.anilist.co/"): AniListApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AniListApi::class.java)
    }
}

private const val MEDIA_FIELDS = "id idMal format seasonYear startDate{year} title{romaji english native} synonyms " +
    "averageScore tags{name rank isMediaSpoiler} nextAiringEpisode{episode airingAt}"

const val SEARCH_QUERY = "query(\$s:String){Page(perPage:6){media(search:\$s,type:ANIME){$MEDIA_FIELDS}}}"
const val BY_ID_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){$MEDIA_FIELDS}}"

@JsonClass(generateAdapter = true)
data class GraphQlRequest(val query: String, val variables: Map<String, Any?>)

@JsonClass(generateAdapter = true)
data class GraphQlResponse(val data: GraphQlData? = null)

@JsonClass(generateAdapter = true)
data class GraphQlData(
    @Json(name = "Page") val page: AniListPage? = null,
    @Json(name = "Media") val media: AniListMedia? = null,
)

@JsonClass(generateAdapter = true)
data class AniListPage(val media: List<AniListMedia> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val format: String? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null,
    val title: AniListTitle = AniListTitle(),
    val synonyms: List<String> = emptyList(),
    val averageScore: Int? = null,
    val tags: List<AniListTag> = emptyList(),
    val nextAiringEpisode: AiringEpisode? = null,
)

@JsonClass(generateAdapter = true)
data class FuzzyDate(val year: Int? = null)

@JsonClass(generateAdapter = true)
data class AniListTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)

@JsonClass(generateAdapter = true)
data class AniListTag(val name: String, val rank: Int? = null, val isMediaSpoiler: Boolean = false)

@JsonClass(generateAdapter = true)
data class AiringEpisode(val episode: Int, val airingAt: Long)
