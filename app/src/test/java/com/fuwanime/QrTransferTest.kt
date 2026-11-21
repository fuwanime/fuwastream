package com.fuwanime

import android.app.Application
import com.fuwanime.data.backup.BackupCodec
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.backup.QrFrames
import com.fuwanime.data.backup.qrBitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** android.util.Base64 and Bitmap need the Android shadows; the frame logic itself is pure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QrTransferTest {
    @Test fun smallPayloadIsOneFrame() {
        val json = """{"format":4,"app":"fuwanime"}"""
        val frames = QrFrames.encode(json)
        assertEquals(1, frames.size)
        val (index, total, chunk) = QrFrames.parse(frames[0])!!
        assertEquals(1, index)
        assertEquals(1, total)
        assertEquals(json, QrFrames.decode(mapOf(1 to chunk), 1))
    }

    @Test fun largePayloadRoundTripsAcrossFramesInAnyOrder() {
        // Random text barely compresses, so this really spans many frames.
        val json = buildString {
            val r = Random(42)
            repeat(20_000) { append(('a'..'z').random(r)) }
        }
        val frames = QrFrames.encode(json)
        assertTrue(frames.size > 1)
        val chunks = HashMap<Int, String>()
        frames.shuffled(Random(7)).forEach { f ->
            val (index, total, chunk) = QrFrames.parse(f)!!
            assertEquals(frames.size, total)
            chunks[index] = chunk
        }
        assertEquals(json, QrFrames.decode(chunks, frames.size))
    }

    @Test fun backupPayloadSurvivesTheWholeChain() {
        val file = BackupFile(createdAt = 1, favorites = emptyList(), history = emptyList(), watchDaily = emptyList(), searches = emptyList(), settings = emptyList())
        val frames = QrFrames.encode(BackupCodec.encode(file))
        val chunks = frames.associate { f -> QrFrames.parse(f)!!.let { (i, _, c) -> i to c } }
        val json = QrFrames.decode(chunks, frames.size)!!
        assertEquals(BackupResult.Ok(file), BackupCodec.decode(json))
    }

    @Test fun foreignAndMalformedFramesAreIgnored() {
        assertNull(QrFrames.parse("https://example.com"))
        assertNull(QrFrames.parse("FUWA1:0/2:abc"))
        assertNull(QrFrames.parse("FUWA1:3/2:abc"))
        assertNull(QrFrames.parse("FUWA1:1/0:abc"))
        assertNull(QrFrames.parse("FUWA1:1/2:"))
        assertNull(QrFrames.parse("FUWA1:1/${QrFrames.MAX_FRAMES + 1}:abc"))
        assertNull(QrFrames.parse("FUWA1:1/2:" + "x".repeat(QrFrames.CHUNK_CHARS + 1)))
    }

    @Test fun incompleteOrCorruptPayloadDoesNotDecode() {
        val frames = QrFrames.encode("y".repeat(4) + Random(1).nextBytes(6000).joinToString("") { "%02x".format(it) })
        assertTrue(frames.size > 1)
        val chunks = frames.associate { f -> QrFrames.parse(f)!!.let { (i, _, c) -> i to c } }
        assertNull(QrFrames.decode(chunks - chunks.keys.first(), frames.size))
        val broken = chunks.toMutableMap()
        broken[1] = "!!!!" + broken[1]!!.drop(4)
        assertNull(QrFrames.decode(broken, frames.size))
    }

    @Test fun generatedQrDecodesBackToTheFrameText() {
        val text = "FUWA1:2/9:abcdef0123456789"
        val px = 400
        val bmp = qrBitmap(text, px)
        assertEquals(px, bmp.width)
        assertEquals(px, bmp.height)
        val pixels = IntArray(px * px)
        bmp.getPixels(pixels, 0, px, 0, 0, px, px)
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(px, px, pixels))))
        assertEquals(text, result.text)
    }
}
