package com.fuwanime.data.remote

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.EpisodeStream
import com.fuwanime.data.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

const val AK_PREFIX = "ak:"

fun isAnimekompiId(id: String): Boolean = id.startsWith(AK_PREFIX)

/** Encodes anime identity as ak:<slug> and episode as ak:<slug>:<episodeSlug>. */
object AnimekompiId {
    fun anime(slug: String): String = "$AK_PREFIX$slug"
    fun episode(slug: String, episodeSlug: String): String = "$AK_PREFIX$slug:$episodeSlug"

    fun parts(id: String): Pair<String, String?>? {
        if (!isAnimekompiId(id)) return null
        val body = id.removePrefix(AK_PREFIX)
        val i = body.indexOf(':')
        return if (i < 0) body to null else body.substring(0, i) to body.substring(i + 1)
    }
}

data class AkAnimePage(val items: List<Anime>, val hasMore: Boolean)

/**
 * Animekompi (v11.animekompi.sbs) additive anime source. The site is a Vue SPA over a plain
 * JSON API at /api, so no HTML scraping is needed — every endpoint returns structured JSON.
 * Re-verify endpoint shapes before reuse; they are not a documented contract.
 *
 * Endpoint map (verified 2026-11-16):
 * - Catalog: GET /api/anime?{order,page,genre,type,status,season,studio,director,subtitle,batch}
 *   page starts at 1, returns {data,page,totalPages,total}. No server-side keyword filter —
 *   use GET /api/search?q= (returns {anime[],episodes[]}).
 * - Home shelves: GET /api/home → featured, latestEpisodes, popularWeekly/Monthly/AllTime,
 *   ongoing, completed, movies, trendingToday.
 * - Schedule: GET /api/schedule → days[]{day:"Senin".."Minggu", items[]{animeSlug,title,cover,episode,time}}.
 * - Genres: GET /api/genres → [{slug,name,count}].
 * - Detail: GET /api/anime/<slug> → {anime, genres[], episodes[], batchDownloads[]}.
 * - Episode: GET /api/episodes/<episodeSlug> → {episode, anime, genres, sources[], downloads[], prev, next, episodeList}.
 *   sources[].kind "direct" (incl. same-origin /api/episodes/stream/<token> proxy that serves the
 *   mp4 directly) is playable; "embed" is an iframe page the native player cannot open.
 */
class AnimekompiSource(
    private val client: OkHttpClient,
    private val mature: suspend () -> Boolean = { false },
) {

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE$path")
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Referer", BASE + "/")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Animekompi HTTP ${r.code}")
            val text = r.body?.string().orEmpty()
            JSONObject(text)
        }
    }

    suspend fun browse(order: String = "popular", page: Int = 1): AkAnimePage {
        val json = getJson("/api/anime?order=$order&page=$page")
        val items = parseAnimeList(json.optJSONArray("data").orEmpty())
        return AkAnimePage(items, page < json.optInt("totalPages", 0))
    }

    suspend fun latest(page: Int = 1): AkAnimePage = browse("latest", page)

    suspend fun search(query: String): List<Anime> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val json = getJson("/api/search?q=" + URLEncoder.encode(q, "UTF-8"))
        return parseAnimeList(json.optJSONArray("anime").orEmpty())
    }

    suspend fun byGenre(slug: String, page: Int = 1): AkAnimePage =
        browsePage(mapOf("genre" to slug, "page" to page.toString()))

    suspend fun byType(type: String, page: Int = 1): AkAnimePage =
        browsePage(mapOf("type" to type.lowercase(), "page" to page.toString()))

    private suspend fun browsePage(params: Map<String, String>): AkAnimePage {
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, "UTF-8")
        }
        val json = getJson("/api/anime?$query")
        val items = parseAnimeList(json.optJSONArray("data").orEmpty())
        return AkAnimePage(items, json.optInt("page", 1) < json.optInt("totalPages", 0))
    }

    /** day: "Senin".."Minggu" (Indonesian weekday names). */
    suspend fun schedule(day: String): List<Anime> {
        val json = getJson("/api/schedule")
        val days = json.optJSONArray("days").orEmpty()
        for (i in 0 until days.length()) {
            val d = days.optJSONObject(i) ?: continue
            if (d.optString("day").equals(day, ignoreCase = true)) {
                val items = d.optJSONArray("items").orEmpty()
                return (0 until items.length()).mapNotNull { j ->
                    val it = items.optJSONObject(j) ?: return@mapNotNull null
                    val slug = it.optString("animeSlug")
                    if (slug.isBlank()) return@mapNotNull null
                    Anime(
                        id = AnimekompiId.anime(slug),
                        title = it.optString("title", slug),
                        poster = absolute(it.optString("cover")),
                        cover = absolute(it.optString("cover")),
                        nextAirTime = it.optString("time"),
                    )
                }
            }
        }
        return emptyList()
    }

    suspend fun detail(animeId: String): Anime {
        val (slug, _) = AnimekompiId.parts(animeId) ?: throw IOException("Invalid Animekompi id")
        val json = getJson("/api/anime/$slug")
        val a = json.optJSONObject("anime") ?: throw IOException("Animekompi detail empty")
        val genres = json.optJSONArray("genres").orEmpty().mapNames()
        return Anime(
            id = AnimekompiId.anime(slug),
            title = a.optString("title", slug),
            synopsis = a.optString("synopsis"),
            alternativeTitles = a.optString("altTitles").orEmpty(),
            poster = absolute(a.optString("banner").takeIf { it.isNotBlank() && it != "null" } ?: a.optString("cover")),
            cover = absolute(a.optString("banner").takeIf { it.isNotBlank() && it != "null" } ?: a.optString("cover")),
            type = a.optString("type"),
            year = a.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty(),
            status = when (a.optString("status").lowercase()) {
                "ongoing" -> "ONGOING"
                "completed" -> "COMPLETED"
                else -> a.optString("status")
            },
            views = a.optLong("views"),
            studio = a.optString("studio").orEmpty().ifBlank { "null" }.takeUnless { it == "null" }.orEmpty(),
            genres = genres,
        )
    }

    /** Full episode list comes with the detail payload; newest-first on page 0 matches app convention. */
    suspend fun episodes(animeId: String): List<Episode> {
        val (slug, _) = AnimekompiId.parts(animeId) ?: throw IOException("Invalid Animekompi id")
        val json = getJson("/api/anime/$slug")
        val eps = json.optJSONArray("episodes").orEmpty()
        val out = (0 until eps.length()).mapNotNull { i ->
            val e = eps.optJSONObject(i) ?: return@mapNotNull null
            val epSlug = e.optString("slug")
            if (epSlug.isBlank()) return@mapNotNull null
            Episode(
                id = AnimekompiId.episode(slug, epSlug),
                number = formatNumber(e.opt("number")),
                title = e.optString("title").ifBlank { "Episode ${e.opt("number")}" },
                releaseDate = e.optString("releasedAt"),
                views = e.optLong("views"),
            )
        }
        return out.sortedByDescending { it.sortKey }
    }

    suspend fun stream(episodeId: String): EpisodeStream {
        val (slug, epSlug) = AnimekompiId.parts(episodeId) ?: throw IOException("Invalid Animekompi id")
        val real = epSlug ?: throw IOException("Missing Animekompi episode slug")
        val json = getJson("/api/episodes/$real")
        val ep = json.optJSONObject("episode") ?: throw IOException("Animekompi episode empty")

        // Direct files first (native player); embeds kept as labeled fallbacks the player
        // surfaces last. Relative proxy URLs are same-origin (serves the mp4 directly).
        val sources = ArrayList<StreamSource>()
        val srcs = json.optJSONArray("sources").orEmpty()
        for (i in 0 until srcs.length()) {
            val s = srcs.optJSONObject(i) ?: continue
            val rawUrl = s.optString("url")
            if (rawUrl.isBlank()) continue
            val label = s.optString("label").ifBlank { "Server ${i + 1}" }
            val kind = s.optString("kind")
            val direct = kind.equals("direct", true) || rawUrl.startsWith("/")
            val url = if (rawUrl.startsWith("/")) BASE + rawUrl else rawUrl
            sources += StreamSource(
                url = url,
                quality = s.optString("quality").orEmpty().ifBlank { if (direct) "Auto" else "Embed" },
                server = "AK $label",
            )
        }
        // Native player can only open direct files: sort them ahead of embed pages.
        val ordered = sources.sortedByDescending { it.quality != "Embed" }
        val episode = Episode(
            id = episodeId,
            number = formatNumber(ep.opt("number")),
            title = ep.optString("title").ifBlank { "Episode ${ep.opt("number")}" },
            releaseDate = ep.optString("releasedAt"),
        )
        return EpisodeStream(episode, ordered.distinctBy { it.url })
    }

    // --- Parsing helpers ---

    private fun parseAnimeList(arr: JSONArray): List<Anime> {
        val out = ArrayList<Anime>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val slug = o.optString("slug")
            if (slug.isBlank()) continue
            val cover = absolute(o.optString("cover"))
            out += Anime(
                id = AnimekompiId.anime(slug),
                title = o.optString("title", slug),
                synopsis = o.optString("synopsis"),
                poster = cover,
                cover = cover,
                type = o.optString("type"),
                year = o.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty(),
                status = when (o.optString("status").lowercase()) {
                    "ongoing" -> "ONGOING"
                    "completed" -> "COMPLETED"
                    else -> o.optString("status")
                },
                views = o.optLong("views"),
                genres = o.optJSONArray("genres").orEmpty().mapNames(),
            )
        }
        return out.distinctBy { it.id }
    }

    private fun absolute(raw: String): String {
        val url = raw.trim()
        if (url.isBlank() || url == "null") return ""
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "$BASE$url"
            else -> "$BASE/$url"
        }
    }

    private fun formatNumber(raw: Any?): String = when (val n = raw) {
        is Number -> if (n.toDouble() % 1.0 == 0.0) n.toLong().toString() else n.toString()
        null -> ""
        else -> n.toString().trim()
    }

    private fun JSONArray?.mapNames(): List<String> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val name = arr.optJSONObject(i)?.optString("name").orEmpty()
            name.ifBlank { arr.optString(i) }.ifBlank { null }
        }
    }

    /** Kotlin's built-in orEmpty() works for String?; JSONArray? needs this since optJSONArray can be null. */
    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray("[]")

    companion object {
        const val BASE = "https://v11.animekompi.sbs"
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
