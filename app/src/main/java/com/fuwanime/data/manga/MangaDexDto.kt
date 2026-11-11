package com.fuwanime.data.manga

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// MangaDex sends [] instead of {} for empty localized maps, so those fields are Any? (read via loc()).
@JsonClass(generateAdapter = true)
data class MangaListDto(
    @Json(name = "data") val data: List<MangaDto> = emptyList(),
    @Json(name = "total") val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MangaDetailDto(@Json(name = "data") val data: MangaDto? = null)

@JsonClass(generateAdapter = true)
data class MangaDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: MangaAttributesDto = MangaAttributesDto(),
    @Json(name = "relationships") val relationships: List<RelationshipDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MangaAttributesDto(
    @Json(name = "title") val title: Any? = null,
    @Json(name = "altTitles") val altTitles: List<Any?> = emptyList(),
    @Json(name = "description") val description: Any? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "originalLanguage") val originalLanguage: String? = null,
    @Json(name = "tags") val tags: List<TagDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RelationshipDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "attributes") val attributes: RelationshipAttributesDto? = null,
)

@JsonClass(generateAdapter = true)
data class RelationshipAttributesDto(
    @Json(name = "fileName") val fileName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: Any? = null,
    @Json(name = "status") val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class TagListDto(@Json(name = "data") val data: List<TagDto> = emptyList())

@JsonClass(generateAdapter = true)
data class TagDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: TagAttributesDto = TagAttributesDto(),
)

@JsonClass(generateAdapter = true)
data class TagAttributesDto(
    @Json(name = "name") val name: Any? = null,
    @Json(name = "group") val group: String = "",
)

@JsonClass(generateAdapter = true)
data class FeedDto(
    @Json(name = "data") val data: List<ChapterDto> = emptyList(),
    @Json(name = "total") val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: ChapterAttributesDto = ChapterAttributesDto(),
    @Json(name = "relationships") val relationships: List<RelationshipDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ChapterAttributesDto(
    @Json(name = "chapter") val chapter: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "translatedLanguage") val translatedLanguage: String = "",
    @Json(name = "pages") val pages: Int = 0,
    @Json(name = "externalUrl") val externalUrl: String? = null,
    @Json(name = "publishAt") val publishAt: String? = null,
    /** When the chapter actually became readable; unlike [publishAt] it is not far in the future. */
    @Json(name = "readableAt") val readableAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AtHomeDto(
    @Json(name = "baseUrl") val baseUrl: String = "",
    @Json(name = "chapter") val chapter: AtHomeChapterDto = AtHomeChapterDto(),
)

@JsonClass(generateAdapter = true)
data class AtHomeChapterDto(
    @Json(name = "hash") val hash: String = "",
    @Json(name = "data") val data: List<String> = emptyList(),
    @Json(name = "dataSaver") val dataSaver: List<String> = emptyList(),
)
