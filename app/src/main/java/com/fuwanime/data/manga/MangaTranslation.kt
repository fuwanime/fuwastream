package com.fuwanime.data.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One translated text block, positioned as fractions of the page (0..1). Keeping the position lets the reader
 * paint the translation over the original text instead of only listing it below the page.
 */
data class TranslatedBlock(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String,
    /** Sampled bubble background, used to cover the original text. */
    val background: Int,
    /** Black or white, whichever is readable on [background]. */
    val foreground: Int,
)

/** Text recognised on one page and translated to Indonesian. */
data class PageTranslation(
    val text: String,
    val regions: List<TranslatedBlock> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    /** Original OCR text shown when translation is unavailable or failed. */
    val originalText: String = "",
)

enum class MangaTranslationFailure { IMAGE, OCR_MODEL, OCR, TRANSLATION_MODEL, TRANSLATION }

class MangaTranslationException(
    val failure: MangaTranslationFailure,
    cause: Throwable,
) : Exception(cause.message, cause)

/** A recognised text block in original page pixels, before normalising for the UI. */
private data class RecognizedBlock(
    val text: String,
    val rect: Rect,
    val background: Int,
    val foreground: Int,
)

private data class RecognizedPage(
    val width: Int,
    val height: Int,
    val blocks: List<RecognizedBlock>,
)

/**
 * On-device English → Indonesian translation for image chapters (Comix). The Latin OCR model is bundled;
 * the translation model is downloaded once and then works offline. Nothing leaves the device.
 */
class MangaTranslator(
    private val http: OkHttpClient,
) {
    private val mutex = Mutex()
    private val clientLock = Any()
    private var translatorClient: Translator? = null
    private var recognizerClient: TextRecognizer? = null

    @Volatile
    private var modelReady = false

    private fun translator(): Translator = synchronized(clientLock) {
        translatorClient ?: Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.INDONESIAN)
                .build(),
        ).also { translatorClient = it }
    }

    private fun recognizer(): TextRecognizer = synchronized(clientLock) {
        recognizerClient ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { recognizerClient = it }
    }

    /**
     * Downloads the page, reads its text and translates each text block. [onDownloadingModel] fires only while
     * the one-time model download is in progress, so the reader can show its status.
     */
    suspend fun translatePage(url: String, onDownloadingModel: () -> Unit = {}): PageTranslation = mutex.withLock {
        var modelNoticeSent = false
        val notifyModelDownload = {
            if (!modelNoticeSent) {
                modelNoticeSent = true
                onDownloadingModel()
            }
        }
        val bytes = stage(MangaTranslationFailure.IMAGE) { download(url) }
        val page = try {
            recognize(bytes, notifyModelDownload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MlKitException) {
            val failure = if (e.errorCode == MlKitException.UNAVAILABLE || e.errorCode == MlKitException.NETWORK_ISSUE) {
                MangaTranslationFailure.OCR_MODEL
            } else {
                MangaTranslationFailure.OCR
            }
            throw MangaTranslationException(failure, e)
        } catch (e: Exception) {
            throw MangaTranslationException(MangaTranslationFailure.OCR, e)
        }
        if (page.blocks.isEmpty()) return@withLock PageTranslation("", emptyList(), page.width, page.height)
        val originalText = page.blocks.joinToString("\n") { it.text }
        if (!modelReady) {
            notifyModelDownload()
            stage(MangaTranslationFailure.TRANSLATION_MODEL) {
                downloadTranslationModel()
            }
            modelReady = true
        }
        val regions = ArrayList<TranslatedBlock>(page.blocks.size)
        var hasTranslationFailure = false
        for (block in page.blocks) {
            val translated = try {
                stage(MangaTranslationFailure.TRANSLATION) { translateBlock(block.text) }
            } catch (e: Exception) {
                hasTranslationFailure = true
                Log.w(TAG, "Translation failed for block: ${e.message}", e)
                null
            }
            if (translated.isNullOrBlank()) continue
            regions += TranslatedBlock(
                left = block.rect.left.toFloat() / page.width,
                top = block.rect.top.toFloat() / page.height,
                right = block.rect.right.toFloat() / page.width,
                bottom = block.rect.bottom.toFloat() / page.height,
                text = translated,
                background = block.background,
                foreground = block.foreground,
            )
        }
        val displayText = if (regions.isNotEmpty()) regions.joinToString("\n") { it.text } else ""
        PageTranslation(
            text = displayText,
            regions = regions,
            width = page.width,
            height = page.height,
            originalText = if (regions.isEmpty()) originalText else "",
        )
    }

    private suspend fun translateBlock(text: String): String =
        translationChunks(text).map { translator().translate(it).await() }.joinToString(" ").trim()

    /** Releases the ML Kit clients; the app-wide instance normally lives for the whole process. */
    fun close() {
        synchronized(clientLock) {
            runCatching { translatorClient?.close() }
            runCatching { recognizerClient?.close() }
            translatorClient = null
            recognizerClient = null
            modelReady = false
        }
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val candidates = mangaPageCandidates(url).ifEmpty { listOf(url) }
        // Downloaded chapters keep their pages as file: URIs; read those directly.
        if (candidates.first().startsWith("file:")) {
            val path = Uri.parse(candidates.first()).path ?: throw IOException("Invalid local page: ${candidates.first()}")
            return@withContext File(path).readBytes()
        }
        // The Comix CDN rejects requests that carry a Referer (403), so send none.
        var failure: IOException? = null
        for (candidate in candidates) {
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", USER_AGENT)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Comix image HTTP ${response.code}")
                    return@withContext response.body?.bytes() ?: throw IOException("Comix image is empty")
                }
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("Comix image has no usable host")
    }

    /** OCR for one image, split into vertical tiles so very long webtoon strips stay within memory limits. */
    private suspend fun recognize(bytes: ByteArray, onWaitingForModel: () -> Unit): RecognizedPage = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw IOException("Unsupported image format")

        val blocks = ArrayList<RecognizedBlock>()
        if (height <= TILE_HEIGHT) {
            val (bitmap, _) = decode(bytes, width, height) ?: throw IOException("Unsupported image format")
            try {
                val scaleX = width.toFloat() / bitmap.width
                val scaleY = height.toFloat() / bitmap.height
                addBlocks(blocks, recognizeBitmap(bitmap, scaleX, scaleY, 0, width, height, onWaitingForModel))
            } finally {
                bitmap.recycle()
            }
            return@withContext RecognizedPage(width, height, blocks)
        }
        // Region decoding is the memory-friendly path, but some WebP variants throw on this decoder. When no
        // tile can be decoded, sample the whole page down and OCR it in strips instead of failing the chapter.
        if (!decodeTilesWithRegionDecoder(bytes, width, height, blocks, onWaitingForModel)) {
            decodeTilesInMemory(bytes, width, height, blocks, onWaitingForModel)
        }
        RecognizedPage(width, height, blocks)
    }

    /** @return true when at least one tile was decoded; false when the region decoder cannot read this file. */
    private suspend fun decodeTilesWithRegionDecoder(
        bytes: ByteArray,
        width: Int,
        height: Int,
        blocks: MutableList<RecognizedBlock>,
        onWaitingForModel: () -> Unit,
    ): Boolean {
        val decoder = try {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        } catch (e: Exception) {
            Log.w(TAG, "Region decoder unavailable for ${width}x$height image: ${e.message}", e)
            null
        } ?: return false
        val sample = ((width + MAX_WIDTH - 1) / MAX_WIDTH).coerceAtLeast(1)
        var decoded = 0
        try {
            var top = 0
            while (top < height) {
                val bottom = (top + TILE_HEIGHT).coerceAtMost(height)
                val tile = try {
                    decoder.decodeRegion(
                        Rect(0, top, width, bottom),
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Region decode failed at y=$top of $height: ${e.message}", e)
                    null
                }
                if (tile != null) {
                    decoded++
                    // Some decoders ignore inSampleSize; derive the scale from the bitmap actually returned.
                    val scaleX = width.toFloat() / tile.width
                    val scaleY = (bottom - top).toFloat() / tile.height
                    try {
                        addBlocks(blocks, recognizeBitmap(tile, scaleX, scaleY, top, width, height, onWaitingForModel))
                    } finally {
                        tile.recycle()
                    }
                }
                if (bottom >= height) break
                // Overlap keeps a bubble cut by the tile edge readable in one of the two tiles.
                top = bottom - TILE_OVERLAP
            }
        } finally {
            decoder.recycle()
        }
        return decoded > 0
    }

    /** Fallback for files the region decoder rejects: decode once at a memory-safe sample size, then OCR strips. */
    private suspend fun decodeTilesInMemory(
        bytes: ByteArray,
        width: Int,
        height: Int,
        blocks: MutableList<RecognizedBlock>,
        onWaitingForModel: () -> Unit,
    ) {
        val (full, _) = decode(bytes, width, height) ?: throw IOException("Unsupported image format")
        try {
            val scaleX = width.toFloat() / full.width
            val scaleY = height.toFloat() / full.height
            val strip = (TILE_HEIGHT / scaleY).toInt().coerceAtLeast(1)
            val overlap = (TILE_OVERLAP / scaleY).toInt().coerceAtLeast(1)
            var top = 0
            while (top < full.height) {
                val bottom = (top + strip).coerceAtMost(full.height)
                val tile = Bitmap.createBitmap(full, 0, top, full.width, bottom - top)
                try {
                    addBlocks(blocks, recognizeBitmap(tile, scaleX, scaleY, (top * scaleY).toInt(), width, height, onWaitingForModel))
                } finally {
                    if (tile !== full) tile.recycle()
                }
                if (bottom >= full.height) break
                top = (bottom - overlap).coerceAtLeast(top + 1)
            }
        } finally {
            full.recycle()
        }
    }

    private fun decode(bytes: ByteArray, width: Int, height: Int): Pair<Bitmap, Int>? {
        var sample = 1
        while (width / sample > MAX_WIDTH || (width / sample).toLong() * (height / sample) > MAX_PIXELS) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return bitmap to sample
    }

    private suspend fun recognizeBitmap(
        bitmap: Bitmap,
        scaleX: Float,
        scaleY: Float,
        topOffset: Int,
        imageWidth: Int,
        imageHeight: Int,
        onWaitingForModel: () -> Unit,
    ): List<RecognizedBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizeWithModelRetry(image, onWaitingForModel)
        return result.textBlocks.mapNotNull { block ->
            val text = block.text.trim()
            if (text.count(Char::isLetterOrDigit) < 2) return@mapNotNull null
            val box = block.boundingBox ?: return@mapNotNull null
            if (box.width() < 4 || box.height() < 4) return@mapNotNull null
            val left = (box.left * scaleX).roundToInt().coerceIn(0, imageWidth - 1)
            val right = (box.right * scaleX).roundToInt().coerceIn(left + 1, imageWidth)
            val top = (box.top * scaleY).roundToInt().plus(topOffset).coerceIn(0, imageHeight - 1)
            val bottom = (box.bottom * scaleY).roundToInt().plus(topOffset).coerceIn(top + 1, imageHeight)
            val rect = Rect(left, top, right, bottom)
            if (rect.width() < 6 || rect.height() < 6) return@mapNotNull null
            val (background, foreground) = sampleColors(bitmap, box)
            RecognizedBlock(text, rect, background, foreground)
        }
    }

    /** Picks the most common colour around the text; that is the bubble fill, not the lettering. */
    private fun sampleColors(bitmap: Bitmap, box: Rect): Pair<Int, Int> {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        val stepX = max(1, (right - left) / 24)
        val stepY = max(1, (bottom - top) / 24)
        val buckets = HashMap<Int, IntArray>()
        var x = left
        while (x < right) {
            var y = top
            while (y < bottom) {
                var color = bitmap.getPixel(x, y)
                if (Color.alpha(color) < 128) color = Color.WHITE
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val bucket = buckets.getOrPut(key) { IntArray(4) }
                bucket[0]++
                bucket[1] += r
                bucket[2] += g
                bucket[3] += b
                y += stepY
            }
            x += stepX
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return Color.WHITE to Color.BLACK
        val count = best[0].coerceAtLeast(1)
        val r = best[1] / count
        val g = best[2] / count
        val b = best[3] / count
        val background = Color.rgb(r, g, b)
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        val foreground = if (luminance > 140f) Color.BLACK else Color.WHITE
        return background to foreground
    }

    /** Drops the duplicate block produced when a bubble sits on the overlap between two tiles. */
    private fun addBlocks(target: MutableList<RecognizedBlock>, blocks: List<RecognizedBlock>) {
        for (block in blocks) {
            if (target.any { duplicate(it, block) }) continue
            target += block
        }
    }

    private fun duplicate(a: RecognizedBlock, b: RecognizedBlock): Boolean {
        val left = max(a.rect.left, b.rect.left)
        val top = max(a.rect.top, b.rect.top)
        val right = min(a.rect.right, b.rect.right)
        val bottom = min(a.rect.bottom, b.rect.bottom)
        if (right <= left || bottom <= top) return false
        val overlap = (right - left).toLong() * (bottom - top)
        val areaA = a.rect.width().toLong() * a.rect.height()
        val areaB = b.rect.width().toLong() * b.rect.height()
        val union = (areaA + areaB - overlap).coerceAtLeast(1)
        val iou = overlap.toFloat() / union
        if (iou >= 0.7f) return true
        if (iou < 0.25f) return false
        return normalize(a.text) == normalize(b.text)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(WHITESPACE, " ").trim().trim('.', ',', '!', '?', '"', '\'')

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    /** The unbundled OCR model can still be downloading when the first page opens. Retry instead of failing it. */
    private suspend fun recognizeWithModelRetry(image: InputImage, onWaitingForModel: () -> Unit): com.google.mlkit.vision.text.Text {
        repeat(OCR_MODEL_RETRIES) { attempt ->
            try {
                return recognizer().process(image).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MlKitException) {
                val waiting = e.errorCode == MlKitException.UNAVAILABLE || e.errorCode == MlKitException.NETWORK_ISSUE
                if (!waiting || attempt == OCR_MODEL_RETRIES - 1) throw e
                onWaitingForModel()
                delay(OCR_MODEL_RETRY_MS)
            }
        }
        error("OCR retry loop ended unexpectedly")
    }

    private suspend fun downloadTranslationModel() {
        repeat(TRANSLATION_MODEL_RETRIES) { attempt ->
            try {
                translator().downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: MlKitException) {
                val transient = e.errorCode == MlKitException.UNAVAILABLE || e.errorCode == MlKitException.NETWORK_ISSUE
                if (!transient || attempt == TRANSLATION_MODEL_RETRIES - 1) throw e
                delay(TRANSLATION_MODEL_RETRY_MS)
            }
        }
    }

    private suspend fun <T> stage(failure: MangaTranslationFailure, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: MangaTranslationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Manga translation failed at $failure: ${e.message}", e)
        throw MangaTranslationException(failure, e)
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        const val TILE_HEIGHT = 2_400
        const val TILE_OVERLAP = 120
        const val MAX_WIDTH = 2_000
        const val MAX_PIXELS = 8_000_000L
        const val OCR_MODEL_RETRIES = 20
        const val OCR_MODEL_RETRY_MS = 1_500L
        const val TRANSLATION_MODEL_RETRIES = 3
        const val TRANSLATION_MODEL_RETRY_MS = 2_000L
        const val TAG = "MangaTranslator"
        val WHITESPACE = Regex("\\s+")
        const val TRANSLATION_FONT = "translation_font.ttf"
    }
}

/**
 * Splits OCR text at line boundaries for the translator, which caps a single request at a few thousand characters.
 * Internal for unit tests: the split must never drop or reorder a line.
 */
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
