package com.fuwanime.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val API_HOST = "https://xyz-api.animein.net"

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "status") val status: Any? = null,
    @Json(name = "error") val error: Any? = null,
    @Json(name = "data") val data: T? = null,
)

@JsonClass(generateAdapter = true)
data class MovieListData(
    @Json(name = "movie") val movie: List<AnimeDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GenreListData(
    @Json(name = "genre") val genre: List<GenreDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GenreDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String = "",
)

@JsonClass(generateAdapter = true)
data class EpisodeListData(
    @Json(name = "episode") val episode: List<EpisodeDto>? = null,
)

@JsonClass(generateAdapter = true)
data class DetailData(
    @Json(name = "movie") val movie: AnimeDto? = null,
    // `episode` is deliberately not mapped: unused, and its shape varies (an object here, a list elsewhere).
    @Json(name = "season") val season: List<AnimeDto>? = null,
)

@JsonClass(generateAdapter = true)
data class StreamData(
    @Json(name = "episode") val episode: EpisodeDto? = null,
    @Json(name = "episode_next") val episodeNext: Any? = null,
    @Json(name = "server") val server: List<ServerDto>? = null,
)

@JsonClass(generateAdapter = true)
data class AnimeDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "synopsis") val synopsis: String? = null,
    @Json(name = "synonyms") val synonyms: String? = null,
    @Json(name = "image_poster") val imagePoster: String? = null,
    @Json(name = "image_cover") val imageCover: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "year") val year: String? = null,
    @Json(name = "day") val day: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "views") val views: String? = null,
    @Json(name = "favorites") val favorites: String? = null,
    @Json(name = "studio") val studio: String? = null,
    @Json(name = "aired_start") val airedStart: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "key_time") val keyTime: String? = null,
    /** Franchise label in detail's `season` array, e.g. "S1", "Movie". */
    @Json(name = "season") val season: String? = null,
    @Json(name = "key_time_update") val keyTimeUpdate: String? = null,
)

@JsonClass(generateAdapter = true)
data class EpisodeDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "index") val index: Any? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "key_time") val keyTime: String? = null,
    @Json(name = "views") val views: String? = null,
    @Json(name = "is_new") val isNew: Any? = null,
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "link") val link: String = "",
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "type") val type: String? = null,
)
