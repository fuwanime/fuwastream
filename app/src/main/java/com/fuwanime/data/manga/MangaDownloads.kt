package com.fuwanime.data.manga

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fuwanime.data.local.MangaDownloadDao
import com.fuwanime.data.local.MangaDownloadEntity
import com.fuwanime.data.local.MangaDownloadState
import com.fuwanime.data.local.SettingsStore
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun safeName(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

fun chapterDir(root: File, mangaId: String, chapterId: String): File = File(File(File(root, "manga"), safeName(mangaId)), safeName(chapterId))

/** "007.webp": index padded to 3 digits, extension from the URL path when it is an image type, else jpg. */
fun pageFileName(index: Int, url: String): String {
    val ext = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
    return "%03d.%s".format(index, ext.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg")
}

private val ACTIVE = setOf(MangaDownloadState.QUEUED.name, MangaDownloadState.RUNNING.name, MangaDownloadState.DONE.name)

/** Chapters not yet queued, downloading or downloaded (failed ones may be queued again). */
fun chaptersToQueue(chapters: List<MangaChapter>, rows: Map<String, MangaDownloadEntity>): List<MangaChapter> =
    chapters.filter { rows[it.id]?.state !in ACTIVE }

/** Downloaded chapters of one manga as a reading list: one per number (best source), in reading order. */
fun offlineChapters(rows: List<MangaDownloadEntity>): List<MangaChapter> {
    val chapters = rows.filter { it.state == MangaDownloadState.DONE.name }.map {
        MangaChapter(it.chapterId, it.chapterNumber, it.chapterLabel, it.language, null, it.pageCount, null, it.createdAt)
    }
    val (numbered, other) = chapters.partition { numberKey(it) != null }
    val picked = numbered.groupBy { numberKey(it) }.values.map { g -> g.minBy { sourceRank(it.id) } }
    val (parsable, odd) = picked.partition { it.number?.toBigDecimalOrNull() != null }
    return parsable.sortedBy { it.number!!.toBigDecimal() } + (odd + other).sortedBy { it.publishAt }
}

data class DownloadedManga(val mangaId: String, val title: String, val coverUrl: String?, val done: Int, val pending: Int, val bytes: Long)

/** Library rows: one per manga, newest first. */
fun groupDownloads(rows: List<MangaDownloadEntity>): List<DownloadedManga> =
    rows.groupBy { it.mangaId }.values.sortedByDescending { g -> g.maxOf { it.createdAt } }.map { g ->
        val done = g.filter { it.state == MangaDownloadState.DONE.name }
        DownloadedManga(g.first().mangaId, g.first().mangaTitle, g.first().coverUrl, done.size, g.count { it.state != MangaDownloadState.DONE.name && it.state != MangaDownloadState.FAILED.name }, g.sumOf { it.bytes })
    }

/**
 * Saves each page of [urls] into [dir], skipping pages already there; a page is written to `.part` and renamed when
 * complete, so a killed download never leaves a half image. Returns false when [keepGoing] says stop (deleted).
 */
suspend fun downloadPages(
    urls: List<String>,
    dir: File,
    client: OkHttpClient,
    keepGoing: suspend () -> Boolean = { true },
    onPage: suspend (done: Int, bytes: Long) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    dir.mkdirs()
    var bytes = 0L
    urls.forEachIndexed { i, url ->
        if (!keepGoing()) return@withContext false
        val file = File(dir, pageFileName(i + 1, url))
        if (!(file.exists() && file.length() > 0)) {
            val part = File(dir, file.name + ".part")
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code} for page ${i + 1}")
                    part.outputStream().use { out -> res.body!!.byteStream().copyTo(out) }
                }
                if (!part.renameTo(file)) throw IOException("rename failed")
            } finally {
                part.delete()
            }
        }
        bytes += file.length()
        onPage(i + 1, bytes)
    }
    true
}

/** Packs page images into a CBZ (a zip of images, in page order); images are already compressed, so they are stored. */
fun writeCbz(pages: List<File>, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
        zip.setLevel(Deflater.NO_COMPRESSION)
        pages.sortedBy { it.name }.forEach { f ->
            zip.putNextEntry(ZipEntry(f.name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** Unpacks a CBZ's images into [dir] (flat, names kept) and returns them in page order. */
fun extractCbz(input: InputStream, dir: File): List<File> {
    dir.mkdirs()
    ZipInputStream(input).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            // Only the file name: an entry like "../x" must not write outside [dir].
            val name = e.name.substringAfterLast('/')
            if (e.isDirectory || name.isEmpty()) continue
            File(dir, name).outputStream().use { zip.copyTo(it) }
        }
    }
    return dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
}

/** Queued and finished chapter downloads; the work runs in [MangaDownloadWorker], one chapter at a time. */
class MangaDownloadRepository(private val context: Context, private val dao: MangaDownloadDao, private val settings: SettingsStore) {
    val all: Flow<List<MangaDownloadEntity>> = dao.observeAll()

    fun forManga(mangaId: String): Flow<Map<String, MangaDownloadEntity>> = dao.observeFor(mangaId).map { l -> l.associateBy { it.chapterId } }

    fun root(): File = context.getExternalFilesDir(null) ?: context.filesDir

    suspend fun row(chapterId: String): MangaDownloadEntity? = dao.get(chapterId)

    /** Queues the chapters not already queued or downloaded; returns how many were queued. */
    suspend fun enqueue(manga: Manga, chapters: List<MangaChapter>, label: (MangaChapter) -> String): Int {
        val rows = dao.allFor(manga.id).associateBy { it.chapterId }
        val todo = chaptersToQueue(chapters, rows)
        if (todo.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val network = if (settings.settings.first().downloadWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val requests = todo.mapIndexed { i, c ->
            dao.upsert(MangaDownloadEntity(c.id, manga.id, manga.title, manga.coverUrl, c.number, label(c), c.language, 0, 0, 0, MangaDownloadState.QUEUED.name, now + i))
            OneTimeWorkRequestBuilder<MangaDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(MangaDownloadWorker.KEY_CHAPTER to c.id))
                .build()
        }
        // One chain: chapters download one after another, gentle on the sources' rate limits.
        requests.drop(1).fold(WorkManager.getInstance(context).beginUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())) { chain, r -> chain.then(r) }
            .enqueue()
        return todo.size
    }

    suspend fun delete(chapterId: String) {
        val row = dao.get(chapterId) ?: return
        dao.delete(chapterId)
        withContext(Dispatchers.IO) {
            chapterDir(root(), row.mangaId, chapterId).deleteRecursively()
            row.fileUri?.let(::deleteDocument)
        }
    }

    suspend fun deleteFor(mangaId: String) {
        val rows = dao.allFor(mangaId)
        dao.deleteFor(mangaId)
        withContext(Dispatchers.IO) {
            File(File(root(), "manga"), safeName(mangaId)).deleteRecursively()
            rows.mapNotNull { it.fileUri }.forEach(::deleteDocument)
        }
    }

    private fun deleteDocument(uri: String) {
        val u = Uri.parse(uri)
        if (u.scheme == "file") File(u.path!!).delete()
        else runCatching { DocumentsContract.deleteDocument(context.contentResolver, u) }
    }

    /**
     * Packs a finished chapter into one CBZ, "<title> - <chapter>.cbz": in the picked download folder when one is set,
     * else next to the app's other manga files; then removes the loose pages. Any failure keeps the pages, so nothing is lost.
     */
    suspend fun packCbz(row: MangaDownloadEntity) = withContext(Dispatchers.IO) {
        val folder = settings.settings.first().downloadFolder
        val dir = chapterDir(root(), row.mangaId, row.chapterId)
        val pages = dir.listFiles { f -> !f.name.endsWith(".part") }.orEmpty().toList()
        if (pages.isEmpty()) return@withContext
        var target: Uri? = null
        try {
            val name = "${row.mangaTitle} - ${row.chapterLabel}.cbz".replace(Regex("[\\\\/:*?\"<>|]"), "_")
            target = if (folder != null) {
                val tree = Uri.parse(folder)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(context.contentResolver, parent, "application/vnd.comicbook+zip", name) ?: return@withContext
            } else {
                Uri.fromFile(File(dir.parentFile, safeName(row.chapterId) + ".cbz"))
            }
            context.contentResolver.openOutputStream(target)!!.use { writeCbz(pages, it) }
            dao.setFile(row.chapterId, target.toString(), pages.sumOf { it.length() })
            dir.deleteRecursively()
        } catch (e: Exception) {
            target?.let { deleteDocument(it.toString()) }
        }
    }

    /** `file://` URIs of a finished download's pages in order, or null when it is not downloaded. */
    suspend fun localPages(chapterId: String): List<String>? {
        val row = dao.get(chapterId)?.takeIf { it.state == MangaDownloadState.DONE.name } ?: return null
        row.fileUri?.let { uri ->
            // A CBZ in the user's folder: unpack once into the cache and read from there.
            return withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(File(context.cacheDir, "cbz"), safeName(chapterId))
                    val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.takeIf { it.isNotEmpty() }
                        ?: context.contentResolver.openInputStream(Uri.parse(uri))!!.use { extractCbz(it, dir) }
                    files.map { "file://" + it.absolutePath }.takeIf { it.isNotEmpty() }
                }.getOrNull()
            }
        }
        return withContext(Dispatchers.IO) {
            chapterDir(root(), row.mangaId, chapterId).listFiles { f -> !f.name.endsWith(".part") }
                ?.sortedBy { it.name }?.map { "file://" + it.absolutePath }?.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun offline(mangaId: String): List<MangaChapter> = offlineChapters(dao.doneFor(mangaId))

    /** The title as stored with its downloads, for opening it without network; null when nothing is downloaded. */
    suspend fun offlineManga(mangaId: String): Manga? = dao.doneFor(mangaId).firstOrNull()?.let {
        Manga(it.mangaId, it.mangaTitle, it.coverUrl, "", emptyList(), MangaStatus.UNKNOWN, null, null)
    }

    companion object {
        const val WORK_NAME = "manga-downloads"
    }
}
