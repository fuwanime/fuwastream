package com.fuwanime.data.manga

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

// Rawkuma (rawkuma.net): raw Japanese manga catalogue. Server-rendered WordPress pages,
// detail + chapter list in HTML, chapter images on cdn.rawkuma.com.
// Ids: "rk:<slug>" for titles, "rk:<slug>|<chapter-slug>" for chapters.
const val RK_PREFIX = "rk:"

fun isRawkumaId(id: String): Boolean = id.startsWith(RK_PREFIX)

class RawkumaSource(
    private val client: OkHttpClient,
    /** "Konten dewasa" setting: while off, Rawkuma's own listing hides explicit genres. */
    private val mature: suspend () -> Boolean = { true },
) {
    private val base = "https://rawkuma.net"
    private val moshi = com.squareup.moshi.Moshi.Builder().build()
    private val mapAdapter = moshi.adapter(Map::class.java)

    @Volatile private var searchNonce: String? = null
    @Volatile private var searchNonceAt = 0L

    private suspend fun get(url: String, referer: String = "$base/"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val call = client.newCall(req)
            call.timeout().timeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            call.execute().use { res ->
                if (!res.isSuccessful) null else res.body?.string()
            }
        }.getOrNull()?.takeIf { it.length > 300 && "Just a moment" !in it }
    }

    private suspend fun post(url: String, form: Map<String, String>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(url)
                .post(body)
                .header("User-Agent", UA)
                .header("Referer", "$base/library/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val call = client.newCall(req)
            call.timeout().timeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            call.execute().use { res ->
                if (!res.isSuccessful) null else res.body?.string()
            }
        }.getOrNull()?.takeIf { "Just a moment" !in it }
    }

    suspend fun browse(page: Int = 1): MangaPage {
        val pageNum = page.coerceAtLeast(1)
        val html = get("$base/latest-update/?the_page=$pageNum${matureQuery()}", "$base/latest-update/")
            ?: return MangaPage(emptyList(), 0, 0)
        val items = parseList(html)
        return MangaPage(items, items.size, (pageNum - 1) * 24)
    }

    suspend fun search(query: String): List<Manga> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        repeat(2) { attempt ->
            val nonce = nonce(force = attempt > 0) ?: return@repeat
            val html = post("$base/wp-admin/admin-ajax.php?nonce=$nonce&action=search", mapOf("query" to q))
                ?: return@repeat
            if (html.trim() !in setOf("0", "-1")) return parseSearch(html)
        }
        return emptyList()
    }

    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(4)) {
            val hit = runCatching { search(name) }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        for (name in names.filter { it.isNotBlank() }.distinct().take(2)) {
            val slug = slugify(name)
            if (slug.isBlank()) continue
            val m = runCatching { detail(idFor(slug)) }.getOrNull()
            if (m != null && sameTitle(name, m.title, m.altTitles)) return m
        }
        return null
    }

    suspend fun detail(id: String): Manga {
        val slug = slugOf(id)
        if (slug.isBlank()) throw IllegalStateException("Manga not found")
        val html = get("$base/manga/$slug/") ?: throw IllegalStateException("Manga not found")
        return parseDetail(html, slug)
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        val slug = slugOf(id)
        if (slug.isBlank()) return emptyList()
        val html = get("$base/manga/$slug/") ?: return emptyList()
        return pickChapters(parseChapters(html, slug), "ja")
    }

    suspend fun pages(chapterId: String): List<String> {
        val raw = chapterId.removePrefix(RK_PREFIX)
        val slug = raw.substringBefore('|').trim()
        val chapterSlug = raw.substringAfter('|', "").trim().trim('/')
        if (slug.isBlank() || chapterSlug.isBlank()) return emptyList()
        val html = get("$base/manga/$slug/$chapterSlug/", "$base/manga/$slug/") ?: return emptyList()
        return parsePages(html)
    }

    internal fun parseList(html: String): List<Manga> {
        val out = LinkedHashMap<String, Manga>()
        val titleRe = Regex("""(?s)<a[^>]+href="https://rawkuma\.net/manga/([^"/]+)/"[^>]*>\s*<h1[^>]*>(.*?)</h1>""")
        for (m in titleRe.findAll(html)) {
            val slug = m.groupValues[1]
            val title = htmlLine(m.groupValues[2])
            if (title.isBlank() || slug in out) continue
            val before = html.substring((m.range.first - 6_000).coerceAtLeast(0), m.range.first)
            val statusText = Regex(""">\s*(Ongoing|Completed|Hiatus|Cancelled)\s*<""", RegexOption.IGNORE_CASE)
                .findAll(before).lastOrNull()?.groupValues?.get(1)
            out[slug] = Manga(
                id = idFor(slug),
                title = title,
                coverUrl = coverBefore(html, m.range.first, title),
                description = "",
                tags = emptyList(),
                status = statusOf(statusText),
                year = null,
                author = null,
                type = "Manga",
            )
        }
        return out.values.toList()
    }

    internal fun parseSearch(html: String): List<Manga> {
        val out = LinkedHashMap<String, Manga>()
        val linkRe = Regex("""(?s)<a[^>]+href="https://rawkuma\.net/manga/([^"/]+)/"[^>]*>(.*?)</a>""")
        for (m in linkRe.findAll(html)) {
            val slug = m.groupValues[1]
            if (slug in setOf("manga", "genre", "library")) continue
            val block = m.groupValues[2]
            val title = Regex("""(?s)<h3[^>]*>(.*?)</h3>""").find(block)?.groupValues?.get(1)?.let(::htmlLine)
                ?: Regex("""<img[^>]+alt="([^"]+)"""", RegexOption.IGNORE_CASE).find(block)
                    ?.groupValues?.get(1)?.let(::htmlLine)
                ?: continue
            if (title.isBlank() || slug in out) continue
            val cover = Regex("""<img[^>]+>""").find(block)?.value?.let(::imgSrc)?.let(::absUrl)
            out[slug] = Manga(
                id = idFor(slug),
                title = title,
                coverUrl = cover,
                description = "",
                tags = emptyList(),
                status = MangaStatus.UNKNOWN,
                year = null,
                author = null,
                type = "Manga",
            )
        }
        return out.values.toList()
    }

    internal fun parseDetail(html: String, slug: String): Manga {
        val book = bookJsonLd(html)
        val title = jsonText(book?.get("name"))?.let(::htmlLine)?.takeIf { it.isNotBlank() }
            ?: Regex("""(?s)<h1[^>]*>(.*?)</h1>""").findAll(html)
                .map { htmlLine(it.groupValues[1]) }
                .lastOrNull { it.isNotBlank() && !it.equals("Last Updates", ignoreCase = true) }
            ?: slug.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        val cover = imageUrl(book?.get("image"))?.let(::absUrl)
            ?: imageWithClass(html, "wp-post-image")
        val description = jsonText(book?.get("description"))?.let(::htmlText)?.trim().orEmpty()
            .ifBlank {
                Regex("""(?s)itemprop="description"[^>]*>(.*?)</div>""").find(html)
                    ?.groupValues?.get(1)?.let(::htmlText)?.trim().orEmpty()
            }
        val jsonGenres = jsonStrings(book?.get("genre"))
        val genres = dedupeGenreNames(
            if (jsonGenres.isNotEmpty()) jsonGenres
            else Regex("""(?s)itemprop="genre"[^>]*>\s*<span[^>]*>(.*?)</span>""").findAll(html)
                .map { htmlLine(it.groupValues[1]) }.toList(),
        )
        val completed = book?.get("isCompleted") == true
        val status = if (completed) MangaStatus.COMPLETED else statusOf(jsonText(book?.get("creativeWorkStatus")))
        val year = yearOf(jsonText(book?.get("datePublished")) ?: jsonText(book?.get("dateCreated")))
        val authors = when (val raw = book?.get("author")) {
            is List<*> -> raw.mapNotNull(::jsonText)
            else -> listOfNotNull(jsonText(raw))
        }
        val alts = jsonStrings(book?.get("alternateName"))
            .filterNot { it.equals(title, ignoreCase = true) }
            .distinct()
        return Manga(
            id = idFor(slug),
            title = title,
            coverUrl = cover,
            description = description,
            tags = genres,
            status = status,
            year = year,
            author = authors.joinToString().ifBlank { null },
            altTitles = alts,
            type = typeFromHtml(html) ?: "Manga",
        )
    }

    internal fun parseChapters(html: String, slug: String): List<MangaChapter> {
        val starts = Regex("""data-chapter-number="([^"]*)"""").findAll(html).toList()
        val out = ArrayList<MangaChapter>(starts.size)
        for ((i, m) in starts.withIndex()) {
            val end = starts.getOrNull(i + 1)?.range?.first ?: minOf(html.length, m.range.first + 5_000)
            val block = html.substring(m.range.first, end)
            val href = Regex("""href="(https://rawkuma\.net/manga/[^"/]+/chapter-[^"]+/?)"""")
                .find(block)?.groupValues?.get(1)?.trim() ?: continue
            val chapterSlug = href.trimEnd('/').substringAfterLast('/')
            val label = Regex("""<span>\s*Chapter\s*([^<]*)</span>""").find(block)?.groupValues?.get(1)?.trim()
            val number = m.groupValues[1].trim().ifBlank {
                Regex("""^([0-9]+(?:\.[0-9]+)?)""").find(label.orEmpty())?.groupValues?.get(1)
                    ?: Regex("""chapter-([0-9]+(?:\.[0-9]+)?)""").find(chapterSlug)?.groupValues?.get(1)
            }
            val chapterTitle = label?.replace(Regex("""^[0-9]+(?:\.[0-9]+)*\s*[:\-–]?\s*"""), "")?.trim()
                ?.takeIf { it.isNotBlank() }
            val time = Regex("""<time[^>]*datetime="([^"]+)"""").find(block)?.groupValues?.get(1)
            out += MangaChapter(
                id = "$RK_PREFIX$slug|$chapterSlug",
                number = number?.ifBlank { null },
                title = chapterTitle,
                language = "ja",
                group = "Rawkuma",
                pages = 1,
                externalUrl = null,
                publishAt = parseIsoDate(time),
            )
        }
        return out.distinctBy { it.id }
    }

    internal fun parsePages(html: String): List<String> {
        val section = Regex("""(?s)<section[^>]+data-image-data[^>]*>(.*?)</section>""")
            .find(html)?.groupValues?.get(1) ?: html
        val images = Regex("""<img[^>]+>""").findAll(section).mapNotNull { imgSrc(it.value) }
            .map(::absUrl)
            .filter { "cdn.rawkuma.com" in it }
            .filter { Regex("""\.(jpg|jpeg|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .distinct().toList()
        if (images.isNotEmpty()) return images
        return Regex("""https://cdn\.rawkuma\.com/[^"'\s\\<>]+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.distinct().toList()
    }

    private suspend fun matureQuery(): String =
        if (mature()) "" else "&genre_exclude=adult,smut,ecchi,mature,hentai"

    private suspend fun nonce(force: Boolean): String? {
        val now = System.currentTimeMillis()
        val cached = searchNonce
        if (!force && cached != null && now - searchNonceAt < NONCE_TTL_MS) return cached
        val html = get("$base/library/") ?: return cached
        val fresh = Regex("""admin-ajax\.php\?nonce=([A-Za-z0-9]+)(?:&|&amp;|&#0*38;)action=search""")
            .find(html)?.groupValues?.get(1)
            ?: Regex("""nonce=([A-Za-z0-9]+)""").find(html)?.groupValues?.get(1)
            ?: return cached
        searchNonce = fresh
        searchNonceAt = now
        return fresh
    }

    @Suppress("UNCHECKED_CAST")
    private fun bookJsonLd(html: String): Map<String, Any?>? {
        val scripts = Regex("""(?s)<script type="application/ld\+json">(.*?)</script>""").findAll(html)
        for (m in scripts) {
            val parsed = runCatching { mapAdapter.fromJson(m.groupValues[1].trim()) as? Map<*, *> }.getOrNull()
                ?: continue
            val root = parsed.entries.associate { it.key.toString() to it.value }
            val types = when (val t = root["@type"]) {
                is List<*> -> t.mapNotNull { it?.toString() }
                is String -> listOf(t)
                else -> emptyList()
            }
            if (types.any { it == "Book" || it == "ComicSeries" }) return root
        }
        return null
    }

    private fun jsonText(value: Any?): String? = when (value) {
        is String -> value
        is Map<*, *> -> (value["name"] ?: value["url"])?.toString()
        is List<*> -> value.firstNotNullOfOrNull(::jsonText)
        else -> null
    }

    private fun jsonStrings(value: Any?): List<String> = when (value) {
        is String -> value.split(',')
        is List<*> -> value.mapNotNull { if (it is String) it else null }
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotBlank() }

    private fun imageUrl(value: Any?): String? = when (value) {
        is String -> value
        is Map<*, *> -> (value["url"] ?: value["contentUrl"])?.toString()
        is List<*> -> value.firstNotNullOfOrNull(::imageUrl)
        else -> null
    }

    private fun imageWithClass(html: String, className: String): String? =
        Regex("""<img[^>]*>""").findAll(html)
            .firstOrNull { className in it.value }
            ?.value?.let(::imgSrc)?.let(::absUrl)

    private fun coverBefore(html: String, end: Int, title: String): String? {
        val start = (end - 6_000).coerceAtLeast(0)
        val window = html.substring(start, end)
        val tags = Regex("""<img[^>]+>""").findAll(window).toList()
        val match = tags.lastOrNull { tag ->
            val alt = Regex("""alt="([^"]*)"""", RegexOption.IGNORE_CASE).find(tag.value)?.groupValues?.get(1)
            alt != null && htmlLine(alt).equals(title, ignoreCase = true)
        } ?: tags.lastOrNull { "wp-content/uploads" in it.value }
        return match?.value?.let(::imgSrc)?.let(::absUrl)
    }

    private fun typeFromHtml(html: String): String? =
        Regex("""(?s)<span[^>]*>\s*Type\s*</span>.{0,300}?<p[^>]*>\s*(Manga|Manhwa|Manhua)\s*</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }

    private fun statusOf(raw: String?): MangaStatus = when {
        raw == null -> MangaStatus.UNKNOWN
        raw.contains("ongoing", true) || raw.contains("releasing", true) -> MangaStatus.ONGOING
        raw.contains("completed", true) || raw.contains("finished", true) -> MangaStatus.COMPLETED
        raw.contains("hiatus", true) -> MangaStatus.HIATUS
        raw.contains("cancel", true) || raw.contains("discontinu", true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private fun yearOf(raw: String?): Int? =
        Regex("""\b(?:19|20)\d{2}\b""").find(raw.orEmpty())?.value?.toIntOrNull()

    private fun parseIsoDate(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return 0L
    }

    private fun imgSrc(tag: String): String? =
        Regex("""\sdata-src="([^"]+)"""").find(tag)?.groupValues?.get(1)
            ?: Regex("""\ssrc="([^"]+)"""").find(tag)?.groupValues?.get(1)

    private fun absUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        url.startsWith("/") -> base + url
        else -> "$base/$url"
    }

    private fun idFor(slug: String) = "$RK_PREFIX$slug"

    private fun slugOf(id: String) = id.removePrefix(RK_PREFIX).substringBefore('|').trim()

    private fun slugify(name: String): String = name.lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        const val NETWORK_TIMEOUT_MS = 6_000L
        const val NONCE_TTL_MS = 10 * 60_000L
    }
}
