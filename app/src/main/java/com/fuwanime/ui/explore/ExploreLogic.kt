package com.fuwanime.ui.explore

import com.fuwanime.data.Anime
import kotlin.random.Random

/** Every selected genre must be present (AND). "Isekai" also matches a synopsis that mentions it. */
fun matchesGenres(anime: Anime, genres: Set<String>): Boolean = genres.all { g ->
    anime.genres.any { it.equals(g, ignoreCase = true) } || (g.equals("Isekai", true) && anime.synopsis.contains("isekai", true))
}

fun pickRandom(results: List<Anime>, random: Random = Random.Default): Anime? =
    if (results.isEmpty()) null else results[random.nextInt(results.size)]
