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
    "averageScore popularity favourites episodes duration trailer{id site thumbnail} studios(isMain:true){nodes{name}} " +
    "tags{name rank isMediaSpoiler} nextAiringEpisode{episode airingAt} streamingEpisodes{title thumbnail} " +
    "relations{edges{relationType(version:2) node{type format title{romaji english native} synonyms}}} " +
    "characters(perPage:12,sort:[ROLE,RELEVANCE,ID]){edges{role node{name{full} image{medium}} " +
    "voiceActors(language:JAPANESE,sort:[RELEVANCE,ID]){name{full}}}}"

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
    /** AniList users who have this title on a list (the closest public equivalent of MAL members). */
    val popularity: Int? = null,
    val favourites: Int? = null,
    val episodes: Int? = null,
    /** Typical episode length in minutes. */
    val duration: Int? = null,
    val studios: AniListStudios? = null,
    val tags: List<AniListTag> = emptyList(),
    val nextAiringEpisode: AiringEpisode? = null,
    val streamingEpisodes: List<AniListStreamingEpisode> = emptyList(),
    val trailer: AniListTrailer? = null,
    val relations: AniListRelations? = null,
    val characters: AniListCharacters? = null,
)

@JsonClass(generateAdapter = true)
data class AniListStudios(val nodes: List<AniListStudio> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListStudio(val name: String? = null)

@JsonClass(generateAdapter = true)
data class AniListStreamingEpisode(val title: String? = null, val thumbnail: String? = null)

@JsonClass(generateAdapter = true)
data class FuzzyDate(val year: Int? = null)

@JsonClass(generateAdapter = true)
data class AniListTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)

@JsonClass(generateAdapter = true)
data class AniListTag(val name: String, val rank: Int? = null, val isMediaSpoiler: Boolean = false)

@JsonClass(generateAdapter = true)
data class AiringEpisode(val episode: Int, val airingAt: Long)

@JsonClass(generateAdapter = true)
data class AniListRelations(val edges: List<AniListRelationEdge> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListRelationEdge(val relationType: String? = null, val node: AniListRelationNode? = null)

@JsonClass(generateAdapter = true)
data class AniListRelationNode(
    val type: String? = null,
    val format: String? = null,
    val title: AniListTitle = AniListTitle(),
    val synonyms: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AniListCharacters(val edges: List<AniListCharacterEdge> = emptyList())

@JsonClass(generateAdapter = true)
data class AniListCharacterEdge(
    val role: String? = null,
    val node: AniListCharacter? = null,
    val voiceActors: List<AniListPerson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AniListCharacter(val name: AniListName? = null, val image: AniListImage? = null)

@JsonClass(generateAdapter = true)
data class AniListPerson(val name: AniListName? = null)

@JsonClass(generateAdapter = true)
data class AniListName(val full: String? = null)

@JsonClass(generateAdapter = true)
data class AniListImage(val medium: String? = null)

@JsonClass(generateAdapter = true)
data class AniListTrailer(val id: String? = null, val site: String? = null, val thumbnail: String? = null)
