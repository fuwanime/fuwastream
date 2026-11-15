package com.fuwanime.data.manga

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.squareup.moshi.Moshi
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.OkHttpClient
import okhttp3.Request

/** Comix (comix.ws), the primary English catalogue. API ids are prefixed so repository routing stays unambiguous. */
const val CX_PREFIX = "cx:"

fun isComixId(id: String): Boolean = id.startsWith(CX_PREFIX)

private const val COMIX_ORIGIN = "https://comix.ws"
private const val COMIX_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

private fun Any?.map(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
private fun Any?.list(): List<Any?> = this as? List<Any?> ?: emptyList()
private fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()?.takeUnless { it == "null" }
private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt() ?: string(key)?.toDoubleOrNull()?.toInt()

/** Comix renames its minified JSON fields on some deploys, so every field is read through its known aliases. */
private fun Map<String, Any?>.firstString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { string(it)?.takeIf(String::isNotBlank) }
private fun Map<String, Any?>.firstInt(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { int(it) }
private fun Map<String, Any?>.tagNames(vararg keys: String): List<String> =
    keys.flatMap { this[it].list() }
        .mapNotNull { item ->
            (item as? String)?.takeIf(String::isNotBlank)
                ?: item.map().firstString("title", "Z2", "k_", "rf")
        }
        .distinct()

private fun absoluteComixUrl(path: String?): String? = path?.let { if (it.startsWith("http")) it else COMIX_ORIGIN + it }

internal fun comixManga(data: Any?): Manga {
    val m = data.map()
    val hid = m.firstString("hid", "rU", "SZ", "fH").orEmpty()
    val title = m.firstString("title", "Z2", "k_", "rf").orEmpty()
    val poster = m["poster"].map().ifEmpty { m["P7"].map() }.ifEmpty { m["fL"].map() }.ifEmpty { m["Zs"].map() }
    return Manga(
        id = CX_PREFIX + hid,
        title = title,
        coverUrl = absoluteComixUrl(poster.firstString("large", "medium", "cT", "Tr", "sP", "HS", "QC", "Kq")),
        description = m.firstString("synopsis", "Ht", "U$", "U7", "fK").orEmpty(),
        tags = m.tagNames("tags", "_v", "Tw", "QT", "Il", "Dx", "gS", "UR", "J5", "hV", "A5", "dE"),
        status = when (m.firstString("status", "cB", "yx", "oF")) {
            "releasing" -> MangaStatus.ONGOING
            "finished" -> MangaStatus.COMPLETED
            "on_hiatus" -> MangaStatus.HIATUS
            "discontinued" -> MangaStatus.CANCELLED
            else -> MangaStatus.UNKNOWN
        },
        year = m.firstInt("year", "Hb", "Pi", "Pa", "_r"),
        author = m["authors"].list().ifEmpty { m["TH"].list() }.ifEmpty { m["NR"].list() }
            .mapNotNull { item -> (item as? String) ?: item.map().firstString("title", "Z2", "k_", "rf") }
            .joinToString().ifBlank { null }
            ?: m["ZN"].list().ifEmpty { m["uy"].list() }.mapNotNull { it.map().firstString("Z2", "k_", "rf") }
                .joinToString().ifBlank { null },
        altTitles = m["altTitles"].list().ifEmpty { m["Jy"].list() }.ifEmpty { m["Hv"].list() }.ifEmpty { m["Ga"].list() }
            .mapNotNull { it?.toString() }.filter { it != title },
        type = m.firstString("type", "ei", "nv", "GR")?.replaceFirstChar(Char::uppercase),
    )
}

internal fun comixChapter(data: Any?, hid: String): MangaChapter {
    val c = data.map()
    val id = c.firstInt("id", "VT", "hk", "kM") ?: 0
    return MangaChapter(
        id = "$CX_PREFIX$hid:$id",
        number = c.firstString("number", "PD", "Z_", "Xn")?.removeSuffix(".0"),
        title = c.firstString("title", "q1", "nb", "f9")?.takeIf { it.isNotBlank() },
        language = c.firstString("language", "Ax", "aK", "FP") ?: "en",
        group = c.firstString("group")
            ?: c["qz"].map().ifEmpty { c["rk"].map() }.firstString("q1", "nb", "f9")
            ?: c["gm"].map().string("f9")
            ?: "Comix",
        pages = c.firstInt("pages", "QQ") ?: 1,
        externalUrl = null,
        publishAt = parseComixRelativeTime(c.firstString("relativeTime", "iS")),
    )
}

internal fun comixPageUrls(data: Any?): List<String> {
    val m = data.map()
    return m["pages"].list().ifEmpty { m["sN"].list() }.ifEmpty { m["iE"].list() }.ifEmpty { m["iT"].list() }
        .mapNotNull { page ->
            val item = page.map()
            val primary = item.firstString("url", "FM", "Gh", "H$") ?: return@mapNotNull null
            val alternatives = item["urls"].list().mapNotNull { it as? String }
            packMangaPageCandidates(listOf(primary) + alternatives)
        }
        .filter { mangaPageCandidates(it).firstOrNull()?.startsWith("https://") == true }
}

private fun parseComixRelativeTime(value: String?): Long {
    val match = Regex("(\\d+)\\s*(mos|mo|yr|yrs|y|w|d|h|m)").find(value ?: return 0L) ?: return 0L
    val count = match.groupValues[1].toLongOrNull() ?: return 0L
    val unit = match.groupValues[2]
    val ms = when (unit) {
        "m" -> 60_000L
        "h" -> 3_600_000L
        "d" -> 86_400_000L
        "w" -> 7 * 86_400_000L
        "mo", "mos" -> 30 * 86_400_000L
        "y", "yr", "yrs" -> 365 * 86_400_000L
        else -> return 0L
    }
    return System.currentTimeMillis() - count * ms
}

private fun comixParts(id: String): Pair<String, Int>? {
    val parts = id.removePrefix(CX_PREFIX).split(':', limit = 2)
    return if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return null) else null
}

/**
 * Comix (comix.ws) client. The site signs and decrypts its API responses in a rotated client module, so a WebView
 * runs that module on-device and returns the decrypted JSON to Kotlin. No server or API key is involved.
 */
class ComixWsSource(
    app: Application,
    private val mature: suspend () -> Boolean = { false },
    private val client: OkHttpClient,
) {
    private val runtime = ComixWebRuntime(app, client)
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(Any::class.java)

    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        page: Int = 1,
        type: MangaType? = null,
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
    ): MangaPage {
        val params = linkedMapOf<String, Any?>(
            "page" to page,
            "limit" to PAGE_SIZE,
            "content_rating" to ratings(),
        )
        when (order) {
            MangaOrder.LATEST -> params["order"] = mapOf("chapter_updated_at" to "desc")
            MangaOrder.POPULAR -> params["order"] = mapOf("follows_total" to "desc")
            // Relevance is the API default for keyword searches; an explicit order could be rejected.
            MangaOrder.RELEVANCE -> if (query.isBlank()) params["order"] = mapOf("follows_total" to "desc")
        }
        query.trim().takeIf { it.isNotEmpty() }?.let { params["keyword"] = it }
        type?.let { params["types"] = listOf(it.slug) }
        tagIds.mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }?.let { params["genres_in"] = it }
        status?.comixValue()?.let { params["statuses"] = listOf(it) }
        val result = runtime.call("browse", params).map()
        val items = result["items"].list().ifEmpty { result["s3"].list() }.ifEmpty { result["I7"].list() }.ifEmpty { result["EY"].list() }
            .map(::comixManga).filter { it.id != CX_PREFIX && it.title.isNotBlank() }
        val meta = result["meta"].map().ifEmpty { result["om"].map() }.ifEmpty { result["aY"].map() }.ifEmpty { result["\$U"].map() }
        val total = meta.firstInt("total", "Yl", "T8", "fc") ?: items.size
        return MangaPage(items, total, (page - 1) * PAGE_SIZE)
    }

    suspend fun search(query: String): List<Manga> = browse(MangaOrder.RELEVANCE, query).items

    suspend fun detail(id: String): Manga {
        val hid = id.removePrefix(CX_PREFIX).substringBefore(':')
        return comixManga(runtime.call("detail", hid))
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        val hid = id.removePrefix(CX_PREFIX).substringBefore(':')
        val all = ArrayList<MangaChapter>()
        var page = 1
        var lastPage = 1
        do {
            val result = runtime.call(
                "chapters",
                mapOf(
                    "hid" to hid,
                    "options" to mapOf("limit" to CHAPTER_LIMIT, "page" to page, "order" to mapOf("number" to "desc")),
                ),
            ).map()
            val items = result["items"].list().ifEmpty { result["s3"].list() }.ifEmpty { result["I7"].list() }.ifEmpty { result["EY"].list() }
            all += items.map { comixChapter(it, hid) }.filter { it.language == "en" || it.language.isBlank() }
            val meta = result["meta"].map().ifEmpty { result["om"].map() }.ifEmpty { result["aY"].map() }.ifEmpty { result["\$U"].map() }
            val total = meta.firstInt("total", "Yl", "T8", "fc") ?: items.size
            val perPage = meta.firstInt("limit", "wt", "f6") ?: CHAPTER_LIMIT
            val pages = meta.firstInt("pages", "zq") ?: if (perPage > 0) (total + perPage - 1) / perPage else 1
            lastPage = pages.coerceAtMost(MAX_CHAPTER_PAGES)
            page++
        } while (page <= lastPage)
        return pickChapters(all, "en")
    }

    suspend fun pages(chapterId: String): List<String> {
        val (_, id) = comixParts(chapterId) ?: return emptyList()
        return comixPageUrls(runtime.call("reader", id))
    }

    /** Newest chapter of recently updated Comix manga, for notifications and the home summary. */
    suspend fun latestOngoingChapters(limit: Int): List<MangaRelease> {
        val manga = ArrayList<Manga>()
        var page = 1
        while (manga.size < limit && page <= MAX_RELEASE_PAGES) {
            val items = browse(MangaOrder.LATEST, page = page).items
            if (items.isEmpty()) break
            manga += items
            page++
        }
        return coroutineScope {
            manga.take(limit).map { m ->
                async {
                    runCatching {
                        val hid = m.id.removePrefix(CX_PREFIX).substringBefore(':')
                        val detail = runtime.call("detail", hid).map()
                        val latestUrl = detail.firstString("HD", "latestChapterUrl") ?: return@runCatching null
                        val number = detail.firstInt("hb", "latestChapter")
                        val chapterId = Regex("/title/[^/]+/(\\d+)-chapter-").find(latestUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: return@runCatching null
                        val publishAt = parseComixRelativeTime(detail.firstString("relativeTime", "sO", "chapterUpdatedAtFormatted"))
                        MangaRelease(m.id, m.title, "$CX_PREFIX$hid:$chapterId", number?.toString(), publishAt)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = try {
                search(name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        return null
    }

    /** Genre ids and names from Comix's server-rendered browse options; stable across client-module deploys. */
    suspend fun tags(): List<MangaTag> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$COMIX_ORIGIN/browse").header("User-Agent", COMIX_USER_AGENT).build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string().orEmpty()
        }
        val raw = INITIAL_DATA.find(html)?.groupValues?.getOrNull(1) ?: return@withContext emptyList()
        val root = jsonAdapter.fromJson(raw) as? Map<*, *> ?: return@withContext emptyList()
        val list = root["list"] as? Map<*, *> ?: return@withContext emptyList()
        val options = list["options"] as? Map<*, *> ?: return@withContext emptyList()
        (options["genres"] as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"]?.toString() ?: return@mapNotNull null
            val label = map["label"] as? String ?: return@mapNotNull null
            MangaTag(id, label, "genre")
        }
    }

    private suspend fun ratings(): List<String> =
        if (mature()) listOf("safe", "suggestive", "erotica") else listOf("safe", "suggestive")

    private companion object {
        const val PAGE_SIZE = 28
        const val CHAPTER_LIMIT = 100
        const val MAX_CHAPTER_PAGES = 50
        const val MAX_RELEASE_PAGES = 4
        val INITIAL_DATA = Regex("<script type=\"application/json\" id=\"initial-data\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    }
}

private class ComixWebRuntime(private val app: Application, private val http: OkHttpClient) {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(Any::class.java)
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, Continuation<Any?>>()
    private var webView: WebView? = null
    private var pageReady = false
    private var pageContinuation: Continuation<Unit>? = null

    suspend fun call(operation: String, argument: Any?): Any? = mutex.withLock {
        try {
            withTimeout(REQUEST_TIMEOUT_MS) { invoke(operation, argument) }
        } catch (e: TimeoutCancellationException) {
            // A caller's own timeout (e.g. a bounded search) is cancellation, not a Comix failure.
            if (!currentCoroutineContext().isActive) throw e
            throw IOException("Comix request timed out", e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun invoke(operation: String, argument: Any?): Any? = withContext(Dispatchers.Main.immediate) {
        ensurePage()
        suspendCancellableCoroutine { continuation ->
            val token = UUID.randomUUID().toString()
            pending[token] = continuation
            continuation.invokeOnCancellation { pending.remove(token) }
            val view = webView ?: throw IOException("Comix WebView unavailable")
            view.evaluateJavascript(script(token, operation, argument), null)
        }
    }

    /** Loads the site once; later calls reuse the page and the already-imported API module. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensurePage() {
        if (pageReady) return
        // WebView uses Android's system DNS and can receive an ISP block page. Fetch the shell with the same
        // OkHttp client as the rest of the app, then keep the comix.ws origin while WebView executes it.
        val html = withContext(Dispatchers.IO) { fetchHtml() }
        suspendCancellableCoroutine<Unit> { continuation ->
            val view = webView ?: WebView(app).also { created ->
                created.settings.javaScriptEnabled = true
                created.settings.domStorageEnabled = true
                created.settings.userAgentString = COMIX_USER_AGENT
                created.addJavascriptInterface(Bridge(), BRIDGE_NAME)
                webView = created
            }
            pageContinuation = continuation
            continuation.invokeOnCancellation { if (pageContinuation === continuation) pageContinuation = null }
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val scheme = request.url.scheme
                    if (scheme != "http" && scheme != "https") return null
                    return runCatching { proxy(request) }.getOrElse { errorResponse(it) }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (pageContinuation == null) return
                    pageReady = true
                    pageContinuation?.resume(Unit)
                    pageContinuation = null
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!request.isForMainFrame) return
                    pageReady = false
                    val failure = IOException("Comix WebView ${error.errorCode}: ${error.description}")
                    pageContinuation?.resumeWithException(failure)
                    pageContinuation = null
                }
            }
            view.loadDataWithBaseURL("$COMIX_ORIGIN/browse", html, "text/html", "UTF-8", null)
        }
    }

    private fun fetchHtml(): String {
        val request = Request.Builder().url("$COMIX_ORIGIN/browse").header("User-Agent", COMIX_USER_AGENT).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Comix page HTTP ${response.code}")
            response.body?.string()?.takeIf { "id=\"initial-data\"" in it }
                ?: throw IOException("Comix page response is invalid")
        }
    }

    /** Proxies WebView resources and Comix API GETs through OkHttp so [MangaDns] and TLS behavior are shared. */
    private fun proxy(webRequest: WebResourceRequest): WebResourceResponse? {
        val method = webRequest.method.uppercase()
        if (method != "GET" && method != "HEAD") return null
        val url = webRequest.url.toString()
        val builder = Request.Builder().url(url).header("User-Agent", COMIX_USER_AGENT)
        webRequest.requestHeaders.forEach { (name, value) ->
            if (
                !name.equals("Host", true) &&
                !name.equals("Connection", true) &&
                !name.equals("Accept-Encoding", true) &&
                !name.equals("If-None-Match", true) &&
                !name.equals("If-Modified-Since", true)
            ) {
                builder.header(name, value)
            }
        }
        if (method == "HEAD") builder.head() else builder.get()
        val response = http.newCall(builder.build()).execute()
        val body = response.body
        val contentType = body?.contentType()
        val mime = contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream"
        val encoding = contentType?.charset()?.name()
        val responseHeaders = response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") }
            .filterKeys { !it.equals("Content-Encoding", true) && !it.equals("Content-Length", true) }
        val status = response.code.takeUnless { it in 300..399 } ?: 502
        val reason = response.message.ifBlank { if (status < 400) "OK" else "Upstream error" }
        return WebResourceResponse(
            mime,
            encoding,
            status,
            reason,
            responseHeaders,
            body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun errorResponse(error: Throwable): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        502,
        "Comix proxy error",
        emptyMap(),
        ByteArrayInputStream((error.message ?: "Comix request failed").toByteArray()),
    )

    private fun script(token: String, operation: String, argument: Any?): String {
        val arg = jsonAdapter.toJson(argument)
        return """
            (async () => {
              const token = ${jsString(token)};
              try {
                let runtime = window.__fuwaComixApi;
                if (!runtime) {
                  const main = [...document.scripts].map(s => s.src).find(s => /\/main-[^/]+\.js(?:\?|$)/.test(s));
                  if (!main) throw new Error('Comix main module not found');
                  const mainSource = await (await fetch(main)).text();
                  const apiFile = mainSource.match(/apicore-[A-Za-z0-9_-]+\.js/)?.[0];
                  if (!apiFile) throw new Error('Comix API module not found');
                  const apiUrl = new URL(apiFile, main).href;
                  const [apiSource, api] = await Promise.all([
                    fetch(apiUrl).then(r => r.text()),
                    import(apiUrl),
                  ]);

                  // Comix rotates every minified method and response key on deployment. Resolve the read methods
                  // from the site's own call sites instead of pinning names such as QJ/Tm/OJ.
                  const ID = '[A-Za-z_$][\\w$]*';
                  const rx = s => s.replace(/\$/g, '\\$');
                  const browseUse = mainSource.match(new RegExp(
                    '"chapter_updated_at":"desc"[\\s\\S]{0,3000}?"queryFn":\\(\\)=>\\s*(' + ID + ')\\.(' + ID + ')\\('
                  ));
                  if (!browseUse) throw new Error('Comix browse method not found');
                  const alias = browseUse[1];
                  const browseMethod = browseUse[2];
                  const detailUse = mainSource.match(new RegExp(
                    '"queryFn":\\(\\)=>' + rx(alias) + '\\.(' + ID + ')\\((' + ID + ')\\),"enabled":!!\\2,"placeholderData"'
                  ));
                  if (!detailUse) throw new Error('Comix detail method not found');
                  const detailMethod = detailUse[1];
                  const chaptersAt = mainSource.indexOf('"mpage__chapters"');
                  const beforeChapters = mainSource.slice(Math.max(0, chaptersAt - 6000), chaptersAt);
                  const chapterUses = [...beforeChapters.matchAll(new RegExp(
                    '\\b' + rx(alias) + '\\.(' + ID + ')\\(([^)]*)\\)', 'g'
                  ))];
                  const chaptersMethod = chapterUses.at(-1)?.[1];
                  if (!chaptersMethod) throw new Error('Comix chapters method not found');

                  const imports = [...mainSource.matchAll(/import\{([^}]*)\}from["']([^"']*apicore-[^"']+\.js)["']/g)];
                  const importBlock = imports.find(m => m[2].endsWith(apiFile))?.[1] ?? '';
                  const exportUse = importBlock.match(new RegExp(
                    '(?:^|,)\\s*(' + ID + ')\\s+as\\s+' + rx(alias) + '\\s*(?:,|$)'
                  ));
                  const catalogue = api[exportUse?.[1] ?? 'f'];

                  // The reader is exported as `n`; discover its first (chapter-detail) method from the module source.
                  const readerLocal = apiSource.slice(-4000).match(new RegExp('(' + ID + ')\\s+as\\s+n(?:,|})'))?.[1];
                  const readerAssign = readerLocal && apiSource.match(new RegExp(
                    rx(readerLocal) + '=(' + ID + ')\\((' + ID + ')\\)'
                  ));
                  const wrapper = readerAssign?.[1];
                  const readerObject = readerAssign?.[2];
                  const readerBody = readerObject && apiSource.match(new RegExp(
                    '(?:var|let|const)?\\s*' + rx(readerObject) + '=(\\{[\\s\\S]*?\\}),' +
                    rx(readerLocal) + '=' + rx(wrapper) + '\\(' + rx(readerObject) + '\\)'
                  ))?.[1];
                  const readerMethod = readerBody?.match(new RegExp(
                    '^\\{(?:async)?["\']?(' + ID + ')["\']?\\s*(?::|\\()'
                  ))?.[1];
                  const reader = api.n;
                  const methods = { browse: browseMethod, detail: detailMethod, chapters: chaptersMethod, reader: readerMethod };
                  for (const [name, owner] of [['browse', catalogue], ['detail', catalogue], ['chapters', catalogue], ['reader', reader]]) {
                    if (typeof owner?.[methods[name]] !== 'function') throw new Error('Comix ' + name + ' method is invalid');
                  }
                  runtime = { catalogue, reader, methods };
                  window.__fuwaComixApi = runtime;
                }

                // Normalize rotating output keys into stable names before crossing the Android bridge.
                const isObject = x => x && typeof x === 'object' && !Array.isArray(x);
                const isRelative = x => typeof x === 'string' && /^\d+\s*(?:m|h|d|w|mo|mos|y|yr|yrs)\s+ago$/i.test(x);
                const label = o => isObject(o)
                  ? Object.values(o).find(v => typeof v === 'string' && v.trim() && !v.startsWith('/') && !v.startsWith('http'))
                  : null;
                const normalizeManga = x => {
                  const entries = Object.entries(isObject(x) ? x : {});
                  const strings = entries.filter(([, v]) => typeof v === 'string');
                  const paths = strings.map(([, v]) => v).filter(v => v.startsWith('/title/'));
                  const mangaUrl = paths.find(v => !/(?:\/|^)\d+-chapter-/.test(v)) ?? paths[0] ?? '';
                  const hidPair = strings.find(([, v]) => mangaUrl.includes('/title/' + v + '-') || mangaUrl.includes('/title/' + v + '/'));
                  const hid = hidPair?.[1] ?? '';
                  const hidIndex = hidPair ? entries.findIndex(([key]) => key === hidPair[0]) : -1;
                  const reserved = new Set([
                    'manga', 'manhwa', 'manhua', 'other', 'releasing', 'finished', 'on_hiatus',
                    'discontinued', 'not_yet_released', 'safe', 'suggestive', 'erotica', 'pornographic'
                  ]);
                  const title = (hidIndex >= 0 && typeof entries[hidIndex + 1]?.[1] === 'string' ? entries[hidIndex + 1][1] : null)
                    ?? strings.map(([, v]) => v).find(v => v && !reserved.has(v) && !v.startsWith('/') && !isRelative(v) && !/^\d{4}$/.test(v))
                    ?? '';
                  const type = strings.map(([, v]) => v).find(v => ['manga', 'manhwa', 'manhua', 'other'].includes(v)) ?? null;
                  const status = strings.map(([, v]) => v).find(v => ['releasing', 'finished', 'on_hiatus', 'discontinued', 'not_yet_released'].includes(v)) ?? null;
                  const covers = Object.values(x ?? {}).filter(isObject)
                    .flatMap(o => Object.values(o).filter(v => typeof v === 'string' && v.includes('/images/covers/')));
                  const medium = covers.find(v => v.includes('@280')) ?? covers[0] ?? null;
                  const large = covers.find(v => !v.includes('@280')) ?? covers.at(-1) ?? null;
                  const excluded = v => !v || v === hid || v === title || reserved.has(v) || v.startsWith('/') ||
                    v.startsWith('http') || isRelative(v) || /^\d{4}$/.test(v) || v === '?';
                  const synopsis = strings.map(([, v]) => v).filter(v => !excluded(v) && v.length > 20)
                    .sort((a, b) => b.length - a.length)[0] ?? '';
                  const yearText = strings.map(([, v]) => v).find(v => /^\d{4}$/.test(v) && +v >= 1800 && +v <= 2100);
                  const year = yearText ? +yearText : (Object.values(x ?? {}).find(v => typeof v === 'number' && v >= 1800 && v <= 2100) ?? null);
                  const arrays = Object.values(x ?? {}).filter(Array.isArray);
                  const altTitles = arrays.find(a => a.length && a.every(v => typeof v === 'string')) ?? [];
                  const labelArrays = arrays.filter(a => a.length && a.every(isObject) && a.some(o => label(o)));
                  const tags = labelArrays.slice(0, 4).flatMap(a => a.map(label).filter(Boolean));
                  const authors = labelArrays.slice(4, 6).flatMap(a => a.map(label).filter(Boolean));
                  const chapterInfo = paths.filter(v => /\/\d+-chapter-/.test(v)).map(url => {
                    const match = url.match(/\/(\d+)-chapter-([^/?]+)/);
                    return { url, id: +(match?.[1] ?? 0), number: match?.[2] ?? '' };
                  }).filter(v => v.id).sort((a, b) => (parseFloat(a.number) || 0) - (parseFloat(b.number) || 0));
                  const first = chapterInfo[0];
                  const latest = chapterInfo.at(-1);
                  return {
                    hid, title, type, status, poster: { medium, large }, synopsis, year, altTitles, tags, authors,
                    url: mangaUrl, latestChapter: latest?.number ?? null, latestChapterUrl: latest?.url ?? null,
                    firstChapterUrl: first?.url ?? null, relativeTime: strings.map(([, v]) => v).find(isRelative) ?? null,
                  };
                };
                const normalizeChapter = x => {
                  const entries = Object.entries(isObject(x) ? x : {});
                  const strings = entries.filter(([, v]) => typeof v === 'string');
                  const url = strings.map(([, v]) => v).find(v => /\/title\/.*\/\d+-chapter-/.test(v)) ?? '';
                  const match = url.match(/\/(\d+)-chapter-([^/?]+)/);
                  const languagePair = strings.find(([, v]) => /^(?:en|id|ja|ko|zh|zh-hk)$/.test(v));
                  const languageIndex = languagePair ? entries.findIndex(([key]) => key === languagePair[0]) : -1;
                  const title = languageIndex > 0 && typeof entries[languageIndex - 1][1] === 'string'
                    ? (entries[languageIndex - 1][1] || null) : null;
                  const group = Object.values(x ?? {}).filter(isObject).map(label).find(Boolean) ?? null;
                  return {
                    id: +(match?.[1] ?? 0), number: match?.[2] ?? null, title,
                    language: languagePair?.[1] ?? 'en', group, pages: 1, url,
                    relativeTime: strings.map(([, v]) => v).find(isRelative) ?? null,
                  };
                };
                const normalizeMeta = (x, options = {}) => {
                  const object = Object.values(x ?? {}).find(v => isObject(v) && Object.values(v).filter(n => typeof n === 'number').length >= 2) ?? {};
                  // Pagination mapper order is stable even though every property name rotates: total, limit,
                  // current page, last page, first item, last item, has next, has previous.
                  const numbers = Object.values(object).filter(n => typeof n === 'number' && Number.isFinite(n));
                  return {
                    total: numbers[0] ?? 0,
                    limit: numbers[1] ?? options.limit ?? 0,
                    page: numbers[2] ?? options.page ?? 1,
                    pages: numbers[3] ?? 1,
                  };
                };
                const normalizeList = (x, options, mapper) => ({
                  items: (Object.values(x ?? {}).find(Array.isArray) ?? []).map(mapper),
                  meta: normalizeMeta(x, options),
                });
                const normalizeReader = x => {
                  const arrays = Object.values(x ?? {}).filter(Array.isArray);
                  const pages = arrays.find(a => a.length && a.every(isObject) &&
                    a.some(o => Object.values(o).some(v => typeof v === 'string' && v.startsWith('https://')))) ?? [];
                  return {
                    // Comix's reader tries several rotating image hosts. Preserve the same candidates so one
                    // ISP-blocked/dead host cannot blank the entire chapter in the Android reader.
                    pages: pages.map((o, pageIndex) => {
                      const values = Object.values(o);
                      const url = values.find(v => typeof v === 'string' && v.startsWith('https://'));
                      if (!url) return null;
                      const parsed = /^(https?:\/\/)([^/?#]+)(.*)$/.exec(url);
                      if (!parsed) return { url, urls: [url] };
                      const hostParts = parsed[2].split('.');
                      const subdomain = hostParts.shift();
                      const root = hostParts.join('.');
                      const domains = values.find(v => Array.isArray(v) && v.length &&
                        v.every(domain => typeof domain === 'string' && domain.includes('.'))) ?? [];
                      const strings = values.filter(v => typeof v === 'string' && !v.startsWith('http'));
                      const fallbackRoot = strings.find(v => v.includes('.'));
                      const fallbackSubdomain = strings.find(v => !v.includes('.') && !v.includes('/'));
                      // The website load-balances by choosing one fallback root per page. The app keeps that
                      // preferred root first, then every remaining root from the payload for hostile ISP networks.
                      const preferredDomain = domains.length ? domains[pageIndex % domains.length] : null;
                      const orderedDomains = preferredDomain
                        ? [preferredDomain, ...domains.filter(domain => domain !== preferredDomain)]
                        : domains;
                      const roots = [root, ...orderedDomains, fallbackRoot];
                      const subdomains = [subdomain, fallbackSubdomain].filter(Boolean);
                      const urls = [];
                      for (const sub of subdomains) for (const domain of roots) {
                        if (!domain) continue;
                        const candidate = parsed[1] + sub + '.' + domain + parsed[3];
                        if (!urls.includes(candidate)) urls.push(candidate);
                      }
                      return { url, urls };
                    }).filter(Boolean),
                  };
                };

                const op = ${jsString(operation)};
                const arg = $arg;
                let result;
                if (op === 'browse') {
                  result = normalizeList(await runtime.catalogue[runtime.methods.browse](arg), arg, normalizeManga);
                } else if (op === 'detail') {
                  result = normalizeManga(await runtime.catalogue[runtime.methods.detail](arg));
                } else if (op === 'chapters') {
                  result = normalizeList(
                    await runtime.catalogue[runtime.methods.chapters](arg.hid, arg.options), arg.options, normalizeChapter
                  );
                } else if (op === 'reader') {
                  result = normalizeReader(await runtime.reader[runtime.methods.reader](arg));
                } else {
                  throw new Error('Unknown Comix operation');
                }
                window.$BRIDGE_NAME.success(token, JSON.stringify(result));
              } catch (error) {
                window.$BRIDGE_NAME.failure(token, String(error && (error.stack || error.message) || error));
              }
            })();
        """.trimIndent()
    }

    private fun jsString(value: String): String = jsonAdapter.toJson(value)

    private fun fail(token: String, error: Throwable) {
        pending.remove(token)?.resumeWithException(error)
    }

    @Keep
    private inner class Bridge {
        @JavascriptInterface
        fun success(token: String, json: String) = handler.post {
            val continuation = pending.remove(token) ?: return@post
            runCatching { jsonAdapter.fromJson(json) }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }

        @JavascriptInterface
        fun failure(token: String, message: String) = handler.post {
            // A deployment can rotate the module while this process is alive. Force the next retry to bootstrap again.
            pageReady = false
            webView?.evaluateJavascript("delete window.__fuwaComixApi", null)
            fail(token, IOException(message.take(1_000)))
        }
    }

    private companion object {
        const val BRIDGE_NAME = "FuwaComix"
        const val REQUEST_TIMEOUT_MS = 60_000L
    }
}
