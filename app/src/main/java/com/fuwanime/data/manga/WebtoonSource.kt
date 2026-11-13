package com.fuwanime.data.manga

import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// LINE Webtoon (webtoons.com): official Naver catalogue — MangaDex-style vertical webtoons, fully in
// Indonesian and English. The Android app API (global.apis.naver.com) rejects the episode-list call from
// outside the app, so the web surfaces are used instead: the search HTML page, the mobile JSON API
// (m.webtoons.com/api/v1) for the full episode list, and the viewer HTML for the panel images.
//
// Ids: "wt:w:<titleNo>" / "wt:c:<titleNo>" (w = original webtoon, c = canvas/challenge) for titles,
// "wt:/<viewer-path>/" for chapters. Chapter ids carry the whole viewer path because the image CDN
// needs it, and the page slug cannot be rebuilt from the numbers alone.
const val WT_PREFIX = "wt:"

fun isWebtoonId(id: String): Boolean = id.startsWith(WT_PREFIX)
private fun String.raw() = removePrefix(WT_PREFIX)

/** "w" for an original (WEBTOON), "c" for a canvas/challenge title. */
private const val WT_WEBTOON = "w"
private const val WT_CANVAS = "c"

private fun titleKind(webtoonType: String?): String =
    if (webtoonType == null || webtoonType.equals("WEBTOON", true)) WT_WEBTOON else WT_CANVAS

/** Title ids keep the kind: "wt:w:7321". */
fun webtoonTitleId(kind: String, titleNo: String) = "$WT_PREFIX$kind:$titleNo"

/** Path segment under webtoons.com: "webtoon" (original) or "canvas" (challenge). */
private fun section(kind: String) = if (kind == WT_WEBTOON) "webtoon" else "canvas"

/** "http://www.webtoons.com" (and https) work on the panel CDN; webtoons.com domains do not. */
internal const val WT_REFERER = "http://www.webtoons.com"

private val WT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

/** True when a URL is on Webtoon's image CDN and needs the Referer header to avoid HTTP 403. */
fun isWebtoonImage(url: String): Boolean =
    url.contains("webtoon-phinf.pstatic.net") || url.contains("swebtoon-phinf.pstatic.net") || url.contains("webtoons-static.pstatic.net")

private fun parseYmdt(ms: Long?): Long = ms ?: 0L

// ---------------------------------------------------------------------------
// Search HTML (https://www.webtoons.com/id/search?keyword=...)
// ---------------------------------------------------------------------------

/** One result card from the search page. Originals live under /id/<genre>/<slug>/, canvas under /id/canvas/. */
fun parseWebtoonSearch(html: String): List<Manga> {
    val anchor = Regex(
        """<a href="(https://www\.webtoons\.com/(?:id|en)/([^"]+?)/list\?title_no=(\d+))"[^>]*class="link _card_item"[^>]*data-webtoon-type="(\w+)"[^>]*>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    val matches = anchor.findAll(html).toList()
    return matches.mapIndexed { i, m ->
        // Each card's fields sit between its anchor and the next one.
        val block = html.substring(m.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: minOf(m.range.last + 3000, html.length))
        val title = Regex("""<strong class="title">(.*?)</strong>""", RegexOption.DOT_MATCHES_ALL).find(block)
            ?.groupValues?.get(1)?.let(::htmlText).orEmpty()
        val author = Regex("""<div class="author">([^<]*)</div>""").find(block)?.groupValues?.get(1)?.let(::htmlText)
        val cover = Regex("""<img[^>]+src="([^"]+)"""").find(block)?.groupValues?.get(1)
        if (title.isBlank()) return@mapIndexed null
        Manga(
            id = webtoonTitleId(titleKind(m.groupValues[4]), m.groupValues[3]),
            title = title,
            coverUrl = cover?.substringBefore('?'),
            description = "",
            tags = emptyList(),
            status = MangaStatus.UNKNOWN,
            year = null,
            author = author?.ifBlank { null },
        )
    }.filterNotNull().toList()
}

// ---------------------------------------------------------------------------
// Title detail HTML (https://www.webtoons.com/{section}/.../list?title_no=...)
// ---------------------------------------------------------------------------

fun parseWebtoonDetail(html: String, id: String, slugPath: String): Manga {
    val meta = { name: String ->
        Regex("""<meta property="com-linewebtoon:webtoon:$name" content="([^"]*)"\s*/?>""").find(html)?.groupValues?.get(1)
    }
    val title = Regex("""<h1 class="subj">(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL).find(html)
        ?.groupValues?.get(1)?.let(::htmlText)
        ?: Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::htmlText)
        ?: id
    return Manga(
        id = id,
        title = title,
        coverUrl = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)?.substringBefore('?'),
        description = Regex("""<meta property="og:description" content="([^"]*)"""").find(html)
            ?.groupValues?.get(1)?.replace(Regex("""\n"""), "\n")?.let(::htmlText).orEmpty(),
        tags = Regex("""<h2 class="genre[^"]*">(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?.groupValues?.get(1)?.let(::htmlText)?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(),
        status = MangaStatus.UNKNOWN,
        year = null,
        author = meta("author")?.ifBlank { null } ?: "WEBTOON",
        // Webtoon panels are always vertical-scroll manhwa/webcomic format.
        type = "Manhwa",
        altTitles = listOf(slugPath.substringAfterLast('/')).filter { it.isNotBlank() && it != title },
    )
}

// ---------------------------------------------------------------------------
// Episode list — mobile JSON API (https://m.webtoons.com/api/v1/{section}/{titleNo}/episodes)
// ---------------------------------------------------------------------------

/** Minimal JSON field reader: the API answers flat objects with string/number values only. */
internal fun webtoonJsonString(json: String, key: String, from: Int = 0): String? =
    Regex("\"$key\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?\\d+)").find(json, from)?.let { m ->
        if (m.groupValues[2].isNotEmpty()) m.groupValues[2]
            .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/") else m.groupValues[1]
    }

/** Episode list JSON → chapters, in the order the API returned (newest first) is reversed by [WebtoonSource]. */
fun parseWebtoonEpisodes(json: String, language: String): List<MangaChapter> {
    val chapters = ArrayList<MangaChapter>()
    var i = json.indexOf("\"episodeList\"")
    if (i < 0) return chapters
    var end = json.indexOf(']', i)
    while (i < end) {
        val no = webtoonJsonString(json, "episodeNo", i)?.toLongOrNull() ?: break
        val title = webtoonJsonString(json, "episodeTitle", i)
        val viewerLink = webtoonJsonString(json, "viewerLink", i)
        val at = webtoonJsonString(json, "exposureDateMillis", i)?.toLongOrNull()
        if (viewerLink == null) break
        // Cover-art/notice rows carry no readable panels and are dropped; pages=1 marks the rest readable.
        chapters += MangaChapter(
            id = WT_PREFIX + "/" + viewerLink.removePrefix("/") + "/",
            number = no.toString(),
            title = title?.takeUnless { it.isBlank() },
            language = language,
            group = "WEBTOON",
            pages = 1,
            externalUrl = null,
            publishAt = parseYmdt(at),
        )
        i = json.indexOf("\"episodeNo\"", i + 12)
        if (i < 0 || i > end) break
    }
    return chapters
}

// ---------------------------------------------------------------------------
// Viewer HTML (panel images)
// ---------------------------------------------------------------------------

/** Panel URLs of a chapter, in reading order, without thumbnails and badges. */
fun parseWebtoonPages(html: String): List<String> {
    val start = html.indexOf("id=\"_imageList\"").takeIf { it >= 0 } ?: 0
    return Regex("""data-url="([^"]+)"""").findAll(html, start)
        .map { it.groupValues[1].replace("&amp;", "&") }
        .filter { "webtoon-phinf.pstatic.net" in it }
        .distinct()
        .toList()
}

// ---------------------------------------------------------------------------
// Source class
// ---------------------------------------------------------------------------

/** LINE Webtoon calls for [MangaRepository]: search, detail, chapters, pages. */
class WebtoonSource(private val client: OkHttpClient) {
    /** Body plus the final URL after redirects (webtoons.com redirects a wrong slug to the canonical one). */
    private suspend fun getWithUrl(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", WT_UA)
                .header("Referer", "https://m.webtoons.com/")
                .build(),
        ).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Webtoon HTTP ${r.code}")
            r.body?.string().orEmpty() to r.request.url.toString()
        }
    }

    private suspend fun get(url: String): String = getWithUrl(url).first

    /** "<genre>/<slug>" per title id, learned from search results; saves a lookup when a result is opened. */
    private val pathCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val language get() = "id"

    /** Search results from the webtoons.com HTML page (originals + canvas, Indonesian catalogue). */
    suspend fun search(query: String): List<Manga> {
        val html = get("https://www.webtoons.com/id/search?keyword=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"))
        Regex("""https://www\.webtoons\.com/(?:id|en)/([^"?]+?)/list\?title_no=(\d+)""").findAll(html).forEach { m ->
            val path = m.groupValues[1]
            val kind = if (path.startsWith("canvas/")) WT_CANVAS else WT_WEBTOON
            pathCache[webtoonTitleId(kind, m.groupValues[2])] = path
        }
        return parseWebtoonSearch(html)
    }

    /** The Webtoon entry for a title (tries each name), matched by normalised title. */
    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = runCatching { search(name) }.getOrNull()?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        return null
    }

    suspend fun detail(id: String): Manga {
        val raw = id.raw()
        val kind = raw.substringBefore(':')
        val titleNo = raw.substringAfter(':')
        // Candidates, best first: the slug seen in search results, then placeholder slugs that webtoons.com
        // redirects to the real page (genre and slug are ignored by the server, only title_no matters).
        val candidates = listOfNotNull(
            pathCache[id],
            if (kind == WT_CANVAS) "canvas/a" else "a/a",
        ).distinct()
        var last: Throwable? = null
        for (path in candidates) {
            try {
                val (html, finalUrl) = getWithUrl("https://www.webtoons.com/id/$path/list?title_no=$titleNo")
                if (!html.contains("og:title")) continue
                val realPath = Regex("""webtoons\.com/(?:id|en)/([^?]+?)/list""").find(finalUrl)?.groupValues?.get(1) ?: path
                pathCache[id] = realPath
                return parseWebtoonDetail(html, id, realPath)
            } catch (e: java.io.IOException) {
                last = e
            }
        }
        // Last resort: the title page is unreachable by number, so look it up by the number in search.
        val html = runCatching { get("https://www.webtoons.com/id/search?keyword=$titleNo") }.getOrNull()
        val path = html?.let { Regex("""https://www\.webtoons\.com/id/([^"]+?)/list\?title_no=$titleNo""").find(it)?.groupValues?.get(1) }
        if (path != null) return parseWebtoonDetail(get("https://www.webtoons.com/id/$path/list?title_no=$titleNo"), id, path)
        throw last ?: java.io.IOException("Webtoon title $titleNo not found")
    }

    /** Every episode, oldest first for reading order; the API pages through a cursor. */
    suspend fun chapters(id: String): List<MangaChapter> {
        val raw = id.raw()
        val kind = raw.substringBefore(':')
        val titleNo = raw.substringAfter(':')
        val all = ArrayList<MangaChapter>()
        var cursor = ""
        var page = 0
        while (page < MAX_EPISODE_PAGES) {
            val json = get(
                "https://m.webtoons.com/api/v1/${section(kind)}/$titleNo/episodes?pageSize=100" +
                    (if (cursor.isEmpty()) "" else "&cursor=$cursor"),
            )
            val batch = parseWebtoonEpisodes(json, language)
            if (batch.isEmpty()) break
            all += batch
            cursor = webtoonJsonString(json, "nextCursor", json.indexOf("\"episodeList\"")) ?: ""
            if (cursor.isEmpty()) break
            page++
        }
        return pickChapters(all, language)
    }

    /** Panel images of one chapter, from the viewer HTML at the path stored in the chapter id. */
    suspend fun pages(chapterId: String): List<String> {
        val raw = chapterId.raw()
        val path = raw.trim('/')
        return parseWebtoonPages(get("https://www.webtoons.com/$path"))
    }

    private companion object {
        const val MAX_EPISODE_PAGES = 30
    }
}
