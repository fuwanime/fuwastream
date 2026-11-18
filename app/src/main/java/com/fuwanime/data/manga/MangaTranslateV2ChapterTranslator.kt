package com.fuwanime.data.manga

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private const val MT_BASE = "https://www.mangatranslate.com"
private const val MT_PREPARE = "/api/v2/api/translate/uploads"
private const val MT_SUBMIT = "/api/v2/api/translate"
private const val MT_TASK_TMPL = "/api/v2/api/tasks/{batch_id}"
private const val MT_CREDITS_V1 = "/api/v1/manga/credits/current"
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
    "http://180.190.185.211:9999",
    "http://151.158.133.163:8080",
    "http://103.171.182.80:8080",
    "http://212.252.73.83:8080",
    "http://110.34.1.178:7777",
    "http://200.48.64.107:999",
    "http://139.59.59.122:8118",
    "http://35.43.85.195:27951",
    "http://186.148.180.131:999",
    "http://1.4.141.44:80",
    "http://45.65.137.218:999",
    "http://200.59.191.27:999",
    "http://45.230.170.30:999",
    "http://112.207.166.195:8082",
    "http://190.119.185.156:999",
    "http://45.174.243.232:999",
    "http://120.28.194.248:8282",
    "http://49.0.26.97:8080",
    "http://216.152.149.246:80",
    "http://45.163.196.50:8083",
    "http://54.36.150.233:3128",
    "http://3.26.199.107:42984",
    "http://49.229.100.235:8080",
    "http://180.180.175.11:8080",
    "http://103.176.96.123:8082",
    "http://202.148.13.182:8080",
    "http://103.180.118.122:1080",
    "http://182.52.113.71:80",
    "http://103.137.91.250:8080",
    "http://194.124.36.14:8080",
    "http://103.167.15.188:3128",
    "http://180.191.142.206:8081",
    "http://101.51.121.149:80",
    "http://163.223.150.21:8080",
    "http://185.142.156.203:80",
    "http://138.118.107.29:999",
    "http://190.12.150.244:999",
    "http://185.153.231.35:3129",
    "http://110.74.206.40:8181",
    "http://113.192.46.66:8080",
    "http://202.59.201.175:8080",
    "http://45.43.60.220:8080",
    "http://58.69.250.43:8082",
    "http://180.180.151.239:8080",
    "http://181.78.94.189:999",
    "http://77.242.21.133:8080",
    "http://181.79.84.254:999",
    "http://191.160.36.7:8080",
    "http://103.111.116.233:81",
    "http://45.198.8.222:8080",
    "http://41.78.38.197:8005",
    "http://102.213.179.194:8081",
    "http://196.251.195.146:8081",
    "http://124.106.39.123:8083",
    "http://150.107.104.24:1111",
    "http://131.221.65.109:8090",
    "http://160.19.145.103:3127",
    "http://190.111.218.142:999",
    "http://192.81.249.2:999",
    "http://164.92.105.30:1080",
    "http://103.176.96.180:8090",
    "http://177.234.217.84:999",
    "http://61.91.162.126:8080",
    "http://181.119.111.59:999",
    "http://35.43.246.3:23571",
    "http://201.150.116.3:999",
    "http://182.48.66.154:8080",
    "http://38.76.138.129:999",
    "http://202.51.196.230:8080",
    "http://144.79.180.61:8080",
    "http://180.191.142.188:8080",
    "http://103.133.25.247:8080",
    "http://45.179.200.118:999",
    "http://161.22.33.152:999",
    "http://45.65.138.48:999",
    "http://27.147.156.174:12051",
    "http://31.14.124.45:8080",
    "http://38.95.88.58:999",
    "http://165.16.30.101:9999",
    "http://190.102.146.98:10101",
    "http://185.238.238.29:58080",
    "http://41.79.9.229:8080",
    "http://198.15.30.239:8080",
    "http://77.42.95.221:8888",
    "http://103.18.78.229:8080",
    "http://211.188.53.248:80",
    "http://124.105.39.244:8081",
    "http://103.164.171.210:8080",
    "http://187.190.127.212:80",
    "http://152.42.170.187:9090",
    "http://58.84.16.214:1231",
    "http://190.7.138.78:8080",
    "http://189.51.168.165:999",
    "http://103.66.47.9:8080",
    "http://65.108.159.129:3128",
    "http://103.189.223.19:7557",
    "http://103.147.230.177:8090",
    "http://216.152.152.44:80",
    "http://116.204.166.148:8080",
    "http://181.119.109.10:999",
    "http://189.51.168.164:999",
    "http://85.132.108.42:3128",
    "http://138.121.113.12:8787",
    "http://130.250.199.73:999",
    "http://91.203.61.52:6789",
    "http://103.135.48.30:8089",
    "http://89.43.135.9:8080",
    "http://184.70.113.34:3128",
    "http://38.188.63.169:8080",
    "http://45.65.226.172:999",
    "http://190.94.213.80:999",
    "http://217.142.139.207:8085",
    "http://203.115.123.163:1256",
    "http://103.230.48.110:8082",
    "http://122.54.119.79:8080",
    "http://43.99.60.244:8089",
    "http://163.223.78.163:8080",
    "http://121.101.129.131:8080",
    "http://103.151.223.168:8080",
    "http://196.251.195.146:8084",
    "http://181.78.94.157:999",
    "http://160.19.16.6:8182",
    "http://207.177.122.144:8080",
    "http://187.44.188.238:4040",
    "http://113.192.48.159:8080",
    "http://128.140.113.110:5678",
    "http://54.198.140.56:8049",
    "http://181.37.240.89:999",
    "http://34.220.80.147:34889",
    "http://114.130.153.46:58080",
    "http://195.175.29.38:9090",
    "http://103.89.27.32:8080",
    "http://185.238.238.21:58080",
    "http://103.184.62.10:8080",
    "http://203.150.128.222:8080",
    "http://123.121.113.161:8888",
    "http://91.150.189.122:30389",
    "http://45.168.236.54:3128",
)

private fun parseHttpProxy(url: String): Proxy? {
    return try {
        val u = java.net.URI(url)
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 8080
        Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    } catch (_: Exception) { null }
}

private fun isRetryableProvisionError(e: Throwable): Boolean {
    if (e is MtV2Exception) {
        val detail = e.body.optJSONObject("detail")
        val reason = detail?.optString("reason_code", "") ?: ""
        if (e.status == 429 && reason == "window_limit_exceeded") return true
        if (e.status == 429) return true
        val msg = (e.message ?: "").lowercase()
        if ("window_limit_exceeded" in msg || "rate limit" in msg || "too many" in msg) return true
    }
    val msg = (e.message ?: "").lowercase()
    return msg.contains("timeout") || msg.contains("timed out") || msg.contains("unable to resolve") ||
        msg.contains("failed to connect") || msg.contains("connection") || msg.contains("socket") ||
        msg.contains("proxy") || msg.contains("tunnel") || msg.contains("unreachable") ||
        msg.contains("refused") || msg.contains("reset") || msg.contains("network")
}

/** MangaTranslate.com V2 API as primary chapter translator. Falls back by throwing [TranslatorBusyException] when busy/unavailable. */
class MangaTranslateV2ChapterTranslator(
    context: Context,
    private val httpClient: OkHttpClient,
) : MangaChapterTranslator {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "mangatranslate_accounts.json")

    override fun chapterDir(cacheDir: File, chapterId: String): File =
        File(cacheDir, "mangatranslate/${chapterId.replace(':', '_').replace('/', '_')}")

    override fun cachedChapterPages(cacheDir: File, chapterId: String, pageCount: Int): List<String>? {
        if (pageCount <= 0) return null
        val dir = chapterDir(cacheDir, chapterId)
        val files = (0 until pageCount).map { pageFile(dir, it) }
        return if (files.all { it.exists() && it.length() > 0 }) files.map { "file://${it.absolutePath}" } else null
    }

    private fun pageFile(dir: File, index: Int): File = File(dir, "p_${index.toString().padStart(4, '0')}.png")

    override suspend fun translateChapter(
        chapterId: String,
        pageUrls: List<String>,
        cacheDir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val outDir = chapterDir(cacheDir, chapterId)
        outDir.mkdirs()
        val outputFiles = pageUrls.indices.map { pageFile(outDir, it) }
        if (outputFiles.isNotEmpty() && outputFiles.all { it.exists() && it.length() > 0 }) {
            onProgress(outputFiles.size, outputFiles.size)
            return@withContext outputFiles.map { "file://${it.absolutePath}" }
        }

        val pending = pageUrls.indices.filterNot { outputFiles[it].exists() && outputFiles[it].length() > 0 }.toMutableList()
        val doneCount = AtomicInteger(pageUrls.size - pending.size)
        if (doneCount.get() > 0) onProgress(doneCount.get(), pageUrls.size)

        // Ensure we have at least one usable API key/account context up front; if not, primary is busy/unavailable.
        ensureUsableKey()

        val concurrency = min(2, pending.size.coerceAtLeast(1))
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            pending.map { i ->
                async {
                    semaphore.withPermit {
                        val bytes = downloadPage(pageUrls[i])
                        val translated = translateOne(bytes)
                        saveAsPng(translated, outputFiles[i])
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
        var last: IOException? = null
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
                last = if (e is IOException) e else IOException(e.message ?: "Gagal mengunduh halaman", e)
            }
        }
        throw IOException("Gagal mengunduh halaman: ${last?.message ?: "unknown"}", last)
    }

    private fun saveAsPng(bytes: ByteArray, out: File) {
        // MangaTranslate returns translated image (usually JPG). Decode and save as PNG for cache consistency.
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp != null) {
            try {
                out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bmp.recycle()
            }
        } else {
            out.writeBytes(bytes)
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

    @Synchronized
    private fun accountsArray(store: JSONObject): JSONArray {
        if (!store.has("accounts")) store.put("accounts", JSONArray())
        return store.optJSONArray("accounts") ?: JSONArray().also { store.put("accounts", it) }
    }

    private fun pickApiKey(store: JSONObject): String? {
        val arr = accountsArray(store)
        for (i in arr.length() - 1 downTo 0) {
            val a = arr.optJSONObject(i) ?: continue
            val key = a.optString("apiKey", a.optString("api_key", ""))
            val credits = if (a.has("totalCredits") && !a.isNull("totalCredits")) a.optDouble("totalCredits", -1.0) else -1.0
            if (key.isNotBlank() && (credits > 0.0 || credits < 0.0)) return key
        }
        return null
    }

    private fun markAccountNoCredits(apiKey: String) {
        val store = loadStore()
        val arr = accountsArray(store)
        var changed = false
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val key = a.optString("apiKey", a.optString("api_key", ""))
            if (key == apiKey) { a.put("totalCredits", 0); changed = true }
        }
        if (changed) saveStore(store)
    }

    private fun ensureUsableKey(): String {
        val store = loadStore()
        pickApiKey(store)?.let { return it }
        // No known-good PAT: try to provision a new account+PAT (best effort). If it fails, primary is busy/unavailable.
        val created = runCatching { provisionAccountWithCredits() }.getOrNull()
        if (created != null) return created
        throw TranslatorBusyException("MangaTranslate unavailable: no API key/credits")
    }

    private fun isCreditOrBusyMessage(msg: String?): Boolean {
        val m = (msg ?: "").lowercase()
        return "insufficient credit" in m || "insufficient_credits" in m || "no enough credit" in m ||
            ("credit" in m && ("not enough" in m || "insufficient" in m || "exhaust" in m || "habis" in m || "balance" in m)) ||
            ("quota" in m && ("exceed" in m || "exhaust" in m || "habis" in m || "insufficient" in m)) ||
            "window_limit_exceeded" in m || "rate limit" in m || "too many requests" in m ||
            "antrian penuh" in m || "queue" in m && "full" in m
    }

    private fun translateOne(imageBytes: ByteArray): ByteArray {
        var attempts = 0
        while (true) {
            val apiKey = ensureUsableKey()
            attempts++
            try {
                return translateOneWithKey(apiKey, imageBytes)
            } catch (e: TranslatorBusyException) {
                // mark and try to rotate/provision once
                markAccountNoCredits(apiKey)
                val rotated = runCatching { provisionAccountWithCredits() }.getOrNull()
                if (rotated != null && attempts < 3) continue
                throw e
            } catch (e: MtV2Exception) {
                if (isCreditOrBusyMessage(e.message) || e.status == 429 || e.status == 402) {
                    markAccountNoCredits(apiKey)
                    val rotated = runCatching { provisionAccountWithCredits() }.getOrNull()
                    if (rotated != null && attempts < 3) continue
                    throw TranslatorBusyException(e.message, e)
                }
                throw e
            }
        }
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

    private fun jsonRequest(url: String, apiKey: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val b = Request.Builder().url(url).post(reqBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        httpClient.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optString("message", json.optString("detail", json.optString("error", ""))).ifBlank { "HTTP ${resp.code}" }
                throw MtV2Exception(msg, resp.code, json)
            }
            return json
        }
    }

    private fun getJson(url: String, apiKey: String): JSONObject {
        val b = Request.Builder().url(url).get()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
        httpClient.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(if (text.isBlank()) "{}" else text) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optString("message", json.optString("detail", json.optString("error", ""))).ifBlank { "HTTP ${resp.code}" }
                throw MtV2Exception(msg, resp.code, json)
            }
            return json
        }
    }

    private fun translateOneWithKey(apiKey: String, imageBytes: ByteArray): ByteArray {
        val contentType = detectMime(imageBytes)
        val filename = "page.${if (contentType == "image/png") "png" else "jpg"}"
        val sha = sha256Hex(imageBytes)

        // 1) prepare upload
        val prepareBody = JSONObject().put("files", JSONArray().put(JSONObject().apply {
            put("filename", filename)
            put("content_type", contentType)
            put("size", imageBytes.size)
            put("content_sha256", sha)
        }))
        val prepare = jsonRequest(MT_BASE + MT_PREPARE, apiKey, prepareBody, mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
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
        val putBody = imageBytes.toRequestBody(contentType.toMediaType())
        val putReqB = Request.Builder().url(putUrl).method(putMethod, putBody).header("Content-Type", contentType)
        if (putHeaders != null) {
            val keys = putHeaders.keys()
            while (keys.hasNext()) { val k = keys.next(); putReqB.header(k, putHeaders.optString(k, "")) }
        }
        httpClient.newCall(putReqB.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw MtV2Exception("Upload ke storage gagal HTTP ${resp.code}", resp.code, JSONObject())
        }

        // 3) submit translate (EN->ID for manga reading, matching app behavior; allow auto source)
        val submitBody = JSONObject().apply {
            put("upload_id", uploadId)
            put("files", JSONArray().put(JSONObject().apply { put("index", index); put("upload_intent", uploadIntent) }))
            put("target_language", "id")
            // source auto-detected (omit source_language)
            put("model", "manga")
            put("translator", "auto")
        }
        val submit = jsonRequest(MT_BASE + MT_SUBMIT, apiKey, submitBody)
        val subData = submit.optJSONObject("data") ?: submit
        val batchId = subData.optString("batch_id", subData.optString("batchId", subData.optString("task_id", subData.optString("id", ""))))
        if (batchId.isBlank()) throw MtV2Exception("batch_id tidak ditemukan", 0, submit)

        // 4) poll task
        val taskPath = MT_TASK_TMPL.replace("{batch_id}", batchId)
        val deadline = System.currentTimeMillis() + 10 * 60_000L
        var lastStatus = ""
        while (System.currentTimeMillis() < deadline) {
            val task = getJson(MT_BASE + taskPath, apiKey)
            val td = task.optJSONObject("data") ?: task
            val taskStatus = td.optString("task_status", td.optString("status", "")).lowercase()
            val items = td.optJSONArray("items")
            if (taskStatus.isNotBlank()) lastStatus = taskStatus
            val doneStates = setOf("completed", "failed", "canceled", "cancelled", "error")
            val allDone = if (taskStatus.isNotBlank()) taskStatus in doneStates else {
                items != null && items.length() > 0 && (0 until items.length()).all { i -> items.optJSONObject(i)?.optString("status", "")?.lowercase() in doneStates }
            }
            if (allDone) {
                var translatedUrl: String? = null
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val st = it.optString("status", "").lowercase()
                        if (st == "completed") {
                            translatedUrl = it.optString("translated_url", it.optString("output_file_path", it.optString("translatedUrl", ""))).ifBlank { null }
                            if (translatedUrl != null) break
                        }
                    }
                }
                if (translatedUrl.isNullOrBlank()) {
                    val err = if (items != null && items.length() > 0) items.optJSONObject(0)?.optString("error", "").orEmpty() else ""
                    val msg = if (err.isNotBlank()) err else "Task selesai tanpa hasil ($lastStatus)"
                    if (isCreditOrBusyMessage(msg) || isCreditOrBusyMessage(lastStatus)) throw TranslatorBusyException(msg)
                    throw MtV2Exception(msg, 0, task)
                }
                // 5) download result
                val resultUrl = translatedUrl!!
                val dl = Request.Builder().url(resultUrl).get().header("User-Agent", "Mozilla/5.0").build()
                httpClient.newCall(dl).execute().use { resp ->
                    if (!resp.isSuccessful) throw MtV2Exception("Download hasil gagal HTTP ${resp.code}", resp.code, JSONObject())
                    return resp.body?.bytes() ?: throw MtV2Exception("Hasil kosong", 0, JSONObject())
                }
            }
            Thread.sleep(2000)
        }
        throw MtV2Exception("Timeout polling task V2 ($lastStatus)", 0, JSONObject())
    }

    // -------- best-effort account provisioning (simplified port of gist workflow, with proxy rotation) --------
    private fun provisionAccountWithCredits(): String? {
        // Try direct first, then rotate through active proxies on rate-limit/connection issues (as in gist workflow).
        val proxies = listOf<String?>(null) + MT_DEFAULT_PROXIES.shuffled()
        val maxAttempts = minOf(proxies.size, 30)
        for (i in 0 until maxAttempts) {
            val proxyUrl = proxies[i]
            val client = clientForProxy(proxyUrl)
            val result = runCatching { provisionOnce(client) }
            val pat = result.getOrNull()
            if (pat != null) return pat
            val err = result.exceptionOrNull() ?: continue
            if (!isRetryableProvisionError(err)) {
                // Non-retryable (e.g. hard failure) - still try a few more proxies in case of IP/account issues
                if (i >= 4) return null
            }
            // else retry with next proxy
        }
        return null
    }

    private fun clientForProxy(proxyUrl: String?): OkHttpClient {
        if (proxyUrl == null) return httpClient
        val proxy = parseHttpProxy(proxyUrl) ?: return httpClient
        return httpClient.newBuilder()
            .proxy(proxy)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun provisionOnce(client: OkHttpClient): String? {
        val email = "mt_${randomHex(6)}@$DEFAULT_REG_DOMAIN"
        val password = "Mt!${randomHex(8)}Aa1"
        val session = MtSession(client)
        session.fetchPage("/")
        session.register(email, password)
        // verify via akunlama inbox (best effort, short wait)
        runCatching { session.verifyAkunlama(email) }
        runCatching { session.performCheckin() }
        val pat = runCatching { session.createApiKey() }.getOrNull().orEmpty()
        if (pat.isBlank()) return null
        // check credits
        val credits = runCatching { session.getCreditsTotal() }.getOrNull()
        val total = credits ?: -1.0
        val store = loadStore()
        val arr = accountsArray(store)
        val acc = JSONObject().apply {
            put("email", email); put("password", password); put("apiKey", pat)
            if (credits != null) put("totalCredits", credits)
            put("createdAt", System.currentTimeMillis())
        }
        arr.put(acc)
        saveStore(store)
        return if (total > 0.0 || total < 0.0) pat else null
    }

    private fun randomHex(n: Int): String {
        val bytes = ByteArray(n)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private class MtV2Exception(message: String, val status: Int, val body: JSONObject) : Exception(message)

private class MtSession(private val http: OkHttpClient) {
    private val cookies = linkedMapOf<String, String>()
    private val fingerprint = run {
        val s = "android|${android.os.Build.MODEL}|mangatranslate"
        "fp_${fnv32(s)}${fnv32(s.reversed())}${fnv32(s.take(8))}"
    }
    private var csrf: String = randomHex(16)
    private var sc: String = randomHex(18)

    init { cookies["csrftoken"] = csrf; cookies["sc"] = sc }

    private fun randomHex(n: Int): String { val b = ByteArray(n); java.security.SecureRandom().nextBytes(b); return b.joinToString("") { "%02x".format(it) } }
    private fun fnv32(str: String): String {
        var t = 2166136261L
        for (ch in str) { t = t xor ch.code.toLong(); t = (t + (t shl 1) + (t shl 4) + (t shl 7) + (t shl 8) + (t shl 24)) and 0xFFFFFFFFL }
        return String.format("%08x", t)
    }
    private fun fnv1a64Hex(input: String): String {
        var t = java.math.BigInteger("14695981039346656037")
        val r = java.math.BigInteger("1099511628211")
        val mask = java.math.BigInteger("FFFFFFFFFFFFFFFF", 16)
        for (ch in input) { t = t.xor(java.math.BigInteger.valueOf(ch.code.toLong())).multiply(r).and(mask) }
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
                if (value.isEmpty() || h.contains("expires=", true) && h.contains("1970")) cookies.remove(name) else cookies[name] = value
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
        return out.optString("token", out.optString("api_key", out.optString("key", out.optJSONObject("data")?.optString("token", "") ?: "")))
    }

    fun getCreditsTotal(): Double? {
        val req = Request.Builder().url(MT_BASE + MT_CREDITS_V1).get()
            .header("User-Agent", UA).header("Accept", "application/json")
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
            .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
            .header("X-Session-Proof", sessionProof(path, "POST")).header("Cookie", cookieHeader())
            .build()
        http.newCall(req).execute().use { resp -> absorbCookies(resp) }
    }

    fun verifyAkunlama(email: String) {
        // best-effort: request resend, poll inbox for verify link, hit it
        runCatching {
            val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(MT_BASE + MT_RESEND_VERIFICATION).post(body)
                .header("User-Agent", UA).header("Accept", "application/json").header("Content-Type", "application/json")
                .header("X-Browser-Fingerprint", fingerprint).header("X-CSRF-Token", csrf)
                .header("X-Session-Proof", sessionProof(MT_RESEND_VERIFICATION, "POST")).header("Cookie", cookieHeader())
                .build()
            http.newCall(req).execute().use { absorbCookies(it) }
        }
        val deadline = System.currentTimeMillis() + 60_000L
        val seen = HashSet<String>()
        while (System.currentTimeMillis() < deadline) {
            val msgs = runCatching { akunlamaList(email) }.getOrDefault(emptyList())
            for (m in msgs) {
                val key = m.optJSONObject("storage")?.optString("key", "").orEmpty()
                if (key.isBlank() || !seen.add(key)) continue
                val region = m.optJSONObject("storage")?.optString("region", "").orEmpty()
                val html = runCatching { akunlamaHtml(region, key) }.getOrDefault("")
                val match = Regex("https://www\\.mangatranslate\\.com/verify-email/\\?token=[^\"'<>\\s]+").find(html)?.value
                if (match != null) {
                    runCatching {
                        val vreq = Request.Builder().url(match).get().header("User-Agent", UA).build()
                        http.newCall(vreq).execute().close()
                    }
                    runCatching { login(email, "") } // refresh if possible not critical
                    return
                }
            }
            Thread.sleep(5000)
        }
    }

    private fun akunlamaList(email: String): List<JSONObject> {
        val url = "$AKUNLAMA_API/list?recipient=${java.net.URLEncoder.encode(email, "UTF-8")}"
        val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = runCatching { JSONArray(resp.body?.string().orEmpty()) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
    }
    private fun akunlamaHtml(region: String, key: String): String {
        val url = "$AKUNLAMA_API/getHtml?region=${java.net.URLEncoder.encode(region, "UTF-8")}&key=${java.net.URLEncoder.encode(key, "UTF-8")}"
        val req = Request.Builder().url(url).get().header("Accept", "text/html,*/*").build()
        http.newCall(req).execute().use { resp -> return if (resp.isSuccessful) resp.body?.string().orEmpty() else "" }
    }

    companion object { private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36" }
}