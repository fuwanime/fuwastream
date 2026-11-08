package com.fuwanime.data.manga

import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Komiku (komiku.org): ~8000 Indonesian titles incl. classics and seinen/horror. HTML pages, read with small regexes.
// Ids: "km:<slug>" for titles, "km:/<chapter-path>/" for chapters.
const val KM_PREFIX = "km:"

fun isKomikuId(id: String): Boolean = id.startsWith(KM_PREFIX)

private val ENTITY = Regex("&#(\\d+);")

/** Plain text from an HTML fragment: tags dropped, the entities Komiku uses decoded, whitespace collapsed. */
fun htmlText(s: String): String = s.replace(Regex("<[^>]+>"), "")
    .replace(ENTITY) { it.groupValues[1].toInt().toChar().toString() }
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ").trim()

private fun slugOf(href: String): String? = Regex("/manga/([^/]+)/?").find(href)?.groupValues?.get(1)

/** A list page from api.komiku.org (popular, latest, search): title, slug and cover per card. */
fun parseKomikuList(html: String): List<Manga> =
    Regex("""<div class="bge">.*?<a href="([^"]+)".*?<img[^>]+src="([^"]+)".*?<h3>\s*(.*?)\s*</h3>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(html)
        .mapNotNull { m ->
            val slug = slugOf(m.groupValues[1]) ?: return@mapNotNull null
            Manga(
                id = KM_PREFIX + slug,
                title = htmlText(m.groupValues[3]),
                coverUrl = htmlText(m.groupValues[2]),
                description = "",
                tags = emptyList(),
                status = MangaStatus.UNKNOWN,
                year = null,
                author = null,
            )
        }
        .distinctBy { it.id }
        .toList()

private fun infoRow(html: String, label: String): String? =
    Regex("""<td>$label:</td><td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::htmlText)?.ifBlank { null }

fun parseKomikuDetail(html: String, slug: String): Manga {
    val title = infoRow(html, "Judul")
        ?: Regex("""<meta property="og:title" content="(?:Komik )?([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::htmlText)
        ?: slug
    return Manga(
        id = KM_PREFIX + slug,
        title = title,
        coverUrl = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)?.substringBefore('?'),
        description = Regex("""<p class="desc"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::htmlText).orEmpty(),
        tags = Regex("""<li class="genre">.*?<span>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).findAll(html).map { htmlText(it.groupValues[1]) }.distinct().toList(),
        status = when (infoRow(html, "Status")?.lowercase()) {
            "ongoing" -> MangaStatus.ONGOING
            "end", "completed", "tamat" -> MangaStatus.COMPLETED
            else -> MangaStatus.UNKNOWN
        },
        year = null,
        author = infoRow(html, "Author"),
        altTitles = infoRow(html, "Judul Alternatif")?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() && it != title }.orEmpty(),
    )
}

/** The chapter table of a title page, newest first as Komiku lists it. Page counts are only known when read; 1 marks "readable". */
fun parseKomikuChapters(html: String): List<MangaChapter> {
    val date = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    return Regex("""<td class="judulseries">.*?<a href="(/[^"]+)"[^>]*>.*?<b>(.*?)</b>.*?<td class="tanggalseries">\s*([^<]*?)\s*</td>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(html)
        .map { m ->
            val label = htmlText(m.groupValues[2])
            MangaChapter(
                id = KM_PREFIX + m.groupValues[1],
                number = Regex("""(\d+(?:\.\d+)?)""").find(label)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" },
                title = label.takeUnless { Regex("""^Chapter\s+[\d.]+$""").matches(it) },
                language = "id",
                group = "Komiku",
                pages = 1,
                externalUrl = null,
                publishAt = runCatching { date.parse(m.groupValues[3])!!.time }.getOrDefault(0L),
            )
        }
        .toList()
}

/** Page images of a chapter, without Komiku's own promo banners. */
fun parseKomikuPages(html: String): List<String> {
    val start = html.indexOf("id=\"Baca_Komik\"").takeIf { it >= 0 } ?: 0
    return Regex("""<img[^>]+src="(https://img\.komiku[^"]+)"""").findAll(html, start)
        .map { htmlText(it.groupValues[1]) }
        .filter { "/upload" in it }
        .distinct()
        .toList()
}

class KomikuSource(private val client: OkHttpClient) {
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Komiku HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    /** [order]: "meta_value_num" (popular) or "modified" (latest). 10 titles per page. */
    suspend fun browse(order: String, page: Int = 1): MangaPage {
        val path = if (page <= 1) "manga/" else "manga/page/$page/"
        val items = parseKomikuList(get("$API$path?orderby=$order"))
        return MangaPage(items, if (items.isEmpty()) 0 else Int.MAX_VALUE, (page - 1) * 10)
    }

    suspend fun search(query: String): List<Manga> =
        parseKomikuList(get("$API?post_type=manga&s=" + java.net.URLEncoder.encode(query.trim(), "UTF-8")))

    suspend fun detail(id: String): Manga {
        val slug = id.removePrefix(KM_PREFIX)
        return parseKomikuDetail(get("$SITE/manga/$slug/"), slug)
    }

    suspend fun chapters(id: String): List<MangaChapter> =
        pickChapters(parseKomikuChapters(get("$SITE/manga/${id.removePrefix(KM_PREFIX)}/")), "id")

    suspend fun pages(chapterId: String): List<String> = parseKomikuPages(get(SITE + chapterId.removePrefix(KM_PREFIX)))

    /** The Komiku entry for a title (tries each name), matched by normalised title. */
    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = runCatching { search(name) }.getOrNull()?.firstOrNull { sameTitle(name, it.title, emptyList()) }
            if (hit != null) return hit
        }
        return null
    }

    private companion object {
        const val SITE = "https://komiku.org"
        const val API = "https://api.komiku.org/"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    }
}
