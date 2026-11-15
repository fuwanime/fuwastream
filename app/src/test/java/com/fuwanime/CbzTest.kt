package com.fuwanime

import com.fuwanime.data.manga.extractCbz
import com.fuwanime.data.manga.writeCbz
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CbzTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripKeepsPagesInOrder() {
        val src = tmp.newFolder("src")
        listOf("002.jpg" to "b", "001.jpg" to "a", "003.webp" to "c").forEach { (n, t) -> File(src, n).writeText(t) }
        val out = ByteArrayOutputStream()
        writeCbz(src.listFiles()!!.toList(), out)
        val pages = extractCbz(ByteArrayInputStream(out.toByteArray()), File(tmp.root, "out"))
        assertEquals(listOf("001.jpg", "002.jpg", "003.webp"), pages.map { it.name })
        assertEquals(listOf("a", "b", "c"), pages.map { it.readText() })
    }

    @Test fun entriesCannotEscapeTheFolder() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("../evil.jpg")); z.write(1); z.closeEntry() }
        val dir = File(tmp.root, "x")
        extractCbz(ByteArrayInputStream(out.toByteArray()), dir)
        assertFalse(File(tmp.root, "evil.jpg").exists())
        assertEquals(listOf("evil.jpg"), dir.list()!!.toList())
    }
}
