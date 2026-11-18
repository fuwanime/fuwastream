package com.fuwanime.data.manga

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

// MangaPark English catalogue (mangapark.cc), based on audited site structure.
const val MP_PREFIX = "mp:"

fun isMangaParkId(id: String): Boolean = id.startsWith(MP_PREFIX)

class MangaParkSource(private val client: OkHttpClient) {
    private val base = "https://mangapark.cc"

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", "$base/")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) null else res.body?.string()
            }
        }.getOrNull()?.takeIf { it.length > 400 }
    }

    private fun idFor(slug: String) = "$MP_PREFIX$slug"
    private fun slugOf(id: String) = id.removePrefix(MP_PREFIX).substringBefore('|').substringBefore('?')

    suspend fun search(query: String): List<Manga> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val html = get("$base/search?q=${URLEncoder.encode(q, "UTF-8")}") ?: return emptyList()
        return parseSearch(html)
    }

    /** Latest/popular-style browse using search listing (MangaPark search returns broad results). */
    suspend fun browse(page: Int = 1): MangaPage {
        // MangaPark browse pages are search-like; page 1 gives a general list.
        val html = get("$base/search?page=$page") ?: get("$base/search") ?: return MangaPage(emptyList(), 0, 0)
        val items = parseSearch(html)
        return MangaPage(items, items.size, 0)
    }

    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(4)) {
            val hit = runCatching { search(name) }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        // fallback: try slugified name directly
        for (name in names.filter { it.isNotBlank() }.distinct().take(2)) {
            val slug = slugify(name)
            val m = runCatching { detail(idFor(slug)) }.getOrNull()
            if (m != null && m.title.isNotBlank()) return m
        }
        return null
    }

    suspend fun detail(id: String): Manga {
        val slug = slugOf(id)
        val html = get("$base/manga/$slug") ?: throw IllegalStateException("Manga not found")
        return parseDetail(html, slug)
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        val slug = slugOf(id)
        val html = get("$base/manga/$slug/chapters") ?: return emptyList()
        return pickChapters(parseChapters(html, slug), "en")
    }

    suspend fun pages(chapterId: String): List<String> {
        val raw = chapterId.removePrefix(MP_PREFIX)
        // formats: "<slug>|<path>" or "<slug>|/read/..."
        val path = raw.substringAfter('|', "")
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "$base$path"
            path.isNotBlank() -> "$base/$path"
            else -> return emptyList()
        }
        val html = get(url) ?: return emptyList()
        return parsePages(html)
    }

    internal fun parseSearch(html: String): List<Manga> {
        val out = LinkedHashMap<String, Manga>()
        // Primary: JSON-LD ItemList (SearchResultsPage) gives reliable slug+name.
        val itemRe = Regex("""\{"@type":"ListItem","position":\d+,"url":"https://mangapark\.cc/manga/([^"]+)","name":"([^"]+)"\}""")
        for (m in itemRe.findAll(html)) {
            val slug = m.groupValues[1].substringBefore('?').substringBefore('/')
            val title = unescapeJson(m.groupValues[2])
            if (slug.isNotBlank() && title.isNotBlank() && slug !in out) {
                out[slug] = Manga(idFor(slug), title, null, "", emptyList(), MangaStatus.UNKNOWN, null, null, type = "Manga")
            }
        }
        if (out.isNotEmpty()) return enrichListCovers(html, out)
        // Fallback: card links
        val cardRe = Regex("""href="/manga/([^"/?#]+)"""")
        val titleAttr = Regex("""title="([^"]+)"""")
        for (m in cardRe.findAll(html)) {
            val slug = m.groupValues[1]
            if (slug.isBlank() || slug in setOf("list", "search")) continue
            val around = html.substring(m.range.first, minOf(html.length, m.range.last + 220))
            val title = titleAttr.find(around)?.groupValues?.getOrNull(1)?.let(::htmlText)?.takeIf { it.isNotBlank() }
                ?: continue
            if (slug !in out) out[slug] = Manga(idFor(slug), title, null, "", emptyList(), MangaStatus.UNKNOWN, null, null, type = "Manga")
        }
        return enrichListCovers(html, out)
    }

    private fun enrichListCovers(html: String, items: LinkedHashMap<String, Manga>): List<Manga> {
        // Try to associate nearby cover images to slugs
        val blockRe = Regex("""(?s)<a[^>]+href="/manga/([^"/?#]+)"[^>]*>.*?</a>""")
        for (b in blockRe.findAll(html)) {
            val slug = b.groupValues[1]
            val cur = items[slug] ?: continue
            if (cur.coverUrl != null) continue
            val img = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""").find(b.value)?.groupValues?.getOrNull(1)
            if (!img.isNullOrBlank()) items[slug] = cur.copy(coverUrl = absUrl(img))
        }
        return items.values.toList()
    }

    internal fun parseDetail(html: String, slug: String): Manga {
        val title = Regex("""property="og:title"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
            ?.let { htmlText(it).substringBefore(" - MangaPark").substringBefore(" |").trim() }
            ?.ifBlank { null }
            ?: Regex("""<h3[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim()
            ?: slug.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        val cover = Regex("""property="og:image"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let(::absUrl)
            ?: Regex("""<meta[^>]+name="twitter:image"[^>]+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let(::absUrl)
        val desc = Regex("""property="og:description"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim().orEmpty()
            .ifBlank {
                Regex("""<div[^>]*class="[^"]*limit-html[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                    .find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim().orEmpty()
            }
        // alt titles from Book JSON-LD alternateName/description sometimes; also page may list alternatives
        val alts = Regex(""""alternateName"\s*:\s*\[([^\]]*)\]""").find(html)?.groupValues?.getOrNull(1)
            ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> unescapeJson(m.groupValues[1]) }.filter { it.isNotBlank() }.toList() }
            .orEmpty()
        val status = when {
            Regex("status[^<]{0,80}Completed", RegexOption.IGNORE_CASE).containsMatchIn(html) -> MangaStatus.COMPLETED
            Regex("status[^<]{0,80}Ongoing|status[^<]{0,80}Releasing", RegexOption.IGNORE_CASE).containsMatchIn(html) -> MangaStatus.ONGOING
            Regex("status[^<]{0,80}Hiatus", RegexOption.IGNORE_CASE).containsMatchIn(html) -> MangaStatus.HIATUS
            Regex("status[^<]{0,80}Cancelled|status[^<]{0,80}Canceled", RegexOption.IGNORE_CASE).containsMatchIn(html) -> MangaStatus.CANCELLED
            else -> MangaStatus.UNKNOWN
        }
        val author = Regex("""class="[^"]*author[^"]*"[^>]*>.*?<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)
        val genres = dedupeGenreNames(
            Regex("""/genre/[^"]+"""").findAll(html).mapNotNull { m ->
                m.value.substringAfterLast('/').substringBefore('?').replace('-', ' ').trim().ifBlank { null }
            }.toList(),
        ).take(12)
        return Manga(
            id = idFor(slug),
            title = title,
            coverUrl = cover,
            description = desc,
            tags = genres,
            status = status,
            year = null,
            author = author,
            altTitles = alts,
            type = "Manga",
        )
    }

    internal fun parseChapters(html: String, slug: String): List<MangaChapter> {
        // Real structure: <span class="chapter-batch-meta" ... data-total="117"> and
        // <li class="item" data-number="117"><a href="/read/<slug>/en/chapter-117" ...><span><i class="flag EN"></i> Chapter 117: ...</span><span>Jul 20, 2026</span></a></li>
        val out = mutableListOf<MangaChapter>()
        val itemRe = Regex("""(?s)<li[^>]*class="[^"]*item[^"]*"[^>]*data-number="([^"]*)"[^>]*>.*?<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?</li>""")
        val spanText = Regex("""(?s)<span[^>]*>(.*?)</span>""")
        val flagRe = Regex("""class="[^"]*flag\s+([A-Z]{2})""")
        val dateRe = Regex("""\b([A-Z][a-z]{2}\s+\d{1,2},\s+\d{4})\b""")
        for (m in itemRe.findAll(html)) {
            val dataNum = m.groupValues[1].trim()
            val href = m.groupValues[2].trim()
            val inner = m.groupValues[3]
            val spans = spanText.findAll(inner).map { htmlText(it.groupValues[1]).trim() }.toList()
            val label = spans.firstOrNull()?.replace(Regex("""^[A-Z]{2}\s*"""), "")?.trim().orEmpty()
            val dateStr = spans.lastOrNull()?.let { dateRe.find(it)?.groupValues?.getOrNull(1) }.orEmpty()
            val lang = flagRe.find(inner)?.groupValues?.getOrNull(1)?.lowercase() ?: "en"
            val number = dataNum.ifBlank {
                Regex("""chapter[ -]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.getOrNull(1)
                    ?: Regex("""([0-9]+(?:\.[0-9]+)?)""").find(label)?.groupValues?.getOrNull(1)
            }
            val cleanTitle = label.replace(Regex("""^Chapter\s*[0-9.]+[:\s-]*""", RegexOption.IGNORE_CASE), "").trim().ifBlank { null }
            out += MangaChapter(
                id = "$MP_PREFIX$slug|$href",
                number = number?.ifBlank { null },
                title = cleanTitle,
                language = lang,
                group = "MangaPark",
                pages = 1,
                externalUrl = null,
                publishAt = parseDate(dateStr),
            )
        }
        if (out.isNotEmpty()) return out
        // fallback: any read links
        val readRe = Regex("""href="(/read/[^"]+chapter[^"]*)"[^>]*>([^<]*)""", RegexOption.IGNORE_CASE)
        for (m in readRe.findAll(html)) {
            val href = m.groupValues[1]
            val label = htmlText(m.groupValues[2]).trim()
            val number = Regex("""chapter[ -]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.getOrNull(1)
            out += MangaChapter("$MP_PREFIX$slug|$href", number, label.ifBlank { null }, "en", "MangaPark", 1, null, 0L)
        }
        return out.distinctBy { it.id }
    }

    internal fun parsePages(html: String): List<String> {
        // Real structure: <section class="chapter-reader"> with <img src="https://.../storage/chapters/.../*.webp" alt="Chapter 117 - Page N">
        val section = Regex("""(?s)<section[^>]*class="[^"]*chapter-reader[^"]*"[^>]*>(.*?)</section>""").find(html)?.groupValues?.getOrNull(1)
            ?: html
        val imgRe = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""")
        val imgs = imgRe.findAll(section).map { it.groupValues[1] }
            .map(::absUrl)
            .filter { u -> Regex("""\.(jpg|jpeg|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(u) }
            .filterNot { "logo" in it || "icon" in it || "avatar" in it || "no-cover" in it }
            .distinct().toList()
        if (imgs.isNotEmpty()) return imgs
        // fallback: scan whole html for chapter storage images
        return Regex("""https?://[^"'\s\\>]+/storage/chapters/[^"'\s\\<]+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value.replace("\\/", "/") }.distinct().toList()
    }

    private fun absUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        url.startsWith("/") -> "$base$url"
        else -> "$base/$url"
    }

    private fun unescapeJson(s: String): String = s.replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"").replace("\\'", "'")

    private fun slugify(name: String): String = name.lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun parseDate(raw: String): Long {
        val s = raw.trim()
        if (s.isBlank()) return 0L
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
