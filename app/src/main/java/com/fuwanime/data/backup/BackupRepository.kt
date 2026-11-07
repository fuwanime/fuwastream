package com.fuwanime.data.backup

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.local.SettingsStore
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface BackupOutcome {
    data object Written : BackupOutcome
    /** The phone has no data yet and the folder already holds a backup; offer a restore instead. */
    data object SkippedEmpty : BackupOutcome
    data object NoFolder : BackupOutcome
    /** [code] is [ERROR_FOLDER] or "other:<detail>"; the UI turns it into text. */
    data class Failed(val code: String) : BackupOutcome
}

const val ERROR_FOLDER = "folder"
const val ERROR_OTHER_PREFIX = "other:"

// Code contributor: Andra
/** Writes `fuwanime-backup.json` into a folder the user picked (SAF), so it outlives an uninstall. */
class BackupRepository(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
) {
    private val resolver get() = context.contentResolver

    /** The daily worker and "Cadangkan sekarang" share this instance; one writer at a time. */
    private val writing = Mutex()

    suspend fun backupNow(): BackupOutcome = writing.withLock {
        val prefs = settings.backup.first()
        val tree = prefs.treeUri?.let(Uri::parse) ?: return@withLock BackupOutcome.NoFolder
        try {
            withContext(Dispatchers.IO) { write(tree, prefs.folderAdopted) }.also {
                if (it == BackupOutcome.Written) {
                    settings.setBackupResult(System.currentTimeMillis(), null)
                    settings.adoptBackupFolder()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = when (e) {
                is SecurityException, is FileNotFoundException, is IllegalArgumentException -> ERROR_FOLDER
                else -> "$ERROR_OTHER_PREFIX${e.message}"
            }
            settings.setBackupResult(null, code)
            BackupOutcome.Failed(code)
        }
    }

    private suspend fun write(tree: Uri, folderAdopted: Boolean): BackupOutcome {
        val existing = findChild(tree, BackupCodec.FILE_NAME)
        // A leftover temp file may be the only copy after an interrupted swap, so it counts as an existing backup.
        val tmpLeft = findChild(tree, TMP_NAME)
        if (!shouldWriteBackup(library.isEmpty(), existing != null || tmpLeft != null, folderAdopted)) return BackupOutcome.SkippedEmpty

        val snap = library.snapshot()
        val json = BackupCodec.encode(
            BackupFile(
                createdAt = System.currentTimeMillis(),
                favorites = snap.favorites,
                history = snap.history,
                watchDaily = snap.watchDaily,
                searches = snap.searches,
                mangaFavorites = snap.mangaFavorites,
                mangaHistory = snap.mangaHistory,
                settings = settings.exportSettings(),
            ),
        )
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

        // Write a temp file and swap it in, so a crash mid-write never leaves a half-written backup behind.
        val tmp = tmpLeft ?: create(parent, TMP_NAME)
        writeText(tmp, json)
        try {
            existing?.let { DocumentsContract.deleteDocument(resolver, it) }
            DocumentsContract.renameDocument(resolver, tmp, BackupCodec.FILE_NAME)
                ?: throw UnsupportedOperationException("rename")
        } catch (_: UnsupportedOperationException) {
            // shortcut: providers without rename get a direct overwrite (tiny file, small window), no better option via SAF.
            writeText(findChild(tree, BackupCodec.FILE_NAME) ?: create(parent, BackupCodec.FILE_NAME), json)
            runCatching { DocumentsContract.deleteDocument(resolver, tmp) }
        }
        return BackupOutcome.Written
    }

    suspend fun read(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()?.let(BackupCodec::decode) ?: BackupResult.Corrupt
    }

    /** Merges [file] in; the picked folder (if any) now holds data this install has seen, so it may be updated. */
    suspend fun restore(file: BackupFile) {
        library.restore(file)
        settings.importSettings(file.settings)
        if (settings.backup.first().treeUri != null) settings.adoptBackupFolder()
    }

    private fun create(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: throw FileNotFoundException("create $name")

    private fun writeText(uri: Uri, text: String) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("open $uri")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** The document for [name] directly inside the picked folder, or null. */
    private fun findChild(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }

    private companion object {
        const val TMP_NAME = "fuwanime-backup.tmp.json"
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}

/** InputStream.readNBytes is API 33+; minSdk is 24. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
