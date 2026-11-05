package com.fuwanime.data.meta

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Community OP/ED/recap timestamps keyed by MyAnimeList id. */
interface AniSkipApi {
    @GET("v2/skip-times/{malId}/{episode}")
    suspend fun skipTimes(
        @Path("malId") malId: Int,
        @Path("episode") episode: Int,
        @Query("types[]") types: List<String>,
        @Query("episodeLength") episodeLength: Double,
    ): AniSkipResponse

    companion object {
        fun create(client: OkHttpClient, baseUrl: String = "https://api.aniskip.com/"): AniSkipApi = TODO()
    }
}

val SKIP_TYPES = listOf("op", "ed", "mixed-op", "mixed-ed", "recap")

@JsonClass(generateAdapter = true)
data class AniSkipResponse(val found: Boolean = false, val results: List<AniSkipResult> = emptyList())

@JsonClass(generateAdapter = true)
data class AniSkipResult(val interval: AniSkipInterval, val skipType: String, val episodeLength: Double = 0.0)

@JsonClass(generateAdapter = true)
data class AniSkipInterval(val startTime: Double, val endTime: Double)
