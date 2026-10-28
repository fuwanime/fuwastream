package com.fuwanime.data

/** Genres most titles carry, from most to least common; they say little about what a show is like. */
private val BroadGenres = listOf(
    "action", "comedy", "fantasy", "adventure", "drama", "shounen", "romance", "school", "supernatural", "slice of life",
)

/** The two genres that best describe [genres]: specific ones first, then the least common broad ones. */
fun seedGenres(genres: List<String>): List<String> = genres
    .sortedBy { g ->
        val i = BroadGenres.indexOf(g.trim().lowercase())
        if (i < 0) 0 else BroadGenres.size - i
    }
    .take(2)

/**
 * Ranks [candidates] by genre similarity to [target] (Jaccard over all genres, small bonus for the same type,
 * popularity only as a tie-break). A candidate must share at least two genres (one if the target has only one),
 * so broad hits like One Piece no longer match everything on a single "Comedy".
 */
fun rankSimilar(target: Anime, candidates: List<Anime>, exclude: Set<String>, limit: Int = 12): List<Anime> {
    val mine = target.genres.map { it.trim().lowercase() }.toSet()
    if (mine.isEmpty()) return emptyList()
    val minShared = minOf(2, mine.size)
    return candidates
        .asSequence()
        .filter { it.id.isNotBlank() && it.id != target.id && it.id !in exclude }
        .distinctBy { it.id }
        .mapNotNull { c ->
            val theirs = c.genres.map { it.trim().lowercase() }.toSet()
            val shared = (mine intersect theirs).size
            if (shared < minShared) return@mapNotNull null
            val jaccard = shared.toDouble() / (mine union theirs).size
            val typeBonus = if (target.type.isNotBlank() && c.type.equals(target.type, ignoreCase = true)) 0.15 else 0.0
            c to jaccard + typeBonus
        }
        .sortedWith(compareByDescending<Pair<Anime, Double>> { it.second }.thenByDescending { it.first.views })
        .take(limit)
        .map { it.first }
        .toList()
}
