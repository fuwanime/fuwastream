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
import com.fuwanime.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============== IsManga workflow (adapted) ==============
// Translates manga page images to Indonesian via IsManga GraphQL API (server-side),
// then composites translated text and saves resulting PNGs locally for the reader.

private const val ISMANGA_ENDPOINT = "https://api.ismanga.com/graphql"
private const val FIREBASE_API_KEY = "AIzaSyApW93EPxiSW6FwUVFc42GT-Q8ILs9wtkk"

internal data class TranslationUploadSize(val width: Int, val height: Int)

/** Preserves aspect ratio while keeping an upload inside both side-length and total-pixel limits. */
internal fun translationUploadSize(
    width: Int,
    height: Int,
    maxDimension: Int = 4_096,
    maxPixels: Long = 8_000_000L,
): TranslationUploadSize {
    if (width <= 0 || height <= 0) return TranslationUploadSize(1, 1)
    val pixels = width.toDouble() * height.toDouble()
    val scale = min(
        1.0,
        min(
            min(maxDimension.toDouble() / width, maxDimension.toDouble() / height),
            sqrt(maxPixels.toDouble() / pixels),
        ),
    )
    return TranslationUploadSize(
        width = (width * scale).toInt().coerceIn(1, maxDimension),
        height = (height * scale).toInt().coerceIn(1, maxDimension),
    )
}

private data class IsUploadLimits(
    val maxDimension: Int,
    val maxPixels: Long,
    val maxBytes: Int,
)

/**
 * IsManga sometimes returns success with no text boxes for a page that does have dialogue. Retry once with a fresh
 * task; if it happens again the page is kept as-is (textless pages are valid) instead of failing the whole chapter.
 */
internal const val EMPTY_PAYLOAD_RETRY_LIMIT = 1

internal fun shouldRetryEmptyTranslation(attempt: Int): Boolean = attempt < EMPTY_PAYLOAD_RETRY_LIMIT

/** Each retry is deliberately smaller for undocumented/variable IsManga GPU limits. */
private val IS_UPLOAD_LIMITS = listOf(
    IsUploadLimits(maxDimension = 4_096, maxPixels = 8_000_000L, maxBytes = 4_500_000),
    IsUploadLimits(maxDimension = 3_072, maxPixels = 6_000_000L, maxBytes = 4_000_000),
    IsUploadLimits(maxDimension = 2_048, maxPixels = 4_000_000L, maxBytes = 3_500_000),
)

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

    /** Internal marker: IsManga reported success but returned no text boxes (e.g. a page without dialogue). */
    const val EMPTY_PAYLOAD = -2
    const val FILE_TOO_LARGE = 9
    const val STILL_IN_QUEUE = 12
    const val PROCESSING = 13
    const val NO_SUCH_TASK = 15
    const val ENQUEUED = 16
    const val DUPLICATE = 17
    const val DIMENSIONS_TOO_LARGE = 21
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

    /**
     * IsManga rejects files above 5 MB and some high-resolution pages with code 21. Keep ordinary pages byte-for-byte,
     * but proportionally downsample/re-encode oversized pages before the base64 upload. [limits] becomes stricter on
     * server rejection, which also covers limits that differ between IsManga workers.
     */
    fun prepareUpload(bytes: ByteArray, limits: IsUploadLimits): ByteArray {
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IsApiException("Gagal membaca dimensi gambar")

        val target = translationUploadSize(bounds.outWidth, bounds.outHeight, limits.maxDimension, limits.maxPixels)
        if (target.width == bounds.outWidth && target.height == bounds.outHeight && bytes.size <= limits.maxBytes) return bytes

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= target.width &&
            bounds.outHeight / (sample * 2) >= target.height
        ) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: throw IsApiException("Gagal decode gambar besar")

        var working = if (decoded.width == target.width && decoded.height == target.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also { decoded.recycle() }
        }
        try {
            var encoded = encodeJpeg(working, 90)
            for (quality in listOf(82, 74, 66)) {
                if (encoded.size <= limits.maxBytes) return encoded
                encoded = encodeJpeg(working, quality)
            }
            // Unusually noisy PNG/WebP pages can remain large after JPEG compression. Reduce dimensions gradually.
            repeat(4) {
                if (encoded.size <= limits.maxBytes) return encoded
                val nextWidth = (working.width * 0.82).toInt().coerceAtLeast(1)
                val nextHeight = (working.height * 0.82).toInt().coerceAtLeast(1)
                val smaller = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
                if (smaller !== working) working.recycle()
                working = smaller
                encoded = encodeJpeg(working, 76)
            }
            return encoded
        } finally {
            if (!working.isRecycled) working.recycle()
        }
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IsApiException("Gagal mengompres gambar")
            out.toByteArray()
        }

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
        val result = if (base.isMutable && base.config == Bitmap.Config.ARGB_8888) base else base.copy(Bitmap.Config.ARGB_8888, true)
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
        return IsAccount(email, pass, json.optString("idToken"), json.optString("refreshToken"), json.optString("localId"), json.optString("expiresIn", "3600"), java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply{timeZone=java.util.TimeZone.getTimeZone("UTC")}.format(java.util.Date()))
    }
    fun refresh(a: IsAccount): IsAccount {
        val form = "grant_type=refresh_token&refresh_token=${java.net.URLEncoder.encode(a.refreshToken, "UTF-8")}"
        val json = IsHttp.postForm(REFRESH, form) ?: throw IsApiException("Refresh response kosong")
        if (json.has("error") || !json.has("id_token")) throw IsApiException("Refresh gagal: ${json.optJSONObject("error")?.optString("message") ?: json}")
        a.idToken = json.optString("id_token"); a.refreshToken = json.optString("refresh_token", a.refreshToken)
        a.localId = json.optString("user_id", a.localId); a.expiresIn = json.optString("expires_in", a.expiresIn)
        a.updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply{timeZone=java.util.TimeZone.getTimeZone("UTC")}.format(java.util.Date()); return a
    }
}

private class IsMangaApi(private val store: IsAccountStore) {
    private val lock = Any()
    private var account: IsAccount? = null

    private fun ensureAccount(): IsAccount = synchronized(lock) {
        account?.let { return it }
        val accounts = store.load()
        if (accounts.isEmpty()) {
            val created = IsAuth.createAccount(); store.add(created); store.save(); account = created; return created
        }
        for (i in accounts.indices.reversed()) {
            val a = accounts[i]
            val exp = IsImage.decodeJwtExp(a.idToken)
            if (exp > 0 && exp - 300 > (System.currentTimeMillis() / 1000L)) { account = a; return a }
            try { IsAuth.refresh(a); store.save(); account = a; return a } catch (_: Exception) {}
        }
        val created = IsAuth.createAccount(); store.add(created); store.save(); account = created; return created
    }

    private fun authToken(): String = synchronized(lock) { ensureAccount().idToken }

    private fun refreshAccount() = synchronized(lock) {
        val a = ensureAccount(); IsAuth.refresh(a); store.save(); account = a
    }

    private fun isAuthExpired(res: JSONObject?): Boolean {
        val errs = res?.optJSONArray("errors") ?: return false
        for (i in 0 until errs.length()) {
            val msg = (errs.optJSONObject(i)?.optString("message") ?: "") + (errs.optJSONObject(i)?.optJSONObject("extensions")?.optString("code") ?: "")
            if ("id-token-expired" in msg || "ID token has expired" in msg) return true
        }
        return false
    }

    fun graphql(query: String, variables: JSONObject, visitId: String, deviceId: String): JSONObject {
        val body = JSONObject().apply { put("query", query); put("variables", variables) }
        fun headers() = mapOf("Authorization" to authToken(), "ismanga-visit-id" to visitId, "ismanga-device-id" to deviceId)
        var res = IsHttp.postJson(ISMANGA_ENDPOINT, body, headers())
        if (isAuthExpired(res)) {
            refreshAccount(); res = IsHttp.postJson(ISMANGA_ENDPOINT, body, headers())
        }
        return res ?: JSONObject()
    }

    fun signupNew() = synchronized(lock) {
        val a = IsAuth.createAccount(); store.add(a); store.save(); account = a
    }
}

class IsMangaChapterTranslator(
    context: Context,
    private val httpClient: OkHttpClient,
) : MangaChapterTranslator {
    private val store = IsAccountStore(File(context.filesDir, "ismanga_accounts.json"))
    private val api = IsMangaApi(store)
    private val renderer = IsTextRenderer()

    /** Directory that holds translated PNGs for [chapterId]. */
    override fun chapterDir(cacheDir: File, chapterId: String): File =
        File(cacheDir, "ismanga/${chapterId.replace(':', '_').replace('/', '_')}")

    /** `file://` URIs of cached translated pages when every page in the chapter is ready. */
    override fun cachedChapterPages(cacheDir: File, chapterId: String, pageCount: Int): List<String>? {
        if (pageCount <= 0) return null
        val dir = chapterDir(cacheDir, chapterId)
        val files = (0 until pageCount).map { File(dir, "p_${it.toString().padStart(4, '0')}.png") }
        return if (files.all { it.exists() && it.length() > 0 }) files.map { "file://${it.absolutePath}" } else null
    }

    /** Translate all pages of a chapter and return local file:// URIs for the translated PNGs (cached). */
    override suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val outDir = chapterDir(cacheDir, chapterId)
        val outputFiles = (pageUrls.indices).map { File(outDir, "p_${it.toString().padStart(4, '0')}.png") }
        if (outputFiles.isNotEmpty() && outputFiles.all { it.exists() && it.length() > 0 }) {
            onProgress(outputFiles.size, outputFiles.size)
            return@withContext outputFiles.map { "file://${it.absolutePath}" }
        }
        outDir.mkdirs()

        // Only process pages not yet cached; translate in parallel with bounded concurrency to overlap server work.
        val pending = pageUrls.indices.filterNot { outputFiles[it].exists() && outputFiles[it].length() > 0 }.toMutableList()
        val doneCount = java.util.concurrent.atomic.AtomicInteger(pageUrls.size - pending.size)
        if (doneCount.get() > 0) onProgress(doneCount.get(), pageUrls.size)

        val concurrency = 3.coerceAtMost(pending.size.coerceAtLeast(1))
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            pending.map { i ->
                async {
                    semaphore.withPermit {
                        val bytes = downloadPage(pageUrls[i])
                        val bitmap = translateOne(bytes)
                        outputFiles[i].outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                        val done = doneCount.incrementAndGet()
                        onProgress(done, pageUrls.size)
                    }
                }
            }.awaitAll()
        }
        outputFiles.map { "file://${it.absolutePath}" }
    }

    private fun downloadPage(url: String): ByteArray {
        if (url.startsWith("file://")) return File(url.removePrefix("file://")).readBytes()
        val candidates = mangaPageCandidates(url).ifEmpty { listOf(url) }
        var lastError: IOException? = null
        for (candidate in candidates) {
            try {
                val req = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    return resp.body?.bytes() ?: throw IOException("Halaman kosong")
                }
            } catch (e: Exception) {
                lastError = if (e is IOException) e else IOException(e.message ?: "Gagal mengunduh halaman", e)
            }
        }
        throw IOException("Gagal mengunduh halaman: ${lastError?.message ?: "unknown"}", lastError)
    }

    private fun translateOne(imageBytes: ByteArray): Bitmap {
        var signups = 0
        var resizeAttempt = 0
        var emptyPayloadAttempts = 0
        var uploadBytes = IsImage.prepareUpload(imageBytes, IS_UPLOAD_LIMITS[resizeAttempt])
        while (true) {
            val visitId = UUID.randomUUID().toString()
            val deviceId = UUID.randomUUID().toString()
            try {
                val created = create(uploadBytes, visitId, deviceId)
                val result = if (created.code == IsCodes.SUCCESS && created.payload.isNotEmpty()) created
                else poll(created.taskPayload ?: throw IsApiException("taskPayload kosong"), visitId, deviceId)
                return compose(uploadBytes, result.payload)
            } catch (e: IsApiException) {
                val sizeRejected = e.code in setOf(IsCodes.FILE_TOO_LARGE, IsCodes.DIMENSIONS_TOO_LARGE) ||
                    e.message.orEmpty().contains("too large", ignoreCase = true) ||
                    e.message.orEmpty().contains("terlalu besar", ignoreCase = true)
                if (
                    sizeRejected &&
                    resizeAttempt < IS_UPLOAD_LIMITS.lastIndex
                ) {
                    resizeAttempt++
                    uploadBytes = IsImage.prepareUpload(imageBytes, IS_UPLOAD_LIMITS[resizeAttempt])
                    continue
                }
                if (e.code == IsCodes.QUOTA_EXCEEDED) {
                    if (signups >= 3) throw IsApiException("Kuota habis, coba lagi nanti", 10003)
                    signups++; api.signupNew(); continue
                }
                if (e.code == IsCodes.EMPTY_PAYLOAD) {
                    if (shouldRetryEmptyTranslation(emptyPayloadAttempts)) {
                        emptyPayloadAttempts++
                        Thread.sleep(600)
                        continue
                    }
                    // Pages without dialogue legitimately have no boxes; show the original instead of failing.
                    return compose(uploadBytes, emptyList())
                }
                throw e
            }
        }
    }

    private fun create(bytes: ByteArray, visitId: String, deviceId: String): IsResult {
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
        val res = api.graphql(q, vars, visitId, deviceId)
        if (res.has("errors")) throw IsApiException("GraphQL error: ${res.optJSONArray("errors")}")
        val data = res.optJSONObject("data")?.optJSONObject("createMangaByDataUrl") ?: throw IsApiException("createMangaByDataUrl kosong")
        val result = parseIsResult(data)
        if (result.code == IsCodes.QUOTA_EXCEEDED) throw IsApiException("Kuota habis", 10003)
        if (result.code == IsCodes.SUCCESS) {
            if (result.payload.isEmpty()) throw IsApiException("Tidak ada teks terdeteksi", IsCodes.EMPTY_PAYLOAD)
            return result
        }
        if (result.code in IsCodes.PENDING) { if (result.taskPayload == null) throw IsApiException("Tidak ada taskId"); return result }
        throw IsApiException(IsCodes.message(result.code), result.code)
    }

    private fun poll(task: IsTaskPayload, visitId: String, deviceId: String, maxSeconds: Int = 180): IsResult {
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
            val res = api.graphql(q, vars, visitId, deviceId)
            if (res.has("errors")) { Thread.sleep(800); continue }
            val data = res.optJSONObject("data")?.optJSONObject("pollTranslationQueue") ?: run { Thread.sleep(800); continue }
            val result = parseIsResult(data)
            when {
                result.code == IsCodes.SUCCESS -> {
                    if (result.payload.isEmpty()) throw IsApiException("Tidak ada teks terdeteksi", IsCodes.EMPTY_PAYLOAD)
                    return result
                }
                result.code in IsCodes.PENDING -> Thread.sleep(800)
                result.code == IsCodes.NO_SUCH_TASK && noSuch < 5 -> { noSuch++; Thread.sleep(1000) }
                result.code == IsCodes.QUOTA_EXCEEDED -> throw IsApiException("Kuota habis", 10003)
                else -> throw IsApiException(IsCodes.message(result.code), result.code)
            }
        }
        throw IsApiException("Timeout menerjemahkan halaman (${maxSeconds}s)")
    }

    private fun compose(originalBytes: ByteArray, boxes: List<IsBox>): Bitmap {
        val original = BitmapFactory.decodeByteArray(
            originalBytes,
            0,
            originalBytes.size,
            BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw IsApiException("Gagal decode gambar")
        val w = boxes.firstOrNull()?.imgWidth?.takeIf { it > 0 } ?: original.width
        val h = boxes.firstOrNull()?.imgHeight?.takeIf { it > 0 } ?: original.height
        val scaled = if (original.width == w && original.height == h) original else Bitmap.createScaledBitmap(original, w, h, true)
        if (scaled !== original) original.recycle()
        val base = if (scaled.isMutable && scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
        if (base !== scaled) scaled.recycle()
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

/** Thrown by the translator when it is busy/unavailable so callers can retry or report it. */
class TranslatorBusyException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Chapter image translation backend (produces cached file:// page images). */
interface MangaChapterTranslator {
    /** Directory that holds translated PNGs for [chapterId]. */
    fun chapterDir(cacheDir: File, chapterId: String): File

    /** `file://` URIs of cached translated pages when every page in the chapter is ready. */
    fun cachedChapterPages(cacheDir: File, chapterId: String, pageCount: Int): List<String>?

    /**
     * One cache slot per chapter page. A null slot means that page is still being translated.
     * The default keeps other translator implementations source-compatible; progressive
     * translators should override this so the reader can display completed pages immediately.
     */
    fun cachedChapterPageSlots(cacheDir: File, chapterId: String, pageCount: Int): List<String?> {
        if (pageCount <= 0) return emptyList()
        val complete = cachedChapterPages(cacheDir, chapterId, pageCount)
        return complete?.map { it } ?: List(pageCount) { null }
    }

    /** Translate all pages of a chapter and return local file:// URIs for the translated PNGs (cached). */
    suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String>
}

/** Progress/result state for chapter-wide translation. */
data class ChapterTranslationState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Reader page URIs. While running this mixes ready translations with original pages. */
    val pages: List<String>? = null,
    /** Localized failure message; internal exception text is never shown to the reader. */
    val error: UiText? = null,
)

/** Splits text at line boundaries for chunked processing; keeps every line. */
internal fun translationChunks(text: String, limit: Int = 3_000): List<String> {
    if (text.length <= limit) return listOf(text)
    val chunks = ArrayList<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotBlank()) chunks += current.toString().trim()
        current.clear()
    }
    for (line in text.lineSequence()) {
        if (line.length > limit) {
            flush()
            var start = 0
            while (start < line.length) {
                val end = (start + limit).coerceAtMost(line.length)
                chunks += line.substring(start, end)
                start = end
            }
            continue
        }
        if (current.isNotEmpty() && current.length + line.length + 1 > limit) flush()
        if (current.isNotEmpty()) current.append('\n')
        current.append(line)
    }
    flush()
    return chunks
}
