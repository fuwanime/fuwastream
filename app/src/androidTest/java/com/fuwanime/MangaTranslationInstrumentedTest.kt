package com.fuwanime

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.MangaOrder
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.PageTranslation
import com.fuwanime.data.manga.mangaPageCandidates
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real pipeline on a device: network download → bundled ML Kit OCR → EN→ID translation.
 * It needs network access and the Google APIs emulator image (the translation model is downloaded once).
 * CI runs it through `connectedDebugAndroidTest`; see the `translate` job in build.yml.
 */
@RunWith(AndroidJUnit4::class)
class MangaTranslationInstrumentedTest {
    @Test(timeout = 30 * 60 * 1000L)
    fun translatesEveryPageOfOneComixChapter() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FuwaApp>()
        val translator = app.container.mangaTranslator
        val repository = app.container.mangaRepository

        val font = Typeface.createFromAsset(app.assets, TRANSLATION_FONT)
        println("translation font loaded: $font")

        // Popular action titles are all-ages in practice and give a stable, text-heavy chapter to translate.
        val popular = repository.browse(MangaOrder.POPULAR, tagIds = listOf(ACTION_GENRE))
        val candidates = popular.items.filter(::isAllAges)
        assertTrue("No all-ages popular action titles from Comix", candidates.isNotEmpty())

        val (manga, chapter, pages) = firstReadableChapter(repository, candidates)
        assertTrue("No readable pages in ${manga.title} ${chapter.id}", pages.isNotEmpty())
        println("chapter: ${manga.title} ${chapter.id} (${pages.size} pages)")

        val translations = ArrayList<PageTranslation>(pages.size)
        pages.forEachIndexed { index, url ->
            val started = System.currentTimeMillis()
            val translation = translator.translatePage(url) {
                println("downloading the translation model (page ${index + 1})")
            }
            val elapsed = System.currentTimeMillis() - started
            println("page ${index + 1}/${pages.size}: ${translation.regions.size} regions, ${translation.text.length} chars in ${elapsed}ms")
            translations += translation
        }

        val withText = translations.count { it.text.isNotBlank() }
        val withRegions = translations.count { it.regions.isNotEmpty() }
        val totalChars = translations.sumOf { it.text.length }
        // Save the first three translated pages with the real overlay font; CI pulls this folder as evidence.
        // Done before the assertions so a failing run still produces images to inspect.
        var saved = 0
        var attempted = 0
        translations.forEachIndexed { index, translation ->
            if (saved < 3 && attempted < 8 && translation.regions.isNotEmpty()) {
                attempted++
                val name = "page-%02d.png".format(index + 1)
                runCatching { writeEvidence(app, pages[index], translation) }
                    .onSuccess { bytes -> if (publishEvidence(app, name, bytes)) saved++ }
                    .onFailure { println("could not save evidence for page ${index + 1}: ${it.message}") }
            }
        }
        println("translated $withText/${pages.size} pages, $withRegions with regions, $totalChars chars total")
        println("saved $saved translated page images")
        assertTrue("OCR produced no text on any page", withRegions > 0)
        // Webtoon chapters contain genuinely textless art pages; require text on a meaningful share instead.
        assertTrue("OCR text missing on most pages ($withText/${pages.size})", withText >= maxOf(1, pages.size / 4))
        assertTrue("Translated text is suspiciously short ($totalChars chars)", totalChars >= pages.size * 20)
        val joined = translations.joinToString(" ") { it.text }.lowercase()
        assertTrue(
            "Translation does not look Indonesian ($totalChars chars)",
            INDONESIAN_WORDS.any { Regex("\\b$it\\b").containsMatchIn(joined) },
        )
        translator.close()
    }

    /** Draws the translated blocks with [TRANSLATION_FONT] so the artifact proves both OCR and the overlay font. */
    private fun writeEvidence(app: FuwaApp, url: String, translation: PageTranslation): ByteArray {
        val bytes = downloadPage(app, url)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inMutable = true },
        ) ?: throw IOException("Unsupported image format (${bytes.size} bytes)")
        // BitmapFactory can ignore inMutable; Canvas requires a mutable bitmap.
        val bitmap = if (decoded.isMutable) {
            decoded
        } else {
            val copy = decoded.copy(Bitmap.Config.ARGB_8888, true)
                ?: throw IOException("Could not make bitmap mutable")
            decoded.recycle()
            copy
        }
        try {
            val canvas = Canvas(bitmap)
            val typeface = Typeface.createFromAsset(app.assets, TRANSLATION_FONT)
            for (block in translation.regions) {
                val left = block.left * bitmap.width
                val top = block.top * bitmap.height
                val right = block.right * bitmap.width
                val bottom = block.bottom * bitmap.height
                canvas.drawRect(left, top, right, bottom, Paint().apply { color = block.background })
                val width = (right - left - 8).toInt().coerceAtLeast(16)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = block.foreground
                    this.typeface = typeface
                    textSize = ((bottom - top) * 0.55f).coerceIn(12f, 72f)
                }
                val layout = StaticLayout.Builder
                    .obtain(block.text, 0, block.text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
                canvas.save()
                canvas.translate(left + 4, top + 4)
                layout.draw(canvas)
                canvas.restore()
            }
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Mirrors MangaTranslator: the Comix CDN needs its User-Agent, no Referer, and may move hosts. */
    private fun downloadPage(app: FuwaApp, url: String): ByteArray {
        var failure: IOException? = null
        for (candidate in mangaPageCandidates(url).ifEmpty { listOf(url) }) {
            try {
                val request = Request.Builder().url(candidate).header("User-Agent", USER_AGENT).build()
                app.container.videoHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $candidate")
                    return response.body?.bytes() ?: throw IOException("Empty body for $candidate")
                }
            } catch (e: IOException) {
                failure = e
            }
        }
        throw IOException("Could not download page $url", failure)
    }

    /** Inserts one render into shared Downloads through MediaStore, so it survives the post-test uninstall. */
    private fun publishEvidence(app: FuwaApp, name: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/translation-evidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            println("could not publish evidence: MediaStore insert returned null")
            return false
        }
        return try {
            val output = resolver.openOutputStream(uri)
            if (output == null) {
                println("could not publish evidence: no output stream for $uri")
                resolver.delete(uri, null, null)
                return false
            }
            output.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            println("published evidence to $uri")
            true
        } catch (e: Exception) {
            println("could not publish evidence: ${e.message}")
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Mature content is a user setting; the pipeline test must stay on all-ages titles. */
    private fun isAllAges(manga: Manga): Boolean {
        val tags = manga.tags.map { it.lowercase() }
        val adult = tags.any { tag -> ADULT_TAGS.any { tag.contains(it) } } ||
            EXPLICIT_WORDS.any { it in manga.title.lowercase() || it in manga.description.lowercase() }
        if (adult) println("skipping adult title: ${manga.title} (tags: ${manga.tags})")
        return !adult
    }

    /** Popular titles can be huge; check the first and latest chapters, which are usually free. */
    private suspend fun firstReadableChapter(
        repository: MangaRepository,
        candidates: List<Manga>,
    ): Triple<Manga, MangaChapter, List<String>> {
        for (manga in candidates) {
            val chapters = runCatching { repository.chapters(manga.id) }.getOrElse {
                println("chapters failed for ${manga.title}: ${it.message}")
                emptyList()
            }
            val readable = chapters.filter { it.externalUrl == null && it.pages > 0 }
            val sample = (readable.take(3) + readable.takeLast(3)).distinctBy { it.id }
            for (chapter in sample) {
                val pages = runCatching { repository.pages(chapter.id) }.getOrElse {
                    println("pages failed for ${manga.title} ${chapter.id}: ${it.message}")
                    emptyList()
                }
                if (pages.size >= 3) return Triple(manga, chapter, pages)
                println("no readable pages for ${manga.title} ${chapter.id} (${pages.size} pages)")
            }
        }
        throw AssertionError("None of ${candidates.size} popular action titles exposed page images")
    }

    private companion object {
        const val TRANSLATION_FONT = "translation_font.ttf"
        const val ACTION_GENRE = "6"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

        /** Broad markers; English text cannot contain most of these, so this proves EN→ID actually ran. */
        val INDONESIAN_WORDS = listOf(
            "yang", "dan", "aku", "kamu", "kau", "tidak", "dengan", "untuk", "ini", "itu",
            "adalah", "apa", "kita", "kami", "kalian", "mereka", "dia", "sudah", "akan", "bisa",
            "dari", "ke", "saya", "anda", "tapi", "karena", "jika", "saat", "harus", "sedang",
            "telah", "belum", "bukan", "hanya", "juga", "lagi",
        )

        /** Mature content is a user setting; the pipeline test must stay on all-ages titles. */
        val ADULT_TAGS = listOf("adult", "smut", "ecchi", "hentai", "mature", "erotica", "pornographic")
        val EXPLICIT_WORDS = listOf("hentai", "erotica", "smut", "nsfw", "porn")
    }
}
