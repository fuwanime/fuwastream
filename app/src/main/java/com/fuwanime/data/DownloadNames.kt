package com.fuwanime.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Characters file systems and SAF providers reject in a file name. */
fun cleanFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

/** "Ep 01" for whole numbers (so files sort in order), "Ep 12.5" for halves, the label as-is otherwise ("OVA"). */
fun episodeLabel(number: String): String {
    val n = number.trim().replace(Regex("(?i)^(episode|eps?)\\.?\\s*"), "")
    return when {
        n.toIntOrNull() != null -> "Ep %02d".format(n.toInt())
        n.toBigDecimalOrNull() != null -> "Ep $n"
        else -> number.trim()
    }
}

/** "Sousou no Frieren - Ep 01 (720p).mkv" */
fun episodeFileName(title: String, number: String, quality: String): String =
    cleanFileName("$title - ${episodeLabel(number)} ($quality).mkv")

private val folderLock = Any()

/** The sub-folder [name] of a picked folder [tree], created when missing; null when the provider refuses. */
fun subFolder(resolver: ContentResolver, tree: Uri, name: String): Uri? = synchronized(folderLock) {
    // Locked: episodes finishing together would otherwise each create "Title", "Title (1)", …
    val parentId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
    resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
        while (c.moveToNext()) {
            if (c.getString(1) == name && c.getString(2) == Document.MIME_TYPE_DIR) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
    }
    DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), Document.MIME_TYPE_DIR, name)
}
