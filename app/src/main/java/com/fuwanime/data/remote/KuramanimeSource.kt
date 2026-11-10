package com.fuwanime.data.remote

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.EpisodeStream
import com.fuwanime.data.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale

const val KR_PREFIX = "kr:"

fun isKuramanimeId(id: String): Boolean = id.startsWith(KR_PREFIX)

/** Encodes anime identity as kr:<siteId>:<slug> and episode as kr:<siteId>:<slug>:<episodeNumber>. */
object KuramanimeId {
    fun anime(siteId: String, slug: String): String = "$KR_PREFIX$siteId:$slug"

    fun episode(siteId: String, slug: String, number: String): String = "$KR_PREFIX$siteId:$slug:$number"

    fun parts(id: String): Triple<String, String, String?>? {
        if (!isKuramanimeId(id)) return null
        val body = id.removePrefix(KR_PREFIX)
        val chunks = body.split(':')
        return when (chunks.size) {
            2 -> Triple(chunks[0], chunks[1], null)
            3 -> Triple(chunks[0], chunks[1], chunks[2])
            else -> null
        }
    }
}

data class KrAnimePage(val items: List<Anime>, val hasMore: Boolean)

class KuramanimeSource(private val client: OkHttpClient) {

    private val random = SecureRandom()

    private suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                .header("Referer", BASE)
            extraHeaders.forEach { (k, v) -> req.header(k, v) }
            client.newCall(req.build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Kuramanime HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }

    private suspend fun postForm(url: String, fields: Map<String, String>, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(url).post(body).header("User-Agent", UA)
                .header("Referer", BASE)
            extraHeaders.forEach { (k, v) -> req.header(k, v) }
            client.newCall(req.build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Kuramanime HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }

    suspend fun browse(order: String = "popular", page: Int = 1): KrAnimePage {
        val html = get("$BASE/anime?order_by=$order&page=$page")
        val items = parseGrid(html)
        return KrAnimePage(items, items.isNotEmpty())
    }

    suspend fun latest(page: Int = 1): KrAnimePage = browse("latest", page)

    suspend fun search(query: String): List<Anime> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val html = get("$BASE/anime?search=" + URLEncoder.encode(q, "UTF-8") + "&order_by=popular&page=1")
        return parseGrid(html)
    }

    /** Tries search first; falls back to resolving direct slug-like url patterns is not needed. */
    suspend fun schedule(day: String): List<Anime> {
        // day: monday..sunday (English lowercase) — kuramanime uses same names
        val html = get("$BASE/schedule?scheduled_day=$day")
        return parseGrid(html)
    }

    suspend fun detail(animeId: String): Anime {
        val (siteId, slug, _) = KuramanimeId.parts(animeId) ?: throw IOException("Invalid Kuramanime id")
        val html = get("$BASE/anime/$siteId/$slug")
        return parseDetail(html, siteId, slug)
    }

    suspend fun episodes(animeId: String): List<Episode> {
        val (siteId, slug, _) = KuramanimeId.parts(animeId) ?: throw IOException("Invalid Kuramanime id")
        // Episode list is usually present on an episode page; fall back to the detail page.
        val epHtml = runCatching { get("$BASE/anime/$siteId/$slug/episode/1") }.getOrDefault("")
        val parsed = parseEpisodes(epHtml, siteId, slug)
        if (parsed.isNotEmpty()) return parsed
        val detailHtml = get("$BASE/anime/$siteId/$slug")
        return parseEpisodes(detailHtml, siteId, slug)
    }

    suspend fun stream(episodeId: String): EpisodeStream {
        val (siteId, slug, epNum) = KuramanimeId.parts(episodeId) ?: throw IOException("Invalid Kuramanime id")
        val ep = epNum ?: throw IOException("Missing episode number")
        // 1) page token
        val token = fetchPageToken()
        // 2) fetch authorized player fragment
        val url = "$BASE/anime/$siteId/$slug/episode/$ep?$PAGE_TOKEN_KEY=${enc(token)}&$SERVER_KEY=kuramadrive&page=1"
        val html = postForm(url, mapOf("authorization" to AUTH_VALUE), extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"))
        val sources = parseSources(html)
        val currentEp = Episode(
            id = episodeId,
            number = ep,
            title = "Episode $ep",
        )
        return EpisodeStream(currentEp, sources)
    }

    private suspend fun fetchPageToken(): String {
        val reqId = randomId(6)
        return get(
            "$BASE/$AUTH_ROUTE_PREFIX$AUTH_ROUTE_FILE",
            extraHeaders = mapOf(
                "X-Fuck-ID" to "$AUTH_KEY:$AUTH_TOKEN",
                "X-Request-ID" to reqId,
                "X-Request-Index" to "0",
            ),
        ).trim().ifEmpty { throw IOException("Kuramanime token empty") }
    }

    private fun randomId(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(len)
        repeat(len) { sb.append(chars[random.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // --- Parsing ---

    private fun parseGrid(html: String): List<Anime> {
        // Each card: <a href="https://v20.kuramanime.ing/anime/{id}/{slug}"> ... data-setbg="COVER" ... <h5>...</h5> ... <div class="d-none"><span>STATUS</span></div>
        val out = ArrayList<Anime>()
        val cardRe = Regex(
            """<a href="https?://[^"]+/anime/(\d+)/([^"/]+)"[^>]*>\s*<div[^>]+data-setbg="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val statusRe = Regex("""<div class="d-none"><span>([^<]+)</span></div>""", RegexOption.IGNORE_CASE)
        val titleRe = Regex("""<h5[^>]*>(.*?)</h5>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val ratingRe = Regex("""<span class="actual-anime-\d+">\s*([^<]+?)\s*</span>""", RegexOption.IGNORE_CASE)
        val typeRe = Regex("""<div class="view">\s*([^<]+?)\s*</div>""", RegexOption.IGNORE_CASE)
        for (m in cardRe.findAll(html)) {
            val siteId = m.groupValues[1]
            val slug = m.groupValues[2]
            val cover = decodeHtml(m.groupValues[3]).substringBefore('?')
            val body = m.groupValues[4]
            val title = cleanText(titleRe.find(body)?.groupValues?.get(1).orEmpty()).ifBlank { slug.replace('-', ' ') }
            val statusRaw = cleanText(statusRe.find(body)?.groupValues?.get(1).orEmpty())
            val type = cleanText(typeRe.find(body)?.groupValues?.get(1).orEmpty())
            val rating = cleanText(ratingRe.find(body)?.groupValues?.get(1).orEmpty()).takeUnless { it == "?" }
            val status = when (statusRaw.lowercase(Locale.ROOT)) {
                "sedang tayang", "ongoing" -> "Ongoing"
                "selesai", "tamat", "completed" -> "Completed"
                else -> statusRaw
            }
            out += Anime(
                id = KuramanimeId.anime(siteId, slug),
                title = title,
                poster = cover,
                cover = cover,
                type = type,
                status = status,
                // rating stored nowhere in Anime model; views/favorites not on cards
            )
        }
        return out.distinctBy { it.id }
    }

    private fun parseDetail(html: String, siteId: String, slug: String): Anime {
        val title = Regex("""<div class="anime__details__title">\s*<h3>.*?</span>\s*(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?: Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)?.let { cleanText(it).substringBefore(" - Kuramanime").substringBefore(" (Episode") }
            ?: slug.replace('-', ' ')
        val alt = Regex("""<div class="anime__details__title">.*?<span>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val synopsis = Regex("""<p id="synopsisField">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let(::htmlToText).orEmpty()
        val poster = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1).orEmpty()
        val genres = Regex("""<a href="[^"]*/properties/genre/[^"]*"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            .findAll(html).map { cleanText(it.groupValues[1]) }.filter { it.isNotBlank() }.distinct().toList()
        val type = infoField(html, "Tipe").ifBlank { infoField(html, "Type") }
        val statusRaw = infoField(html, "Status")
        val status = when {
            statusRaw.contains("Sedang Tayang", true) || statusRaw.contains("Ongoing", true) -> "Ongoing"
            statusRaw.contains("Selesai", true) || statusRaw.contains("Tamat", true) || statusRaw.contains("Completed", true) -> "Completed"
            else -> statusRaw
        }
        val studio = infoField(html, "Studio")
        return Anime(
            id = KuramanimeId.anime(siteId, slug),
            title = title,
            synopsis = synopsis,
            alternativeTitles = alt,
            poster = poster,
            cover = poster,
            type = type,
            status = status,
            studio = studio,
            genres = genres,
        )
    }

    private fun infoField(html: String, label: String): String {
        // <li>...<span>Label:</span>...</div><div class="col-9"> VALUE </div>...
        val re = Regex(
            """<span>\s*$label\s*:\s*</span>.*?<div class="col-9">\s*(.*?)\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val raw = re.find(html)?.groupValues?.get(1) ?: return ""
        return cleanText(raw.replace(Regex("<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL), "$1"))
    }

    private fun parseEpisodes(html: String, siteId: String, slug: String): List<Episode> {
        // Accept any episode link for this title (class/text may vary across layouts).
        val re = Regex(
            """href="https?://[^"]+/anime/$siteId/${Regex.escape(slug)}/episode/(\d+)"""",
            RegexOption.IGNORE_CASE,
        )
        val nums = LinkedHashSet<String>()
        for (m in re.findAll(html)) nums += m.groupValues[1]
        return nums.map { n ->
            Episode(
                id = KuramanimeId.episode(siteId, slug, n),
                number = n,
                title = "Episode $n",
            )
        }
    }

    private fun parseSources(html: String): List<StreamSource> {
        val out = ArrayList<StreamSource>()
        val srcRe = Regex("""<source[^>]+src="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
        val sizeRe = Regex("""size="(\d+)"""", RegexOption.IGNORE_CASE)
        val typeRe = Regex("""type="([^"]+)"""", RegexOption.IGNORE_CASE)
        for (m in srcRe.findAll(html)) {
            val tag = m.value
            val url = decodeHtml(m.groupValues[1])
            if (url.isBlank()) continue
            val size = sizeRe.find(tag)?.groupValues?.get(1)
            val type = typeRe.find(tag)?.groupValues?.get(1).orEmpty()
            if (type.isNotBlank() && !type.contains("mp4") && !type.contains("mpegurl") && !type.contains("m3u")) continue
            val quality = if (size != null) "${size}p" else "Auto"
            out += StreamSource(url = url, quality = quality, server = "Kuramadrive")
        }
        // fallback: data-hls-src
        if (out.isEmpty()) {
            val hls = Regex("""data-hls-src="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1).orEmpty()
            if (hls.isNotBlank()) out += StreamSource(decodeHtml(hls), "Auto", "Kuramadrive")
        }
        return out.distinctBy { it.url }
    }

    private fun cleanText(s: String): String = htmlToText(s).replace(Regex("\\s+"), " ").trim()

    private fun htmlToText(s: String): String = decodeHtml(
        s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
    ).trim()

    private fun decodeHtml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { runCatching { it.groupValues[1].toInt().toChar().toString() }.getOrDefault(it.value) }

    companion object {
        const val BASE = "https://v20.kuramanime.ing"
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        // From wzl3ClXO8shDECR.js (captured 2026-11-10)
        private const val AUTH_ROUTE_PREFIX = "assets/"
        private const val AUTH_ROUTE_FILE = "Ks6sqSgloPTlHMl.txt"
        private const val AUTH_KEY = "rFj8fp1nxMuNfKq"
        private const val AUTH_TOKEN = "ijjAwj6Jze0kscx"
        private const val PAGE_TOKEN_KEY = "Ub3BzhijicHXZdv"
        private const val SERVER_KEY = "C2XAPerzX1BM7V9"
        // From leviathan.js (captured 2026-11-10)
        private const val AUTH_VALUE = "kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur"
    }
}
