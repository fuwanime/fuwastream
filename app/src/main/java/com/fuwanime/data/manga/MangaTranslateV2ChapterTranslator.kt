package com.fuwanime.data.manga

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

private const val MT_BASE = "https://www.mangatranslate.com"
private const val MT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
private const val MT_PREPARE = "/api/v2/api/translate/uploads"
private const val MT_SUBMIT = "/api/v2/api/translate"
private const val MT_TASK_TMPL = "/api/v2/api/tasks/{batch_id}"
private const val MT_CREDITS_V1 = "/api/v1/manga/credits/current"
private const val MT_CREDITS_V2 = "/api/v2/api/credits"
private const val MT_CREATE_API_KEY = "/api/v1/api-keys"
private const val MT_REGISTER = "/api/v1/auth/register/email"
private const val MT_LOGIN = "/api/v1/auth/login/email"
private const val MT_CHECKIN_STATUS = "/api/v1/dashboard/checkin-status"
private const val MT_PERFORM_CHECKIN = "/api/v1/dashboard/perform-checkin"
private const val MT_RESEND_VERIFICATION = "/api/v1/auth/resend-verification"
private const val AKUNLAMA_API = "https://akunlama.com/api"
private const val DEFAULT_REG_DOMAIN = "akunlama.com"

private val MT_DEFAULT_PROXIES = listOf(
    "http://103.237.102.191:11111",
    "http://121.101.131.110:8080",
    "http://132.243.174.245:5002",
    "http://46.39.105.157:8080",
    "http://34.105.252.3:3128",
    "http://49.147.46.158:8082",
    "http://18.170.45.5:21005",
    "http://27.147.155.206:12051",
    "http://160.19.18.243:8080",
    "http://20.98.234.24:8080",
    "http://8.219.94.23:7890",
    "http://130.110.103.245:3128",
    "http://176.111.37.5:39811",
    "http://4.144.146.21:80",
    "http://128.199.254.13:9090",
    "http://98.81.204.137:9459",
    "http://3.134.245.77:57240",
    "http://157.15.211.48:8080",
    "http://45.155.226.177:3128",
    "http://206.135.80.94:999",
    "http://23.185.192.66:8181",
    "http://180.180.156.53:8080",
    "http://103.145.149.230:80",
    "http://188.166.178.163:3129",
    "http://207.248.108.129:20185",
    "http://102.244.78.61:8081",
    "http://49.146.155.183:8082",
    "http://111.90.188.156:8080",
    "http://104.251.93.236:16062",
    "http://123.16.15.41:1452",
    "http://119.93.169.230:8087",
    "http://35.43.246.3:38395",
    "http://41.216.57.75:8080",
    "http://122.53.48.41:8080",
    "http://200.59.191.146:999",
    "http://119.17.211.204:8080",
    "http://157.20.35.40:9090",
    "http://218.100.23.130:8080",
    "http://103.191.251.197:8080",
    "http://51.17.99.135:35715",
    "http://94.183.206.215:8080",
    "http://185.238.238.165:58080",
    "http://180.191.121.186:8181",
    "http://157.15.116.75:8080",
    "http://3.21.156.147:20303",
    "http://102.244.78.61:8086",
    "http://45.190.184.105:8080",
    "http://184.75.221.82:3118",
    "http://45.167.124.31:999",
    "http://201.4.65.190:8080",
)

/** Proxies confirmed to reach MangaTranslate; tried before the shuffled reserve pool. */
private val MT_PREFERRED_PROXIES = listOf(
    "http://103.237.102.191:11111",
    "http://132.243.174.245:5002",
    "http://34.105.252.3:3128",
    "http://20.98.234.24:8080",
    "http://130.110.103.245:3128",
    "http://176.111.37.5:39811",
    "http://4.144.146.21:80",
    "http://128.199.254.13:9090",
    "http://45.155.226.177:3128",
    "http://41.216.57.75:8080",
    "http://3.21.156.147:20303",
    "http://184.75.221.82:3118",
)

private fun parseHttpProxy(url: String): Proxy? {
    return try {
        val u = java.net.URI(url)
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 8080
        Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    } catch (_: Exception) { null }
}

private fun checkInterrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException("MangaTranslate dibatalkan")
}

private fun interruptibleSleep(ms: Long) {
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) {
        checkInterrupted()
        val left = end - System.currentTimeMillis()
        if (left <= 0) break
        try {
            Thread.sleep(min(left, 250L))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedException("MangaTranslate dibatalkan")
        }
    }
}

private fun isTransientTranslateError(e: Throwable): Boolean {
    val msg = (e.message ?: "").lowercase()
    return msg.contains("timeout") || msg.contains("timed out") || msg.contains("failed to connect") ||
        msg.contains("connection") || msg.contains("socket") || msg.contains("reset") ||
        msg.contains("network") || msg.contains("fetch failed") || msg.contains("http 5") ||
        msg.contains("download hasil gagal http 5") || msg.contains("upload ke storage gagal http 5")
}

/** MangaTranslate.com V2 chapter translator. Throws [TranslatorBusyException] when busy/unavailable. */
class MangaTranslateV2ChapterTranslator(
    context: Context,
    private val httpClient: OkHttpClient,
) : MangaChapterTranslator {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "mangatranslate_accounts.json")
    private val provisionLock = ReentrantLock()
    private val translationLock = ReentrantLock(true)
    private val provisionProxies = (MT_PREFERRED_PROXIES +
        MT_DEFAULT_PROXIES.filterNot { it in MT_PREFERRED_PROXIES }.shuffled()).distinct()
    private val proxyClients = ConcurrentHashMap<String, OkHttpClient>()
    private var provisionProxyIndex = 0
    private var triedDirectProvision = false

    // direct client for akunlama/non-mangatranslate calls (never proxied)
    private val directClient: OkHttpClient = httpClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private val storageClient: OkHttpClient = httpClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun chapterDir(cacheDir: File, chapterId: String): File =
        File(cacheDir, "mangatranslate/${chapterId.replace(':', '_').replace('/', '_')}")

    override fun cachedChapterPages(cacheDir: File, chapterId: String, pageCount: Int): List<String>? {
        val slots = cachedChapterPageSlots(cacheDir, chapterId, pageCount)
        return if (slots.isNotEmpty() && slots.all { it != null }) slots.filterNotNull() else null
    }

    override fun cachedChapterPageSlots(cacheDir: File, chapterId: String, pageCount: Int): List<String?> {
        if (pageCount <= 0) return emptyList()
        val dir = chapterDir(cacheDir, chapterId)
        return (0 until pageCount).map { index ->
            pageFile(dir, index).takeIf { it.exists() && it.length() > 0 }?.let { "file://${it.absolutePath}" }
        }
    }

    private fun pageFile(dir: File, index: Int): File = File(dir, "p_${index.toString().padStart(4, '0')}.png")

    override suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        translationLock.lockInterruptibly()
        try {
            val outDir = chapterDir(cacheDir, chapterId)
            outDir.mkdirs()
            val outputFiles = pageUrls.indices.map { pageFile(outDir, it) }
            if (outputFiles.isNotEmpty() && outputFiles.all { it.exists() && it.length() > 0 }) {
                onProgress(outputFiles.size, outputFiles.size)
                return@withContext outputFiles.map { "file://${it.absolutePath}" }
            }

            val pending = pageUrls.indices.filterNot { outputFiles[it].exists() && outputFiles[it].length() > 0 }
            var doneCount = pageUrls.size - pending.size
            if (doneCount > 0) onProgress(doneCount, pageUrls.size)

            // Ensure we have at least one usable API key/account up front; if not, primary is busy/unavailable.
            ensureUsableKey()

            // MangaTranslate accounts have very small credit balances. Sequential processing mirrors
            // the proven CLI flow and prevents two parallel pages racing for the final credit, where
            // one succeeds server-side while the other aborts the whole chapter.
            val failures = mutableListOf<Throwable>()
            for (i in pending) {
                checkInterrupted()
                try {
                    translatePageWithRetry(pageUrls[i], outputFiles[i])
                    doneCount++
                    onProgress(doneCount, pageUrls.size)
                } catch (e: TranslatorBusyException) {
                    throw e
                } catch (e: Throwable) {
                    failures += e
                }
            }
            val firstFailure = failures.firstOrNull()
            if (firstFailure != null) {
                val busy = firstFailure is TranslatorBusyException || isCreditOrBusyMessage(firstFailure.message)
                if (busy) throw TranslatorBusyException(firstFailure.message, firstFailure)
                throw (firstFailure as? Exception) ?: Exception(firstFailure.message, firstFailure)
            }
            outputFiles.map { "file://${it.absolutePath}" }
        } finally {
            translationLock.unlock()
        }
    }

    private fun translatePageWithRetry(url: String, out: File) {
        var last: Throwable? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            checkInterrupted()
            try {
                val bytes = downloadPage(url)
                val translated = translateOne(bytes)
                saveAsPng(translated, out)
                return
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: TranslatorBusyException) {
                throw e
            } catch (e: MtV2Exception) {
                if (isCreditOrBusyMessage(e.message) || e.status == 429 || e.status == 402) throw TranslatorBusyException(e.message, e)
                last = e
                if (!isTransientTranslateError(e) || attempt >= maxAttempts) throw e
            } catch (e: IOException) {
                last = e
                if (attempt >= maxAttempts) throw e
            } catch (e: Exception) {
                last = e
                if (e is java.util.concurrent.CancellationException) throw e
                if (!isTransientTranslateError(e) || attempt >= maxAttempts) throw e
            }
            interruptibleSleep((600L * attempt).coerceAtMost(2000L))
        }
        throw (last as? Exception) ?: Exception(last?.message ?: "Gagal menerjemahkan halaman", last)
    }

    private fun downloadPage(url: String): ByteArray {
        if (url.startsWith("file://")) return File(url.removePrefix("file://")).readBytes()
        val candidates = mangaPageCandidates(url).ifEmpty { listOf(url) }
        var last: IOException? = null
        for (candidate in candidates) {
            checkInterrupted()
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
                if (e is InterruptedException) throw e
                last = if (e is IOException) e else IOException(e.message ?: "Gagal mengunduh halaman", e)
            }
        }
        throw IOException("Gagal mengunduh halaman: ${last?.message ?: "unknown"}", last)
    }

    private fun saveAsPng(bytes: ByteArray, out: File) {
        out.parentFile?.mkdirs()
        val temp = File(out.parentFile, ".${out.name}.part")
        temp.delete()
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            if (bmp != null) {
                try {
                    temp.outputStream().use { stream ->
                        if (!bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw IOException("Gagal menyimpan hasil terjemahan")
                        }
                    }
                } finally {
                    bmp.recycle()
                }
            } else {
                temp.writeBytes(bytes)
            }
            if (out.exists() && !out.delete()) throw IOException("Gagal mengganti cache terjemahan")
            if (!temp.renameTo(out)) throw IOException("Gagal menyelesaikan cache terjemahan")
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    private fun loadStore(): JSONObject {
        return try { if (storeFile.exists()) JSONObject(storeFile.readText()) else JSONObject().put("accounts", JSONArray()) } catch (_: Exception) { JSONObject().put("accounts", JSONArray()) }
    }

    @Synchronized
    private fun saveStore(store: JSONObject) {
        runCatching { storeFile.parentFile?.mkdirs(); storeFile.writeText(store.toString()) }
    }

    private fun accountsArray(store: JSONObject): JSONArray {
        if (!store.has("accounts")) store.put("accounts", JSONArray())
        return store.optJSONArray("accounts") ?: JSONArray().also { store.put("accounts", it) }
    }

    private fun pickKnownGoodApiKey(store: JSONObject): String? {
        val arr = accountsArray(store)
        for (i in arr.length() - 1 downTo 0) {
            val a = arr.optJSONObject(i) ?: continue
            val key = a.optString("apiKey", a.optString("api_key", ""))
            if (key.isBlank()) continue
            val credits = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", Double.NaN) else Double.NaN
            if (!credits.isNaN() && credits > 0.0) return key
        }
        return null
    }

    private fun markAccountNoCredits(apiKey: String) {
        synchronized(this) {
            val store = loadStore()
            val arr = accountsArray(store)
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val key = a.optString("apiKey", a.optString("api_key", ""))
                if (key == apiKey) a.put("totalCredits", 0)
            }
            // remove all 0-credit accounts to keep store clean
            val cleaned = JSONArray()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val c = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", Double.NaN) else Double.NaN
                if (!c.isNaN() && c <= 0.0) continue
                cleaned.put(a)
            }
            store.put("accounts", cleaned)
            saveStore(store)
        }
    }

    private fun recordCreditUsed(apiKey: String) {
        synchronized(this) {
            val store = loadStore()
            val accounts = accountsArray(store)
            var changed = false
            for (i in 0 until accounts.length()) {
                val account = accounts.optJSONObject(i) ?: continue
                val key = account.optString("apiKey", account.optString("api_key", ""))
                if (key != apiKey) continue
                val credits = account.optDouble("totalCredits", Double.NaN)
                if (!credits.isNaN()) {
                    account.put("totalCredits", (credits - 1.0).coerceAtLeast(0.0))
                    changed = true
                }
                break
            }
            if (changed) saveStore(store)
        }
    }

    private fun ensureUsableKey(): String {
        provisionLock.withLock {
            val store = loadStore()
            pickKnownGoodApiKey(store)?.let { return it }
            // try to refresh credits for accounts with unknown credits (best effort, direct)
            val refreshed = refreshUnknownCreditAccount(store)
            if (refreshed != null) return refreshed
            val created = runCatching { provisionAccountWithCreditsLocked() }.getOrNull()
            if (created != null) return created
            throw TranslatorBusyException("MangaTranslate unavailable: no API key/credits")
        }
    }

    private fun refreshUnknownCreditAccount(store: JSONObject): String? {
        val arr = accountsArray(store)
        for (i in arr.length() - 1 downTo 0) {
            checkInterrupted()
            val a = arr.optJSONObject(i) ?: continue
            val key = a.optString("apiKey", a.optString("api_key", ""))
            if (key.isBlank()) continue
            val credits = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", Double.NaN) else Double.NaN
            if (!credits.isNaN()) continue // only unknown
            val total = runCatching { getCreditsWithPat(key) }.getOrNull()
            if (total != null && total > 0.0) {
                a.put("totalCredits", total)
                saveStore(store)
                return key
            } else if (total != null && total <= 0.0) {
                a.put("totalCredits", 0)
                saveStore(store)
            }
        }
        // clean 0-credit leftovers
        val cleaned = JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val c = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", Double.NaN) else Double.NaN
            if (!c.isNaN() && c <= 0.0) continue
            cleaned.put(a)
        }
        store.put("accounts", cleaned)
        saveStore(store)
        return pickKnownGoodApiKey(store)
    }

    private fun apiClientForKey(apiKey: String): OkHttpClient {
        val accounts = accountsArray(loadStore())
        for (i in 0 until accounts.length()) {
            val account = accounts.optJSONObject(i) ?: continue
            val key = account.optString("apiKey", account.optString("api_key", ""))
            if (key != apiKey) continue
            val proxyUrl = account.optString("proxyUrl", account.optString("proxy", ""))
            if (proxyUrl.isNotBlank()) return clientForProxy(proxyUrl)
            break
        }
        return httpClient
    }

    private fun getCreditsWithPat(apiKey: String, client: OkHttpClient = apiClientForKey(apiKey)): Double? {
        for (url in listOf(MT_BASE + MT_CREDITS_V2, MT_BASE + MT_CREDITS_V1)) {
            try {
                val req = Request.Builder().url(url).get()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .header("User-Agent", MT_UA)
                    .header("Origin", MT_BASE)
                    .header("Referer", "$MT_BASE/")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val j = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull() ?: return@use
                    val d = j.optJSONObject("data") ?: j
                    val v = when {
                        d.has("total_credits") -> d.optDouble("total_credits", Double.NaN)
                        d.has("totalCredits") -> d.optDouble("totalCredits", Double.NaN)
                        else -> Double.NaN
                    }
                    if (!v.isNaN()) return v
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun isCreditExhaustedMessage(msg: String?): Boolean {
        val m = (msg ?: "").lowercase()
        return "insufficient credit" in m || "insufficient_credits" in m || "no enough credit" in m ||
            ("credit" in m && ("not enough" in m || "insufficient" in m || "exhaust" in m || "habis" in m || "balance" in m || "saldo 0" in m)) ||
            ("quota" in m && ("exceed" in m || "exhaust" in m || "habis" in m || "insufficient" in m))
    }

    private fun isCreditOrBusyMessage(msg: String?): Boolean {
        val m = (msg ?: "").lowercase()
        return isCreditExhaustedMessage(m) ||
            "window_limit_exceeded" in m || "rate limit" in m || "too many requests" in m ||
            "antrian penuh" in m || "busy" in m || "unavailable" in m || "no api key" in m ||
            "timeout polling task" in m
    }

    private fun translateOne(imageBytes: ByteArray): ByteArray {
        var attempts = 0
        var lastErr: Throwable? = null
        while (attempts < 2) {
            checkInterrupted()
            val apiKey = ensureUsableKey()
            attempts++
            try {
                return translateOneWithKey(apiKey, imageBytes)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: TranslatorBusyException) {
                lastErr = e
                // Polling/server availability errors do not mean this account lost its credits.
                // Only rotate when the response explicitly says the balance is exhausted.
                if (!isCreditExhaustedMessage(e.message)) throw e
                markAccountNoCredits(apiKey)
                val rotated = runCatching { provisionLock.withLock { provisionAccountWithCreditsLocked() } }.getOrNull()
                if (rotated == null || attempts >= 2) throw e
                interruptibleSleep(300)
                continue
            } catch (e: MtV2Exception) {
                lastErr = e
                val shouldRotateAccount = isCreditExhaustedMessage(e.message) || e.status in setOf(401, 402, 403, 429)
                if (shouldRotateAccount) {
                    markAccountNoCredits(apiKey)
                    val rotated = runCatching { provisionLock.withLock { provisionAccountWithCreditsLocked() } }.getOrNull()
                    if (rotated == null || attempts >= 2) throw TranslatorBusyException(e.message, e)
                    interruptibleSleep(300)
                    continue
                }
                if (isCreditOrBusyMessage(e.message)) throw TranslatorBusyException(e.message, e)
                // other V2 errors: if transient/unavailable-like, retry a bit; else bubble up
                if (isTransientTranslateError(e) && attempts < 2) { interruptibleSleep(600L * attempts); continue }
                throw e
            } catch (e: IOException) {
                lastErr = e
                if (attempts >= 2) throw e
                interruptibleSleep(600L * attempts)
                continue
            }
        }
        throw (lastErr as? Exception) ?: TranslatorBusyException(lastErr?.message, lastErr)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun detectMime(bytes: ByteArray, fallback: String = "image/jpeg"): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"
        else -> fallback
    }

    private fun jsonRequest(client: OkHttpClient, url: String, apiKey: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val b = Request.Builder().url(url).post(reqBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", MT_UA)
            .header("Origin", MT_BASE)
            .header("Referer", "$MT_BASE/")
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optString("message", json.optString("detail", json.optString("error", ""))).ifBlank { "HTTP ${resp.code}" }
                throw MtV2Exception(msg, resp.code, json)
            }
            return json
        }
    }

    private fun getJson(client: OkHttpClient, url: String, apiKey: String): JSONObject {
        val b = Request.Builder().url(url).get()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("User-Agent", MT_UA)
            .header("Origin", MT_BASE)
            .header("Referer", "$MT_BASE/")
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optString("message", json.optString("detail", json.optString("error", ""))).ifBlank { "HTTP ${resp.code}" }
                throw MtV2Exception(msg, resp.code, json)
            }
            return json
        }
    }

    private fun downloadTranslatedResult(url: String): ByteArray {
        var lastError: Throwable? = null
        for (attempt in 1..3) {
            checkInterrupted()
            try {
                val request = Request.Builder().url(url).get().header("User-Agent", MT_UA).build()
                storageClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.bytes()?.takeIf { it.isNotEmpty() }
                            ?: throw IOException("Hasil terjemahan kosong")
                    }
                    val error = MtV2Exception("Download hasil gagal HTTP ${response.code}", response.code, JSONObject())
                    if (response.code !in 500..599 || attempt >= 3) throw error
                    lastError = error
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt >= 3) throw e
            }
            interruptibleSleep(750L * attempt)
        }
        throw (lastError as? Exception) ?: IOException("Gagal mengunduh hasil terjemahan", lastError)
    }

    private fun translateOneWithKey(apiKey: String, imageBytes: ByteArray): ByteArray {
        checkInterrupted()
        val apiClient = apiClientForKey(apiKey)
        val contentType = detectMime(imageBytes)
        val ext = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val filename = "page.$ext"
        val sha = sha256Hex(imageBytes)

        // 1) prepare upload
        val prepareBody = JSONObject().put("files", JSONArray().put(JSONObject().apply {
            put("filename", filename)
            put("content_type", contentType)
            put("size", imageBytes.size)
            put("content_sha256", sha)
        }))
        val prepare = jsonRequest(apiClient, MT_BASE + MT_PREPARE, apiKey, prepareBody, mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        val prepData = prepare.optJSONObject("data") ?: prepare
        val uploadId = prepData.optString("upload_id", prepData.optString("uploadId", prepData.optString("id", "")))
        val filesOut = prepData.optJSONArray("files") ?: prepData.optJSONArray("items")
        val f0 = filesOut?.optJSONObject(0)
        if (uploadId.isBlank() || f0 == null) throw MtV2Exception("Response prepare V2 tidak dikenali", 0, prepare)
        val uploadIntent = f0.optString("upload_intent", f0.optString("uploadIntent", f0.optString("file_id", f0.optString("intent", ""))))
        val index = f0.optInt("index", 0)
        val uploadInfo = f0.optJSONObject("upload") ?: f0.optJSONObject("target") ?: f0.optJSONObject("put") ?: f0
        val putUrl = uploadInfo.optString("url", uploadInfo.optString("upload_url", uploadInfo.optString("presigned_url", f0.optString("url", ""))))
        val putMethod = uploadInfo.optString("method", "PUT").uppercase()
        if (putUrl.isBlank()) throw MtV2Exception("Presigned upload URL tidak ditemukan", 0, prepare)
        val putHeaders = uploadInfo.optJSONObject("headers")

        // 2) PUT bytes
        checkInterrupted()
        val putBody = imageBytes.toRequestBody(contentType.toMediaType())
        val putReqB = Request.Builder().url(putUrl).method(putMethod, putBody).header("Content-Type", contentType)
        if (putHeaders != null) {
            val keys = putHeaders.keys()
            while (keys.hasNext()) { val k = keys.next(); putReqB.header(k, putHeaders.optString(k, "")) }
        }
        storageClient.newCall(putReqB.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw MtV2Exception("Upload ke storage gagal HTTP ${resp.code}", resp.code, JSONObject())
        }

        // 3) submit translate (English scanlation -> Indonesian)
        checkInterrupted()
        val submitBody = JSONObject().apply {
            put("upload_id", uploadId)
            put("files", JSONArray().put(JSONObject().apply { put("index", index); put("upload_intent", uploadIntent) }))
            put("target_language", "id")
            // The manga model rejects requests without a source language; FuwaStream manga sources are English.
            put("source_language", "en")
            put("model", "manga")
            put("translator", "auto")
        }
        val submit = jsonRequest(apiClient, MT_BASE + MT_SUBMIT, apiKey, submitBody)
        val subData = submit.optJSONObject("data") ?: submit
        val batchId = subData.optString("batch_id", subData.optString("batchId", subData.optString("task_id", subData.optString("id", ""))))
        if (batchId.isBlank()) throw MtV2Exception("batch_id tidak ditemukan", 0, submit)
        // MangaTranslate charges the image when the task is accepted, not when the result is downloaded.
        recordCreditUsed(apiKey)

        // 4) poll task
        val taskPath = MT_TASK_TMPL.replace("{batch_id}", batchId)
        val deadline = System.currentTimeMillis() + 8 * 60_000L
        var lastStatus = ""
        var consecutivePollErrors = 0
        while (System.currentTimeMillis() < deadline) {
            checkInterrupted()
            val task = try {
                getJson(apiClient, MT_BASE + taskPath, apiKey).also { consecutivePollErrors = 0 }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                val transient = e is IOException || (e is MtV2Exception && (e.status == 429 || e.status >= 500))
                if (!transient) throw e
                consecutivePollErrors++
                if (consecutivePollErrors >= 5) {
                    throw TranslatorBusyException("Polling hasil tidak stabil setelah $consecutivePollErrors percobaan", e)
                }
                interruptibleSleep((1_000L * consecutivePollErrors).coerceAtMost(5_000L))
                continue
            }
            // The task endpoint returns either {data:{items:[...]}} or {data:[...]}; the array
            // shape is what mangatranslate.com currently serves. Without handling it the status
            // is never observed and polling waits until the timeout even though pages are done.
            val dataItems = task.optJSONArray("data")
            val td = task.optJSONObject("data")
            val items = dataItems ?: td?.optJSONArray("items")
            val taskStatus = td?.optString("task_status", td.optString("status", ""))?.lowercase().orEmpty()
            if (taskStatus.isNotBlank()) lastStatus = taskStatus
            val doneStates = setOf("completed", "failed", "canceled", "cancelled", "error")
            val allDone = if (taskStatus.isNotBlank()) taskStatus in doneStates else {
                items != null && items.length() > 0 && (0 until items.length()).all { i -> items.optJSONObject(i)?.optString("status", "")?.lowercase() in doneStates }
            }
            if (allDone) {
                var translatedUrl: String? = null
                var itemErr = ""
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val st = it.optString("status", "").lowercase()
                        if (st == "completed") {
                            translatedUrl = it.optString("translated_url", it.optString("output_file_path", it.optString("translatedUrl", ""))).ifBlank { null }
                            if (translatedUrl != null) break
                        } else if (st in setOf("failed", "error", "canceled", "cancelled")) {
                            itemErr = it.optString("error", it.optString("message", "")).ifBlank { itemErr }
                        }
                    }
                }
                if (translatedUrl.isNullOrBlank()) {
                    val msg = itemErr.ifBlank { "Task selesai tanpa hasil ($lastStatus)" }
                    if (isCreditOrBusyMessage(msg) || isCreditOrBusyMessage(lastStatus)) throw TranslatorBusyException(msg)
                    // A single bad/blank page must not cancel the remaining chapter. Record it as
                    // a page failure; the worker will finish other pages and retry only this slot.
                    throw MtV2Exception(msg, 0, task)
                }
                // 5) download result
                return downloadTranslatedResult(translatedUrl)
            }
            interruptibleSleep(1000)
        }
        throw TranslatorBusyException("Timeout polling task V2 ($lastStatus)")
    }

    // -------- account provisioning (with proxy rotation for mangatranslate, direct for akunlama) --------
    private fun provisionAccountWithCreditsLocked(): String? {
        // called under provisionLock; re-check if another thread already provisioned
        pickKnownGoodApiKey(loadStore())?.let { return it }

        val proxies = buildList<String?> {
            if (!triedDirectProvision) {
                add(null)
                triedDirectProvision = true
            }
            if (provisionProxies.isNotEmpty()) {
                repeat(minOf(provisionProxies.size, 16)) { offset ->
                    add(provisionProxies[(provisionProxyIndex + offset) % provisionProxies.size])
                }
            }
        }
        val maxAttempts = proxies.size
        val overallDeadline = System.currentTimeMillis() + 180_000L
        for (i in 0 until maxAttempts) {
            checkInterrupted()
            if (System.currentTimeMillis() > overallDeadline) break
            val proxyUrl = proxies[i]
            if (proxyUrl != null && provisionProxies.isNotEmpty()) {
                provisionProxyIndex = (provisionProxyIndex + 1) % provisionProxies.size
            }
            val mtClient = clientForProxy(proxyUrl)
            val attemptDeadline = minOf(overallDeadline, System.currentTimeMillis() + 55_000L)
            val result = runCatching { provisionOnce(mtClient, directClient, proxyUrl, attemptDeadline) }
            val pat = result.getOrNull()
            if (pat != null) return pat
            val err = result.exceptionOrNull() ?: continue
            android.util.Log.w("MangaTranslate", "provision attempt ${i + 1} failed: ${err.message}")
        }
        return null
    }

    private fun clientForProxy(proxyUrl: String?): OkHttpClient {
        if (proxyUrl != null) {
            return proxyClients.getOrPut(proxyUrl) { buildProvisionClient(proxyUrl) }
        }
        return buildProvisionClient(null)
    }

    private fun buildProvisionClient(proxyUrl: String?): OkHttpClient {
        val builder = httpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
        if (proxyUrl != null) parseHttpProxy(proxyUrl)?.let { builder.proxy(it) }
        return builder.build()
    }

    private fun provisionOnce(mtClient: OkHttpClient, akunlamaClient: OkHttpClient, proxyUrl: String?, deadlineMs: Long): String? {
        if (System.currentTimeMillis() > deadlineMs) return null
        val email = "mt_${randomHex(6)}@$DEFAULT_REG_DOMAIN"
        val password = "Mt!${randomHex(8)}Aa1"
        val session = MtSession(mtClient, akunlamaClient)
        session.fetchPage("/")
        if (System.currentTimeMillis() > deadlineMs) return null
        session.register(email, password)
        if (System.currentTimeMillis() > deadlineMs) return null
        // verify via akunlama inbox (best effort, direct client) and refresh the session afterwards
        runCatching { session.verifyAkunlama(email, password, minOf(deadlineMs, System.currentTimeMillis() + 30_000L)) }
            .onFailure { android.util.Log.w("MangaTranslate", "verify failed: ${it.message}") }
        if (System.currentTimeMillis() > deadlineMs) return null
        val checkinStatus = runCatching { session.getCheckinStatus() }.getOrNull()
        val statusData = checkinStatus?.optJSONObject("data") ?: checkinStatus
        val canCheckin = statusData?.optBoolean("can_checkin", false) == true
        val todayChecked = statusData?.optBoolean("today_checked", false) == true
        if (canCheckin || (statusData == null && !todayChecked)) {
            runCatching { session.performCheckin() }
                .onFailure { android.util.Log.w("MangaTranslate", "check-in failed: ${it.message}") }
        }
        if (System.currentTimeMillis() > deadlineMs) return null
        val pat = runCatching { session.createApiKey() }.getOrNull().orEmpty()
        if (pat.isBlank()) return null
        if (System.currentTimeMillis() > deadlineMs) return null
        // check credits (must be > 0)
        val total = runCatching { session.getCreditsTotal() }.getOrNull() ?: runCatching { getCreditsWithPat(pat, mtClient) }.getOrNull()
        val credits = total ?: -1.0
        if (credits <= 0.0) return null
        val store = loadStore()
        val arr = accountsArray(store)
        // remove existing with same email and 0-credit junk
        val cleaned = JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("email") == email) continue
            val c = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", Double.NaN) else Double.NaN
            if (!c.isNaN() && c <= 0.0) continue
            cleaned.put(a)
        }
        val acc = JSONObject().apply {
            put("email", email); put("password", password); put("apiKey", pat)
            put("totalCredits", credits)
            put("createdAt", System.currentTimeMillis())
            if (proxyUrl != null) put("proxyUrl", proxyUrl)
        }
        cleaned.put(acc)
        store.put("accounts", cleaned)
        saveStore(store)
        return pat
    }

    private fun randomHex(n: Int): String {
        val bytes = ByteArray(n)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private class MtV2Exception(message: String, val status: Int, val body: JSONObject) : Exception(message)

private class MtSession(
    private val http: OkHttpClient,          // for mangatranslate.com (may be proxied)
    private val directHttp: OkHttpClient,    // for akunlama/external verify (never proxied)
) {
    private val cookies = linkedMapOf<String, String>()
    private val fingerprint = run {
        val s = "android|${android.os.Build.MODEL}|mangatranslate"
        "fp_${fnv32(s)}${fnv32(s.reversed())}${fnv32(s.take(8))}"
    }
    private var csrf: String = randomHex(16)
    private var sc: String = randomHex(18)

    init { cookies["csrftoken"] = csrf; cookies["sc"] = sc }

    private fun randomHex(n: Int): String { val b = ByteArray(n); SecureRandom().nextBytes(b); return b.joinToString("") { "%02x".format(it) } }
    private fun fnv32(str: String): String {
        var t = 2166136261L
        for (ch in str) { t = t xor ch.code.toLong(); t = (t + (t shl 1) + (t shl 4) + (t shl 7) + (t shl 8) + (t shl 24)) and 0xFFFFFFFFL }
        return String.format("%08x", t)
    }
    private fun fnv1a64Hex(input: String): String {
        var t = BigInteger("14695981039346656037")
        val r = BigInteger("1099511628211")
        val mask = BigInteger("FFFFFFFFFFFFFFFF", 16)
        for (ch in input) { t = t.xor(BigInteger.valueOf(ch.code.toLong())).multiply(r).and(mask) }
        val hex = t.toString(16)
        return ("0000000000000000" + hex).takeLast(16)
    }
    private fun sessionProof(path: String, method: String): String {
        val minute = System.currentTimeMillis() / 60000L
        return fnv1a64Hex("$sc|$path|${method.uppercase()}|$minute")
    }
    private fun cookieHeader(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    private fun absorbCookies(resp: okhttp3.Response) {
        for (h in resp.headers("Set-Cookie")) {
            val first = h.substringBefore(';')
            val eq = first.indexOf('=')
            if (eq > 0) {
                val name = first.substring(0, eq).trim()
                val value = first.substring(eq + 1).trim()
                if (value.isEmpty() || (h.contains("expires=", true) && h.contains("1970"))) cookies.remove(name) else cookies[name] = value
            }
        }
        cookies["csrftoken"]?.let { csrf = it }
        cookies["sc"]?.let { sc = it }
    }

    fun fetchPage(path: String) {
        val req = Request.Builder().url(MT_BASE + path).get()
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .header("X-Browser-Fingerprint", fingerprint)
            .header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(path, "GET"))
            .header("Cookie", cookieHeader())
            .build()
        runCatching { http.newCall(req).execute().use { absorbCookies(it) } }
    }

    private fun postJson(path: String, json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(MT_BASE + path).post(body)
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", MT_BASE)
            .header("Referer", "$MT_BASE/")
            .header("Content-Type", "application/json")
            .header("X-Browser-Fingerprint", fingerprint)
            .header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(path, "POST"))
            .header("Cookie", cookieHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            absorbCookies(resp)
            val text = resp.body?.string().orEmpty()
            val out = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) throw MtV2Exception(out.optString("message", "HTTP ${resp.code}"), resp.code, out)
            return out
        }
    }

    fun register(email: String, password: String) {
        val payload = JSONObject().apply {
            put("email", email); put("password", password)
            put("accept_privacy", true); put("accept_refund", true); put("accept_terms", true); put("lang", "en")
        }
        postJson(MT_REGISTER, payload)
    }

    fun login(email: String, password: String) {
        val payload = JSONObject().apply {
            put("email", email); put("password", password)
            put("accept_privacy", true); put("accept_refund", true); put("accept_terms", true); put("lang", "en")
        }
        postJson(MT_LOGIN, payload)
    }

    fun createApiKey(): String {
        val out = postJson(MT_CREATE_API_KEY, JSONObject().put("name", "app-${System.currentTimeMillis()}"))
        val data = out.optJSONObject("data")
        return sequenceOf(
            out.optString("token"),
            out.optString("api_key"),
            out.optString("key"),
            out.optString("plaintext_token"),
            data?.optString("token").orEmpty(),
            data?.optString("api_key").orEmpty(),
            data?.optString("key").orEmpty(),
            data?.optString("plaintext_token").orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    fun getCheckinStatus(): JSONObject? {
        val req = Request.Builder().url(MT_BASE + MT_CHECKIN_STATUS).get()
            .header("User-Agent", UA).header("Accept", "application/json")
            .header("Origin", MT_BASE).header("Referer", "$MT_BASE/")
            .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(MT_CHECKIN_STATUS, "GET")).header("Cookie", cookieHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            absorbCookies(resp)
            if (!resp.isSuccessful) return null
            return runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
        }
    }

    fun getCreditsTotal(): Double? {
        val req = Request.Builder().url(MT_BASE + MT_CREDITS_V1).get()
            .header("User-Agent", UA).header("Accept", "application/json")
            .header("Origin", MT_BASE).header("Referer", "$MT_BASE/")
            .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(MT_CREDITS_V1, "GET")).header("Cookie", cookieHeader())
            .build()
        http.newCall(req).execute().use { resp ->
            absorbCookies(resp)
            val text = resp.body?.string().orEmpty()
            val j = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrNull() ?: return null
            val d = j.optJSONObject("data") ?: j
            val v = when {
                d.has("total_credits") -> d.optDouble("total_credits", Double.NaN)
                d.has("totalCredits") -> d.optDouble("totalCredits", Double.NaN)
                else -> Double.NaN
            }
            return if (v.isNaN()) null else v
        }
    }

    fun performCheckin() {
        val path = MT_PERFORM_CHECKIN
        val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(MT_BASE + path).post(body)
            .header("User-Agent", UA).header("Accept", "application/json").header("Content-Type", "application/json")
            .header("Origin", MT_BASE).header("Referer", "$MT_BASE/")
            .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(path, "POST")).header("Cookie", cookieHeader())
            .build()
        http.newCall(req).execute().use { resp -> absorbCookies(resp) }
    }

    fun verifyAkunlama(email: String, password: String, deadlineMs: Long) {
        // Already verified? Nothing to do.
        val initial = runCatching { getCheckinStatus() }.getOrNull()
        val initialData = initial?.optJSONObject("data") ?: initial
        if (initialData?.optBoolean("email_verified", false) == true) return

        // best-effort: request resend, poll inbox briefly for verify link, hit it.
        runCatching {
            val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(MT_BASE + MT_RESEND_VERIFICATION).post(body)
                .header("User-Agent", UA).header("Accept", "application/json").header("Content-Type", "application/json")
                .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
                .header("X-Session-Proof", sessionProof(MT_RESEND_VERIFICATION, "POST")).header("Cookie", cookieHeader())
                .build()
            http.newCall(req).execute().use { absorbCookies(it) }
        }
        val seen = HashSet<String>()
        while (System.currentTimeMillis() < deadlineMs) {
            checkInterrupted()
            val msgs = runCatching { akunlamaList(email) }.getOrDefault(emptyList())
            for (m in msgs) {
                val key = m.optJSONObject("storage")?.optString("key", "").orEmpty()
                if (key.isBlank() || !seen.add(key)) continue
                val region = m.optJSONObject("storage")?.optString("region", "").orEmpty()
                val html = runCatching { akunlamaHtml(region, key) }.getOrDefault("")
                val match = Regex("https://www\\.mangatranslate\\.com/verify-email/\\?token=[^\"'<>\\s]+").find(html)?.value
                if (match != null) {
                    // Consume the link with the direct client: akunlama/base verification pages
                    // must never go through the (possibly dead) MangaTranslate proxies.
                    runCatching {
                        val vreq = Request.Builder().url(match).get().header("User-Agent", UA).build()
                        directHttp.newCall(vreq).execute().close()
                    }
                    // Verification changes account state; refresh the authenticated session.
                    runCatching { login(email, password) }
                    runCatching { fetchPage("/") }
                    return
                }
            }
            if (System.currentTimeMillis() + 2000 > deadlineMs) break
            interruptibleSleep(2000)
        }
    }

    private fun akunlamaList(email: String): List<JSONObject> {
        val url = "$AKUNLAMA_API/list?recipient=${URLEncoder.encode(email, "UTF-8")}"
        val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
        directHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = runCatching { JSONArray(resp.body?.string().orEmpty()) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
    }
    private fun akunlamaHtml(region: String, key: String): String {
        val url = "$AKUNLAMA_API/getHtml?region=${URLEncoder.encode(region, "UTF-8")}&key=${URLEncoder.encode(key, "UTF-8")}"
        val req = Request.Builder().url(url).get().header("Accept", "text/html,*/*").build()
        directHttp.newCall(req).execute().use { resp -> return if (resp.isSuccessful) resp.body?.string().orEmpty() else "" }
    }

    companion object { private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36" }
}
