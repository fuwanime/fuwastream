package com.fuwanime.data.backup

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Cross-device transfer over animated QR codes, fully local.
 *
 * The backup JSON is gzipped, Base64'd and split into numbered frames ("FUWA1:i/n:chunk"). The
 * sender cycles through the frames; the receiver collects them in any order until all n arrived.
 * Chunks stay small enough that each frame is a low-version QR a phone camera reads reliably from
 * another phone's screen. A payload that fits one chunk is simply a single static frame.
 */
object QrFrames {
    const val PREFIX = "FUWA1:"
    /** Base64 chars per frame: ~530 bytes total per QR keeps the module count comfortably scannable. */
    const val CHUNK_CHARS = 512
    /** Upper bound so a malformed/hostile frame can never make the receiver allocate without limit. */
    const val MAX_FRAMES = 4096

    private val FRAME = Regex("^FUWA1:(\\d+)/(\\d+):(.+)$")

    /** gzip + Base64 + chunk; always at least one frame, even for an empty payload. */
    fun encode(json: String): List<String> {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val parts = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(CHUNK_CHARS)
        return parts.mapIndexed { i, chunk -> "$PREFIX${i + 1}/${parts.size}:$chunk" }
    }

    /** (index, total, chunk) for a well-formed frame, or null for anything else (other QR codes). */
    fun parse(text: String): Triple<Int, Int, String>? {
        val m = FRAME.matchEntire(text) ?: return null
        val index = m.groupValues[1].toIntOrNull() ?: return null
        val total = m.groupValues[2].toIntOrNull() ?: return null
        val chunk = m.groupValues[3]
        if (total !in 1..MAX_FRAMES || index !in 1..total || chunk.length > CHUNK_CHARS) return null
        return Triple(index, total, chunk)
    }

    /** Joins the collected chunks and undoes Base64 + gzip; null when incomplete or corrupt. */
    fun decode(chunks: Map<Int, String>, total: Int): String? {
        if (total !in 1..MAX_FRAMES || chunks.size != total) return null
        val joined = buildString { for (i in 1..total) append(chunks[i] ?: return null) }
        return runCatching {
            val bytes = Base64.decode(joined, Base64.NO_WRAP)
            GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }
}

/** Renders one frame as a crisp black-on-white QR bitmap (low error correction = fewer modules). */
fun qrBitmap(content: String, px: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
    val pixels = IntArray(px * px) { i -> if (matrix[i % px, i / px]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}
