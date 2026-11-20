package com.fuwanime.ui.explore

import com.fuwanime.data.Anime
import com.fuwanime.data.SortOrder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Every selected genre must be present (AND). "Isekai" also matches a synopsis that mentions it. */
fun matchesGenres(anime: Anime, genres: Set<String>): Boolean = genres.all { g ->
    anime.genres.any { it.equals(g, ignoreCase = true) } || (g.equals("Isekai", true) && anime.synopsis.contains("isekai", true))
}

fun pickRandom(results: List<Anime>, random: Random = Random.Default): Anime? =
    if (results.isEmpty()) null else results[random.nextInt(results.size)]

/** Jelajah header offset after the grid scrolled by [scrolledPx] (negative = content moved up): 0 shown, -[headerPx] hidden. */
fun headerOffsetAfterScroll(offsetPx: Float, scrolledPx: Float, headerPx: Float): Float =
    (offsetPx + scrolledPx).coerceIn(-headerPx, 0f)

/** Saved filters store the query separately; this JSON carries only the genre/status/type/sort state. */
fun ExploreFilters.toJson(): String = JSONObject()
    .put("genres", JSONArray(genres.toList()))
    .put("status", status.name)
    .put("type", type.name)
    .put("sort", sort.name)
    .toString()

/** Tolerant restore: unknown or missing values fall back to the defaults instead of failing the apply. */
fun exploreFiltersFromJson(json: String, query: String): ExploreFilters = runCatching {
    val obj = JSONObject(json)
    val genres = obj.optJSONArray("genres")?.let { arr ->
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { g -> g.isNotBlank() } }.toSet()
    }.orEmpty()
    ExploreFilters(
        query = query,
        genres = genres,
        status = runCatching { StatusFilter.valueOf(obj.optString("status")) }.getOrDefault(StatusFilter.ALL),
        type = runCatching { TypeFilter.valueOf(obj.optString("type")) }.getOrDefault(TypeFilter.ALL),
        sort = runCatching { SortOrder.valueOf(obj.optString("sort")) }.getOrDefault(SortOrder.POPULAR),
    )
}.getOrDefault(ExploreFilters(query = query))
