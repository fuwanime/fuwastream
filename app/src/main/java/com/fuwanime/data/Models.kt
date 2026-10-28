package com.fuwanime.data

import androidx.compose.runtime.Immutable
import com.fuwanime.data.remote.API_HOST
import com.fuwanime.data.remote.AnimeDto
import com.fuwanime.data.remote.EpisodeDto
import com.fuwanime.data.remote.ServerDto

@Immutable
data class Anime(
    val id: String,
    val title: String,
    val synopsis: String = "",
    val alternativeTitles: String = "",
    val poster: String = "",
    val cover: String = "",
    val type: String = "",
    val year: String = "",
    val status: String = "",
    val views: Long = 0,
    val favorites: Long = 0,
    val studio: String = "",
    val airedStart: String = "",
    val genres: List<String> = emptyList(),
    /** Schedule only: next air time as reported by the API. */
    val nextAirTime: String = "",
) {
    val isOngoing: Boolean get() = status.equals("ONGOING", ignoreCase = true)
    val isUpcoming: Boolean get() = status.equals("UPCOMING", true) || status.equals("NOT_YET_AIRED", true)
    val displayYear: String get() = year.takeUnless { it.isBlank() || it.equals("UNKNOWN", true) }.orEmpty()
    val backdrop: String get() = cover.ifBlank { poster }
}

@Immutable
data class Episode(
    val id: String,
    val number: String,
    val title: String,
    val thumbnail: String = "",
    val releaseDate: String = "",
    val views: Long = 0,
    val isNew: Boolean = false,
) {
    /** Numeric episode index for sorting; fractional episodes (e.g. 12.5) keep their order. */
    val sortKey: Double get() = number.toDoubleOrNull() ?: Double.MAX_VALUE
}

@Immutable
data class StreamSource(
    val url: String,
    val quality: String,
    val server: String,
) {
    /** Vertical resolution parsed from labels like "720p"; 0 when unknown. */
    val height: Int get() = Regex("""(\d{3,4})""").find(quality)?.value?.toIntOrNull() ?: 0
}

/** The best source below [current] that has not been tried yet; null when there is nothing lower. */
fun List<StreamSource>.nextLowerThan(current: StreamSource, tried: Set<String>): StreamSource? =
    filter { it.height in 1 until current.height && it.url !in tried }.maxByOrNull { it.height }

/** Source whose resolution is nearest the preferred height; unlabeled sources rank last. */
fun List<StreamSource>.closestTo(preferredHeight: Int): StreamSource? =
    minByOrNull { s -> if (s.height == 0) Int.MAX_VALUE else kotlin.math.abs(s.height - preferredHeight) }

/** Where the opening sits in an anime's episodes, as marked by the user. */
data class OpMark(val startMs: Long, val endMs: Long) {
    val isValid: Boolean get() = endMs > startMs
}

/** A title from the latest-episodes feed. */
@Immutable
data class LatestRelease(val anime: Anime, val episodeLabel: String, val updatedAt: String)

/** One entry of a title's franchise list ("S1", "Movie", …) from the detail response. */
@Immutable
data class SeasonEntry(val anime: Anime, val label: String)

@Immutable
data class AnimeDetail(val anime: Anime, val seasons: List<SeasonEntry>)

@Immutable
data class EpisodeStream(
    val episode: Episode?,
    val sources: List<StreamSource>,
)

fun AnimeDto.toAnime() = Anime(
    id = id,
    title = title.trim(),
    synopsis = synopsis.orEmpty().replace("\r\n", "\n").trim(),
    alternativeTitles = synonyms.orEmpty().trim(),
    poster = imageUrl(imagePoster ?: imageCover),
    cover = imageUrl(imageCover),
    type = type.orEmpty(),
    year = year.orEmpty(),
    status = status.orEmpty(),
    views = views?.toLongOrNull() ?: 0,
    favorites = favorites?.toLongOrNull() ?: 0,
    studio = studio.orEmpty(),
    airedStart = airedStart.orEmpty(),
    // Franchise entries send "-" for no genre.
    genres = genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "-" },
    nextAirTime = keyTime.orEmpty(),
)

fun EpisodeDto.toEpisode(): Episode {
    val number = when (val i = index) {
        is Double -> if (i % 1.0 == 0.0) i.toLong().toString() else i.toString()
        null -> ""
        else -> i.toString()
    }
    return Episode(
        id = id,
        number = number,
        title = title?.trim().orEmpty().ifBlank { "Episode $number" },
        thumbnail = imageUrl(image),
        releaseDate = keyTime.orEmpty(),
        views = views?.toLongOrNull() ?: 0,
        isNew = when (val n = isNew) {
            is String -> n.trim() == "1" || n.trim().equals("true", ignoreCase = true)
            is Number -> n.toInt() == 1
            is Boolean -> n
            else -> false
        },
    )
}

/** The latest-episodes feed reuses `genre` for the episode label, so the anime gets no genres from it. */
fun AnimeDto.toLatestRelease() = LatestRelease(
    anime = toAnime().copy(genres = emptyList()),
    episodeLabel = genre?.trim()?.takeIf { it.startsWith("Episode", ignoreCase = true) }.orEmpty(),
    updatedAt = keyTimeUpdate?.trim().orEmpty(),
)

fun ServerDto.toSource() = StreamSource(
    url = link.trim(),
    quality = quality?.trim().orEmpty().ifBlank { "Auto" },
    server = name?.trim().orEmpty(),
)

/** Normalises the API's mix of absolute, protocol-relative and root-relative image paths. */
fun imageUrl(raw: String?): String {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty()) return ""
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$API_HOST$url"
        else -> "$API_HOST/$url"
    }
}
