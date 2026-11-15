package com.fuwanime.data.manga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

// ============== IsManga workflow (adapted) ==============
// Translates manga page images to Indonesian via IsManga GraphQL API (server-side),
// then composites translated text and saves resulting PNGs locally for the reader.

private const val ISMANGA_ENDPOINT = "https://api.ismanga.com/graphql"
private const val FIREBASE_API_KEY = "AIzaSyApW93EPxiSW6FwUVFc42GT-Q8ILs9wtkk"

private data class IsAccount(
    var email: String = "",
    var password: String = "",
    var idToken: String = "",
    var refreshToken: String = "",
    var localId: String = "",
    var expiresIn: String = "3600",
    var createdAt: String = "",
    var updatedAt: String? = null,
)

private data class IsLangText(val script: String?)
private data class IsBoxColors(val text: String?, val background: String?)
private data class IsBox(
    val x: Double, val y: Double, val width: Double, val height: Double,
    val toLang: IsLangText?, val imgWidth: Int, val imgHeight: Int,
    val inpaintingUrl: String?, val colors: IsBoxColors?,
)
private data class IsTaskPayload(
    val taskId: String, val url: String?, val isPseudoUrl: Boolean,
    val isHighQuality: Boolean, val langCode: String?, val shouldCache: Boolean,
)
private data class IsResult(val code: Int, val payload: List<IsBox>, val taskPayload: IsTaskPayload?)
private class IsApiException(message: String, val code: Int? = null) : Exception(message)

private object IsCodes {
    const val SUCCESS = 1
    const val STILL_IN_QUEUE = 12
    const val PROCESSING = 13
    const val NO_SUCH_TASK = 15
    const val ENQUEUED = 16
    const val DUPLICATE = 17
    const val QUOTA_EXCEEDED = 10003
    val PENDING = setOf(STILL_IN_QUEUE, PROCESSING, ENQUEUED, DUPLICATE)
    fun message(code: Int): String = when (code) {
        3 -> "Gambar bukan manga / tidak valid"
        9 -> "File terlalu besar (maks 5MB)"
        10 -> "Dimensi gambar terlalu kecil"
        14 -> "Antrian GPU gagal memproses"
        15 -> "Task tidak ditemukan"
        18 -> "Antrian penuh"
        19 -> "Antrian kehabisan memori"
        20 -> "GPU internal error"
        21 -> "Dimensi gambar terlalu besar"
        10002 -> "Kena throttle"
        10003 -> "Kuota habis"
        10004 -> "Terjemahan diblokir"
        else -> "Error kode $code"
    }
}

private fun JSONObject.toIsBox(): IsBox? = try {
    val to = optJSONObject("toLang")
    val colors = optJSONObject("colors")
    IsBox(
        x = optDouble("x", 0.0), y = optDouble("y", 0.0),
        width = optDouble("width", 0.0), height = optDouble("height", 0.0),
        toLang = to?.let { IsLangText(it.optString("script", null)) },
        imgWidth = optInt("imgWidth", 0), imgHeight = optInt("imgHeight", 0),
        inpaintingUrl = optString("inpaintingUrl", null),
        colors = colors?.let { IsBoxColors(it.optString("text", null), it.optString("background", null)) },
    )
} catch (_: Exception) { null }

private fun JSONArray.toIsBoxes(): List<IsBox> = buildList {
    for (i in 0 until length()) { optJSONObject(i)?.toIsBox()?.let { add(it) } }
}

private fun JSONObject.toTaskPayload(): IsTaskPayload? {
    val id = optString("taskId", null) ?: return null
    return IsTaskPayload(id, optString("url", null), optBoolean("isPseudoUrl", false),
        optBoolean("isHighQuality", false), optString("langCode", null), optBoolean("shouldCache", false))
}

private fun parseIsResult(data: JSONObject?): IsResult {
    if (data == null) throw IsApiException("Response kosong")
    val code = data.optInt("code", -1)
    val payloadAny = data.opt("payload")
    val payload = when (payloadAny) {
        is JSONArray -> payloadAny.toIsBoxes()
        is JSONObject -> JSONArray().put(payloadAny).toIsBoxes()
        else -> emptyList()
    }
    return IsResult(code, payload, data.optJSONObject("taskPayload")?.toTaskPayload())
}

private object IsHttp {
    fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap(), timeout: Int = 60_000): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = timeout; readTimeout = timeout; doOutput = true; doInput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            try { JSONObject(text) } catch (_: Exception) { null }
        } finally { conn.disconnect() }
    }
    fun postForm(url: String, form: String, timeout: Int = 60_000): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = timeout; readTimeout = timeout; doOutput = true; doInput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            try { JSONObject(text) } catch (_: Exception) { null }
        } finally { conn.disconnect() }
    }
    fun getBytes(url: String, timeout: Int = 60_000): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = timeout; readTimeout = timeout
        }
        return try {
            val status = conn.responseCode
            if (status in 300..399) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) return getBytes(URL(URL(url), loc).toString(), timeout)
            }
            val stream = if (status in 200..299) conn.inputStream else throw IOException("HTTP $status: $url")
            stream.use { it.readBytes() }
        } finally { conn.disconnect() }
    }
}

private object IsImage {
    fun detectMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"
        bytes.size >= 6 && String(bytes, 0, 6).let { it == "GIF87a" || it == "GIF89a" } -> "image/gif"
        else -> "image/jpeg"
    }
    fun toDataUrl(bytes: ByteArray): String = "data:${detectMime(bytes)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun dataUrlId(dataUrl: String): String = buildString { var i = 0; while (i < dataUrl.length) { append(dataUrl[i]); i += 10000 } }
    fun decodeJwtExp(token: String): Long = try {
        val parts = token.split('.')
        if (parts.size < 2) 0L else JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))).optLong("exp", 0L)
    } catch (_: Exception) { 0L }
    fun parseColor(color: String?, fallback: Int): Int {
        if (color.isNullOrBlank()) return fallback
        return try {
            when {
                color.startsWith("#") || !color.startsWith("rgb", true) -> Color.parseColor(color)
                else -> {
                    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(color).map { it.value.toDouble() }.toList()
                    if (nums.size < 3) fallback else Color.argb(
                        if (nums.size >= 4) (nums[3] * 255).toInt().coerceIn(0, 255) else 255,
                        nums[0].toInt().coerceIn(0, 255), nums[1].toInt().coerceIn(0, 255), nums[2].toInt().coerceIn(0, 255)
                    )
                }
            }
        } catch (_: Exception) { fallback }
    }
}

private class IsTextRenderer {
    fun draw(base: Bitmap, boxes: List<IsBox>): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        for (box in boxes) {
            val text = box.toLang?.script?.trim().orEmpty()
            if (text.isEmpty()) continue
            val x = box.x.toFloat(); val y = box.y.toFloat()
            val w = max(1f, box.width.toFloat()); val h = max(1f, box.height.toFloat())
            val textColor = IsImage.parseColor(box.colors?.text, Color.BLACK)
            val outlineColor = IsImage.parseColor(box.colors?.background, Color.WHITE)
            val pad = max(2f, min(w, h) * 0.07f)
            val innerW = max(1f, w - pad * 2); val innerH = max(1f, h - pad * 2)
            val (paint, layout) = fit(text, innerW, innerH, textColor)
            val dx = x + pad; val dy = y + (h - layout.height) / 2f
            canvas.save(); canvas.translate(dx, dy)
            val outline = TextPaint(paint).apply {
                style = Paint.Style.STROKE; strokeWidth = max(1f, paint.textSize * 0.18f)
                color = outlineColor; strokeJoin = Paint.Join.ROUND
            }
            buildLayout(text, outline, layout.width.toFloat()).draw(canvas)
            layout.draw(canvas); canvas.restore()
        }
        return result
    }
    private fun fit(text: String, maxW: Float, maxH: Float, color: Int): Pair<TextPaint, StaticLayout> {
        var size = min(maxH, 120f); val minSize = 10f
        var cp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = minSize; isFakeBoldText = true }
        var cl = buildLayout(text, cp, maxW)
        while (size >= minSize) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; isFakeBoldText = true }
            val l = buildLayout(text, p, maxW); cp = p; cl = l
            if (l.height <= maxH) break; size -= 1f
        }
        return cp to cl
    }
    private fun buildLayout(text: String, paint: TextPaint, maxW: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
}

private class IsAccountStore(private val file: File) {
    private val accounts = mutableListOf<IsAccount>()
    @Synchronized fun load(): MutableList<IsAccount> {
        accounts.clear()
        if (!file.exists()) return accounts
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                accounts.add(IsAccount(
                    email = o.optString("email"), password = o.optString("password"),
                    idToken = o.optString("idToken"), refreshToken = o.optString("refreshToken"),
                    localId = o.optString("localId"), expiresIn = o.optString("expiresIn", "3600"),
                    createdAt = o.optString("createdAt"),
                    updatedAt = if (o.has("updatedAt")) o.optString("updatedAt") else null,
                ))
            }
        }
        return accounts
    }
    @Synchronized fun save() {
        val arr = JSONArray()
        accounts.forEach { a -> arr.put(JSONObject().apply {
            put("email", a.email); put("password", a.password); put("idToken", a.idToken)
            put("refreshToken", a.refreshToken); put("localId", a.localId); put("expiresIn", a.expiresIn)
            put("provider", "firebase"); put("createdAt", a.createdAt)
            if (a.updatedAt != null) put("updatedAt", a.updatedAt)
        }) }
        file.parentFile?.mkdirs(); file.writeText(arr.toString(2))
    }
    @Synchronized fun all(): MutableList<IsAccount> = accounts
    @Synchronized fun add(a: IsAccount) { accounts.add(a) }
}

private object IsAuth {
    private const val SIGNUP = "https://www.googleapis.com/identitytoolkit/v3/relyingparty/signupNewUser?key=$FIREBASE_API_KEY"
    private const val REFRESH = "https://securetoken.googleapis.com/v1/token?key=$FIREBASE_API_KEY"
    private val rnd = SecureRandom()
    fun createAccount(): IsAccount {
        val bytes = ByteArray(8).also { rnd.nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val domains = listOf("mailinator.com", "tempmail.com", "guerrillamail.com")
        val email = "ismanga_${hex}@${domains[rnd.nextInt(domains.size)]}"
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val pass = buildString { repeat(12) { append(chars[rnd.nextInt(chars.length)]) }; append("!A1") }
        val body = JSONObject().apply { put("email", email); put("password", pass); put("returnSecureToken", true) }
        val json = IsHttp.postJson(SIGNUP, body) ?: throw IsApiException("Signup response kosong")
        if (json.has("error") || !json.has("idToken")) throw IsApiException("Signup gagal: ${json.optJSONObject("error")?.optString("message") ?: json}")
        return IsAccount(email, pass, json.optString("idToken"), json.optString("refreshToken"), json.optString("localId"), json.optString("expiresIn", "3600"), Instant.now().toString())
    }
    fun refresh(a: IsAccount): IsAccount {
        val form = "grant_type=refresh_token&refresh_token=${java.net.URLEncoder.encode(a.refreshToken, "UTF-8")}"
        val json = IsHttp.postForm(REFRESH, form) ?: throw IsApiException("Refresh response kosong")
        if (json.has("error") || !json.has("id_token")) throw IsApiException("Refresh gagal: ${json.optJSONObject("error")?.optString("message") ?: json}")
        a.idToken = json.optString("id_token"); a.refreshToken = json.optString("refresh_token", a.refreshToken)
        a.localId = json.optString("user_id", a.localId); a.expiresIn = json.optString("expires_in", a.expiresIn)
        a.updatedAt = Instant.now().toString(); return a
    }
}

private class IsMangaApi(private val store: IsAccountStore) {
    private var account: IsAccount? = null
    private var visitId = UUID.randomUUID().toString()
    private var deviceId = UUID.randomUUID().toString()

    @Synchronized private fun ensureAccount(): IsAccount {
        account?.let { return it }
        val accounts = store.load()
        if (accounts.isEmpty()) {
            val created = IsAuth.createAccount(); store.add(created); store.save(); account = created; return created
        }
        for (i in accounts.indices.reversed()) {
            val a = accounts[i]
            val exp = IsImage.decodeJwtExp(a.idToken)
            if (exp > 0 && exp - 300 > Instant.now().epochSecond) { account = a; return a }
            try { IsAuth.refresh(a); store.save(); account = a; return a } catch (_: Exception) {}
        }
        val created = IsAuth.createAccount(); store.add(created); store.save(); account = created; return created
    }

    private fun headers(): Map<String, String> = mapOf("Authorization" to (account?.idToken.orEmpty()), "ismanga-visit-id" to visitId, "ismanga-device-id" to deviceId)

    private fun isAuthExpired(res: JSONObject?): Boolean {
        val errs = res?.optJSONArray("errors") ?: return false
        for (i in 0 until errs.length()) {
            val msg = (errs.optJSONObject(i)?.optString("message") ?: "") + (errs.optJSONObject(i)?.optJSONObject("extensions")?.optString("code") ?: "")
            if ("id-token-expired" in msg || "ID token has expired" in msg) return true
        }
        return false
    }

    @Synchronized fun newSession() { visitId = UUID.randomUUID().toString(); deviceId = UUID.randomUUID().toString() }

    fun graphql(query: String, variables: JSONObject): JSONObject {
        ensureAccount()
        val body = JSONObject().apply { put("query", query); put("variables", variables) }
        var res = IsHttp.postJson(ISMANGA_ENDPOINT, body, headers())
        if (isAuthExpired(res)) {
            val a = ensureAccount(); IsAuth.refresh(a); store.save(); res = IsHttp.postJson(ISMANGA_ENDPOINT, body, headers())
        }
        return res ?: JSONObject()
    }

    @Synchronized fun signupNew() {
        val a = IsAuth.createAccount(); store.add(a); store.save(); account = a
    }
}

class IsMangaChapterTranslator(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    private val store = IsAccountStore(File(context.filesDir, "ismanga_accounts.json"))
    private val api = IsMangaApi(store)
    private val renderer = IsTextRenderer()

    /** Translate all pages of a chapter and return local file:// URIs for the translated PNGs (cached). */
    suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> = withContext(Dispatchers.IO) {
        val outDir = File(cacheDir, "ismanga/${chapterId.replace(':', '_').replace('/', '_')}")
        // If fully cached, return immediately.
        val cached = (pageUrls.indices).map { File(outDir, "p_${it.toString().padStart(4, '0')}.png") }
        if (cached.isNotEmpty() && cached.all { it.exists() && it.length() > 0 }) {
            onProgress(cached.size, cached.size)
            return@withContext cached.map { "file://${it.absolutePath}" }
        }
        outDir.mkdirs()
        val results = ArrayList<String>(pageUrls.size)
        for ((i, url) in pageUrls.withIndex()) {
            val outFile = File(outDir, "p_${i.toString().padStart(4, '0')}.png")
            if (outFile.exists() && outFile.length() > 0) {
                results += "file://${outFile.absolutePath}"
                onProgress(i + 1, pageUrls.size)
                continue
            }
            val bytes = downloadPage(url)
            val bitmap = translateOne(bytes)
            outFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            results += "file://${outFile.absolutePath}"
            onProgress(i + 1, pageUrls.size)
            if (i < pageUrls.lastIndex) delay(400)
        }
        results
    }

    private fun downloadPage(url: String): ByteArray {
        if (url.startsWith("file://")) return File(url.removePrefix("file://")).readBytes()
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Gagal mengunduh halaman: HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw IOException("Halaman kosong")
        }
    }

    private fun translateOne(imageBytes: ByteArray): Bitmap {
        var signups = 0
        while (true) {
            api.newSession()
            try {
                val created = create(imageBytes)
                val result = if (created.code == IsCodes.SUCCESS && created.payload.isNotEmpty()) created
                else poll(created.taskPayload ?: throw IsApiException("taskPayload kosong"))
                return compose(imageBytes, result.payload)
            } catch (e: IsApiException) {
                if (e.code == IsCodes.QUOTA_EXCEEDED) {
                    if (signups >= 3) throw IsApiException("Kuota habis, coba lagi nanti", 10003)
                    signups++; api.signupNew(); continue
                }
                throw e
            }
        }
    }

    private fun create(bytes: ByteArray): IsResult {
        val dataUrl = IsImage.toDataUrl(bytes)
        val vars = JSONObject().apply {
            put("dataUrl", dataUrl); put("dataUrlId", IsImage.dataUrlId(dataUrl))
            put("originUrl", "https://ismanga.com")
            put("langCode", "id-ID"); put("selectedLangCode", "en-US")
            put("isScreenshot", false); put("shouldSkipCache", true); put("shouldForceRefresh", false)
        }
        val q = """
            mutation CreateMangaByDataUrl(${'$'}dataUrl: String!, ${'$'}dataUrlId: String!, ${'$'}originUrl: String!, ${'$'}langCode: String!, ${'$'}selectedLangCode: String!, ${'$'}isScreenshot: Boolean!, ${'$'}shouldSkipCache: Boolean!, ${'$'}shouldForceRefresh: Boolean!) {
              createMangaByDataUrl(dataUrl: ${'$'}dataUrl, dataUrlId: ${'$'}dataUrlId, originUrl: ${'$'}originUrl, langCode: ${'$'}langCode, selectedLangCode: ${'$'}selectedLangCode, isScreenshot: ${'$'}isScreenshot, shouldSkipCache: ${'$'}shouldSkipCache, shouldForceRefresh: ${'$'}shouldForceRefresh) {
                code payload { x y width height toLang{code script} imgWidth imgHeight inpaintingUrl colors{text background} }
                taskPayload { taskId url isPseudoUrl isHighQuality langCode shouldCache }
              }
            }
        """.trimIndent()
        val res = api.graphql(q, vars)
        if (res.has("errors")) throw IsApiException("GraphQL error: ${res.optJSONArray("errors")}")
        val data = res.optJSONObject("data")?.optJSONObject("createMangaByDataUrl") ?: throw IsApiException("createMangaByDataUrl kosong")
        val result = parseIsResult(data)
        if (result.code == IsCodes.QUOTA_EXCEEDED) throw IsApiException("Kuota habis", 10003)
        if (result.code == IsCodes.SUCCESS && result.payload.isNotEmpty()) return result
        if (result.code in IsCodes.PENDING) { if (result.taskPayload == null) throw IsApiException("Tidak ada taskId"); return result }
        throw IsApiException(IsCodes.message(result.code), result.code)
    }

    private fun poll(task: IsTaskPayload, maxSeconds: Int = 180): IsResult {
        val start = System.currentTimeMillis(); var noSuch = 0
        val q = """
            query PollTranslationQueue(${'$'}taskId: String!, ${'$'}isHighQuality: Boolean!, ${'$'}url: String!, ${'$'}isPseudoUrl: Boolean!, ${'$'}langCode: String!, ${'$'}shouldCache: Boolean!) {
              pollTranslationQueue(taskId: ${'$'}taskId, isHighQuality: ${'$'}isHighQuality, url: ${'$'}url, isPseudoUrl: ${'$'}isPseudoUrl, langCode: ${'$'}langCode, shouldCache: ${'$'}shouldCache) {
                code payload { x y width height toLang{code script} imgWidth imgHeight inpaintingUrl colors{text background} }
                taskPayload { taskId url isPseudoUrl isHighQuality langCode shouldCache }
              }
            }
        """.trimIndent()
        while (System.currentTimeMillis() - start < maxSeconds * 1000L) {
            val vars = JSONObject().apply {
                put("taskId", task.taskId); put("isHighQuality", task.isHighQuality)
                put("url", task.url ?: ""); put("isPseudoUrl", task.isPseudoUrl)
                put("langCode", task.langCode ?: ""); put("shouldCache", task.shouldCache)
            }
            val res = api.graphql(q, vars)
            if (res.has("errors")) { Thread.sleep(1000); continue }
            val data = res.optJSONObject("data")?.optJSONObject("pollTranslationQueue") ?: run { Thread.sleep(1000); continue }
            val result = parseIsResult(data)
            when {
                result.code == IsCodes.SUCCESS -> { if (result.payload.isEmpty()) throw IsApiException("Sukses tapi payload kosong"); return result }
                result.code in IsCodes.PENDING -> Thread.sleep(1000)
                result.code == IsCodes.NO_SUCH_TASK && noSuch < 5 -> { noSuch++; Thread.sleep(1000) }
                result.code == IsCodes.QUOTA_EXCEEDED -> throw IsApiException("Kuota habis", 10003)
                else -> throw IsApiException(IsCodes.message(result.code), result.code)
            }
        }
        throw IsApiException("Timeout menerjemahkan halaman (${maxSeconds}s)")
    }

    private fun compose(originalBytes: ByteArray, boxes: List<IsBox>): Bitmap {
        val original = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size) ?: throw IsApiException("Gagal decode gambar")
        val w = boxes.firstOrNull()?.imgWidth?.takeIf { it > 0 } ?: original.width
        val h = boxes.firstOrNull()?.imgHeight?.takeIf { it > 0 } ?: original.height
        val base = Bitmap.createScaledBitmap(original, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        if (original != base) original.recycle()
        val canvas = Canvas(base)
        for (box in boxes) {
            val url = box.inpaintingUrl ?: continue
            val patchBytes = runCatching { IsHttp.getBytes(url) }.getOrNull() ?: continue
            val patch = BitmapFactory.decodeByteArray(patchBytes, 0, patchBytes.size) ?: continue
            val isFull = kotlin.math.abs(patch.width - w) <= 2 && kotlin.math.abs(patch.height - h) <= 2
            if (isFull) canvas.drawBitmap(patch, null, Rect(0, 0, w, h), null)
            else {
                val left = box.x.toInt().coerceAtLeast(0); val top = box.y.toInt().coerceAtLeast(0)
                val bw = box.width.toInt().coerceAtLeast(1).coerceAtMost((w - left).coerceAtLeast(1))
                val bh = box.height.toInt().coerceAtLeast(1).coerceAtMost((h - top).coerceAtLeast(1))
                canvas.drawBitmap(patch, null, Rect(left, top, left + bw, top + bh), null)
            }
            patch.recycle()
        }
        return renderer.draw(base, boxes)
    }
}

/** Progress/result state for chapter-wide translation (IsManga). */
data class ChapterTranslationState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Translated page URIs (file://) when complete. */
    val pages: List<String>? = null,
    val error: String? = null,
)
