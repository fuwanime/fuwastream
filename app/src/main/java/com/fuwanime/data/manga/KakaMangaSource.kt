package com.fuwanime.data.manga

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// MangaKakalot / KakaManga-family English catalogue (kakamanga.com, mangakakalot.gg, etc.),
// based on audited structure: detail /manga/<slug>, chapters via /api/manga/<slug>/chapters (JSON),
// reader /manga/<slug>/<chapter-slug> with .container-chapter-reader images.
const val KK_PREFIX = "kk:"

fun isKakaMangaId(id: String): Boolean = id.startsWith(KK_PREFIX)

class KakaMangaSource(private val client: OkHttpClient) {
    private val mirrors = listOf(
        "https://www.kakamanga.com",
        "https://www.mangakakalot.gg",
        "https://www.natomanga.com",
        "https://www.nelomanga.com",
    )
    private val moshi = com.squareup.moshi.Moshi.Builder().build()
    private val mapAdapter = moshi.adapter(Map::class.java)

    private fun slugOf(id: String) = id.removePrefix(KK_PREFIX).substringBefore('|').substringBefore('?')
    private fun idFor(slug: String, base: String? = null) = if (base != null) "$KK_PREFIX$slug|$base" else "$KK_PREFIX$slug"

    private suspend fun get(url: String, referer: String? = null): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply { if (referer != null) header("Referer", referer) }
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) null else res.body?.string()
            }
        }.getOrNull()?.takeIf { it.length > 300 && "Just a moment" !in it }
    }

    suspend fun search(query: String): List<Manga> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val slugQ = q.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val enc = URLEncoder.encode(q, "UTF-8")
        val paths = listOf(
            "/search/story/$slugQ",
            "/search/story/${q.lowercase(Locale.ENGLISH).replace(Regex("\\s+"), "_")}",
            "/search/story/${enc.replace("+", "_")}",
        )
        for (path in paths.distinct()) {
            for (base in mirrors) {
                val html = get("$base$path", "$base/") ?: continue
                val items = parseSearch(html, base)
                if (items.isNotEmpty()) return items
            }
        }
        // fallback: try to resolve directly by slugified title (works even if search blocked)
        val direct = directBySlug(q)
        return if (direct != null) listOf(direct) else emptyList()
    }

    private suspend fun directBySlug(query: String): Manga? {
        val slug = slugify(query)
        if (slug.isBlank()) return null
        return runCatching { detail(idFor(slug)) }.getOrNull()?.takeIf { it.title.isNotBlank() }
    }

    suspend fun browse(page: Int = 1): MangaPage {
        for (base in mirrors) {
            val html = get("$base/manga-list/latest-manga?page=$page", "$base/")
                ?: get("$base/manga-list/hot-manga?page=$page", "$base/") ?: continue
            val items = parseList(html, base)
            if (items.isNotEmpty()) return MangaPage(items, items.size, 0)
        }
        return MangaPage(emptyList(), 0, 0)
    }

    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(4)) {
            val hit = runCatching { search(name) }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        for (name in names.filter { it.isNotBlank() }.distinct().take(2)) {
            val hit = runCatching { directBySlug(name) }.getOrNull()
            if (hit != null) return hit
        }
        return null
    }

    suspend fun detail(id: String): Manga {
        val slug = slugOf(id)
        val hintBase = id.removePrefix(KK_PREFIX).substringAfter('|', "").substringBefore('|').takeIf { it.startsWith("http") }
        val bases = (if (hintBase != null) listOf(hintBase) else emptyList()) + mirrors
        for (base in bases.distinct()) {
            val html = get("$base/manga/$slug", "$base/") ?: continue
            val m = parseDetail(html, slug, base)
            if (m.title.isNotBlank()) return m
        }
        throw IllegalStateException("Manga not found")
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        val slug = slugOf(id)
        val hintBase = id.removePrefix(KK_PREFIX).substringAfter('|', "").substringBefore('|').takeIf { it.startsWith("http") }
        val bases = (if (hintBase != null) listOf(hintBase) else emptyList()) + mirrors
        for (base in bases.distinct()) {
            // Preferred: real chapters API
            val api = get("$base/api/manga/$slug/chapters", "$base/manga/$slug")
            if (api != null) {
                val ch = parseChaptersApi(api, slug, base)
                if (ch.isNotEmpty()) return pickChapters(ch, "en")
            }
            // Fallback: parse chapter rows from detail HTML
            val html = get("$base/manga/$slug", "$base/") ?: continue
            val ch = parseChaptersHtml(html, slug, base)
            if (ch.isNotEmpty()) return pickChapters(ch, "en")
        }
        return emptyList()
    }

    suspend fun pages(chapterId: String): List<String> {
        val raw = chapterId.removePrefix(KK_PREFIX)
        val parts = raw.split('|')
        // formats: slug|base|chapterSlug  OR slug|chapterSlug  OR slug|/manga/...
        val slug = parts.getOrNull(0).orEmpty()
        var baseHint: String? = null
        var chap: String? = null
        when {
            parts.size >= 3 -> { baseHint = parts[1].takeIf { it.startsWith("http") }; chap = parts[2] }
            parts.size == 2 -> {
                val p1 = parts[1]
                if (p1.startsWith("http")) chap = null else if (p1.startsWith("/")) chap = p1 else { baseHint = null; chap = p1 }
            }
        }
        val candidates = mutableListOf<String>()
        if (chap != null) {
            if (chap.startsWith("http")) candidates += chap
            else {
                val bases = (if (baseHint != null) listOf(baseHint) else emptyList()) + mirrors
                for (b in bases.distinct()) {
                    candidates += if (chap.startsWith("/")) "$b$chap" else "$b/manga/$slug/$chap"
                }
            }
        } else {
            for (b in (if (baseHint != null) listOf(baseHint) else mirrors)) candidates += "$b/manga/$slug"
        }
        for (url in candidates.distinct()) {
            val ref = runCatching {
                val u = java.net.URI(url)
                "${u.scheme}://${u.host}/"
            }.getOrDefault(mirrors.first())
            val html = get(url, ref) ?: continue
            val imgs = parsePages(html)
            if (imgs.isNotEmpty()) return imgs
        }
        return emptyList()
    }

    internal fun parseSearch(html: String, base: String): List<Manga> = parseList(html, base)

    internal fun parseList(html: String, base: String): List<Manga> {
        val out = LinkedHashMap<String, Manga>()
        val itemRe = Regex("""(?s)<(?:div|li)[^>]*class="[^"]*(?:story_item|search-story-item|list-truyen-item)[^"]*"[^>]*>(.*?)</(?:div|li)>""")
        val linkRe = Regex("""href="[^"]*/manga/([^"/?#]+)[^"]*"""")
        val imgRe = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""")
        val titleRe = Regex("""<h3[^>]*>.*?<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val titleAttr = Regex("""title="([^"]+)"""")
        for (m in itemRe.findAll(html)) {
            val block = m.groupValues[1]
            val slug = linkRe.find(block)?.groupValues?.getOrNull(1) ?: continue
            if (slug in setOf("list", "search", "genre", "latest", "hot")) continue
            val img = imgRe.find(block)?.groupValues?.getOrNull(1)?.let { absUrl(it, base) }
            val title = titleRe.find(block)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim()?.ifBlank { null }
                ?: titleAttr.find(block)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim()?.ifBlank { null }
                ?: continue
            if (slug !in out) out[slug] = Manga(idFor(slug, base), title, img, "", emptyList(), MangaStatus.UNKNOWN, null, null, type = "Manga")
        }
        if (out.isEmpty()) {
            val generic = Regex("""<a[^>]+href="[^"]*/manga/([^"/?#]+)"[^>]*title="([^"]+)"[^>]*>""")
            for (m in generic.findAll(html)) {
                val slug = m.groupValues[1]
                if (slug in setOf("list", "search", "genre", "latest", "hot")) continue
                val title = htmlText(m.groupValues[2]).trim()
                if (slug !in out && title.isNotBlank()) out[slug] = Manga(idFor(slug, base), title, null, "", emptyList(), MangaStatus.UNKNOWN, null, null, type = "Manga")
            }
        }
        return out.values.toList()
    }

    internal fun parseDetail(html: String, slug: String, base: String): Manga {
        val title = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim()?.ifBlank { null }
            ?: Regex("""property="og:title"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
                ?.substringBefore(" |")?.substringBefore(" - Manga")?.let(::htmlText)?.trim()?.ifBlank { null }
            ?: slug.replace('-', ' ')
        val cover = Regex("""<div[^>]*class="[^"]*manga-info-pic[^"]*"[^>]*>.*?<img[^>]+(?:src|data-src)="([^"]+)"""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""class="[^"]*info-image[^"]*"[^>]*>.*?<img[^>]+(?:src|data-src)="([^"]+)"""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""property="og:image"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
        val desc = Regex("""<div[^>]*id="(?:noidungm|panel-story-info-description|contentBox)"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim().orEmpty()
            .ifBlank { Regex("""property="og:description"\s+content="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)?.let(::htmlText)?.trim().orEmpty() }
        val infoBlock = Regex("""<ul[^>]*class="[^"]*manga-info-text[^"]*"[^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL).find(html)?.value ?: html
        val statusText = Regex("""Status\s*:?\s*</[^>]+>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(infoBlock)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("""Status\s*:?\s*([^<\n]+)""", RegexOption.IGNORE_CASE).find(infoBlock)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val status = when {
            statusText.contains("ongoing", true) -> MangaStatus.ONGOING
            statusText.contains("completed", true) -> MangaStatus.COMPLETED
            statusText.contains("hiatus", true) -> MangaStatus.HIATUS
            else -> MangaStatus.UNKNOWN
        }
        val authors = Regex("""Author[^<]*</[^>]+>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).find(infoBlock)?.groupValues?.getOrNull(1)
            ?.let { Regex("""<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(it).map { m -> htmlText(m.groupValues[1]).trim() }.filter { it.isNotBlank() }.toList() }.orEmpty()
        val genres = dedupeGenreNames(
            Regex("""Genres?[^<]*</[^>]+>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).find(infoBlock)?.groupValues?.getOrNull(1)
                ?.let { Regex("""<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(it).map { m -> htmlText(m.groupValues[1]).trim() }.filter { it.isNotBlank() }.toList() }
                .orEmpty(),
        )
        val alts = Regex("""Alternative[^<]*</[^>]+>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(infoBlock)?.groupValues?.getOrNull(1)
            ?.split(';', ',', '/')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        return Manga(
            id = idFor(slug, base),
            title = title,
            coverUrl = cover?.let { absUrl(it, base) },
            description = desc,
            tags = genres,
            status = status,
            year = null,
            author = authors.joinToString().ifBlank { null },
            altTitles = alts,
            type = "Manga",
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseChaptersApi(json: String, slug: String, base: String): List<MangaChapter> {
        val root = runCatching { mapAdapter.fromJson(json) as? Map<String, Any?> }.getOrNull() ?: return emptyList()
        if (root["success"] != true) return emptyList()
        val data = root["data"] as? Map<String, Any?> ?: return emptyList()
        val chapters = data["chapters"] as? List<*> ?: return emptyList()
        val out = ArrayList<MangaChapter>(chapters.size)
        for (c in chapters) {
            val m = c as? Map<String, Any?> ?: continue
            val chSlug = m["chapter_slug"]?.toString()?.trim().orEmpty()
            val chName = m["chapter_name"]?.toString()?.trim().orEmpty()
            val numRaw = m["chapter_num"]
            val number = when (numRaw) {
                is Number -> {
                    val d = numRaw.toDouble()
                    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                }
                is String -> numRaw.trim().ifBlank { null }
                else -> null
            } ?: Regex("""([0-9]+(?:\.[0-9]+)?)""").find(chSlug)?.groupValues?.getOrNull(1)
            val updated = m["updated_at"]?.toString().orEmpty()
            val publishAt = parseIsoDate(updated)
            if (chSlug.isBlank()) continue
            val cleanTitle = chName.replace(Regex("""^Chapter\s*[0-9.]+[:\s-]*""", RegexOption.IGNORE_CASE), "").trim().ifBlank { null }
            out += MangaChapter(
                id = "$KK_PREFIX$slug|$base|$chSlug",
                number = number,
                title = cleanTitle,
                language = "en",
                group = "MangaKakalot",
                pages = 1,
                externalUrl = null,
                publishAt = publishAt,
            )
        }
        return out
    }

    internal fun parseChaptersHtml(html: String, slug: String, base: String): List<MangaChapter> {
        val out = mutableListOf<MangaChapter>()
        val rowRe = Regex("""(?s)<(?:li|div)[^>]*class="[^"]*(?:a-h|row|chapter-list)[^"]*"[^>]*>.*?</(?:li|div)>""")
        val rows = rowRe.findAll(html).toList().ifEmpty {
            Regex("""(?s)<li[^>]*>.*?href="[^"]*(?:/chapter)[^"]*".*?</li>""").findAll(html).toList()
        }
        val linkRe = Regex("""href="([^"]+/chapter-[^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val numRe = Regex("""chapter[ -]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        for (row in rows) {
            val lm = linkRe.find(row.value) ?: continue
            val href = lm.groupValues[1].trim()
            val chSlug = href.substringAfterLast('/').substringBefore('?')
            val label = htmlText(lm.groupValues[2]).trim()
            val number = numRe.find(chSlug)?.groupValues?.getOrNull(1) ?: numRe.find(label)?.groupValues?.getOrNull(1)
            val time = Regex("""title="([^"]+)"""").find(row.value)?.groupValues?.getOrNull(1).orEmpty()
            val publishAt = parseIsoDate(time).takeIf { it > 0 } ?: parseRelative(time)
            out += MangaChapter("$KK_PREFIX$slug|$base|$chSlug", number, label.ifBlank { null }, "en", "MangaKakalot", 1, null, publishAt)
        }
        return out.distinctBy { it.id }
    }

    internal fun parsePages(html: String): List<String> {
        val section = Regex("""(?s)<div[^>]*class="[^"]*container-chapter-reader[^"]*"[^>]*>(.*?)</div>\s*(?:<div class="chapter|<div style="text-align|<footer|$)""").find(html)?.groupValues?.getOrNull(1)
            ?: html
        val imgRe = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""")
        val imgs = imgRe.findAll(section).map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .map { absUrl(it, mirrors.first()) }
            .filter { u -> Regex("""\.(jpg|jpeg|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(u) || "/2xstorage" in u || "storage.com" in u }
            .filterNot { "logo" in it || "avatar" in it || "icon" in it || "/thumb/" in it }
            .distinct().toList()
        return imgs
    }

    private fun absUrl(url: String, base: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        url.startsWith("/") -> base.trimEnd('/') + url
        else -> base.trimEnd('/') + "/" + url
    }

    private fun slugify(name: String): String = name.lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun parseIsoDate(raw: String): Long {
        val s = raw.trim()
        if (s.isBlank()) return 0L
        // 2025-10-26T06:14:51.000000Z or 2025-10-26
        return try {
            val clean = s.replace(" ", "T")
            val fmt = when {
                clean.contains('.') -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
                clean.contains('T') && clean.endsWith("Z") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
                clean.contains('T') -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            }
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(clean.take(fmt.toPattern().replace("'", "").length.coerceAtLeast(clean.length)).let { clean })?.time ?: 0L
        } catch (_: Exception) {
            // retry with simpler parse
            try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.take(19))?.time ?: 0L } catch (_: Exception) { 0L }
        }
    }

    private fun parseRelative(raw: String): Long {
        val s = raw.trim().lowercase(Locale.ENGLISH)
        val n = Regex("""(\d+)""").find(s)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return 0L
        val ms = when {
            "minute" in s -> 60_000L
            "hour" in s -> 3_600_000L
            "day" in s -> 86_400_000L
            "week" in s -> 7L * 86_400_000L
            "month" in s -> 30L * 86_400_000L
            "year" in s -> 365L * 86_400_000L
            else -> return 0L
        }
        return System.currentTimeMillis() - n * ms
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
