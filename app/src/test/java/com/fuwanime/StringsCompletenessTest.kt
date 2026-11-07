package com.fuwanime

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Plain-JVM check that every locale ships the same keys with compatible format placeholders. */
class StringsCompletenessTest {
    private val res = File(System.getProperty("user.dir"), "src/main/res")
    private val placeholder = Regex("%\\d+\\$[sd]")

    /** key -> placeholders of the entry (plurals: items must agree with `other`, checked here). */
    private fun load(dir: String): Map<String, List<String>> {
        val out = mutableMapOf<String, List<String>>()
        File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().forEach { f ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                if (e.getAttribute("translatable") == "false") continue
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> out[name] = placeholders(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        val byQty = (0 until items.length).associate { (items.item(it) as Element).getAttribute("quantity") to placeholders(items.item(it).textContent) }
                        val other = byQty["other"] ?: error("plurals $name in $dir has no 'other'")
                        byQty.forEach { (q, p) -> assertEquals("plurals $name/$q in $dir", other, p) }
                        out[name] = other
                    }
                }
            }
        }
        return out
    }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    private val base = load("values")
    private val others = listOf("values-ms", "values-en", "values-zh-rCN")

    private fun expectedKeys(dir: String) =
        if (dir == "values-en") base.keys else base.keys.filterNot { it.startsWith("legal_") }.toSet()

    @Test fun everyKeyTranslated() {
        others.forEach { dir ->
            assertTrue("$dir/ missing", File(res, dir).isDirectory)
            val keys = load(dir).keys
            assertEquals("$dir missing keys", emptySet<String>(), expectedKeys(dir) - keys)
            assertEquals("$dir extra keys", emptySet<String>(), keys - expectedKeys(dir))
        }
    }

    @Test fun placeholdersMatchAcrossLocales() {
        others.forEach { dir ->
            val loc = load(dir)
            loc.forEach { (k, p) -> assertEquals("$dir:$k", base[k], p) }
        }
    }

    @Test fun noUnnumberedPlaceholders() {
        val bad = Regex("%(?![0-9]+\\$|%|n)[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdfx]")
        (listOf("values") + others).forEach { dir ->
            File(res, dir).listFiles { f -> f.name.startsWith("strings") }.orEmpty().forEach { f ->
                assertTrue("${f.path} has unnumbered placeholder", bad.find(f.readText()) == null)
            }
        }
    }
}
