package com.fuwanime

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Downloaded episodes live in external files; backing them up blows the 25 MB quota and skips the whole backup. */
class BackupRulesTest {
    private fun excludesExternal(file: String, section: String): Boolean {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val sections = doc.getElementsByTagName(section)
        return (0 until sections.length).any { i ->
            val excludes = (sections.item(i) as Element).getElementsByTagName("exclude")
            (0 until excludes.length).any { j ->
                val e = excludes.item(j) as Element
                e.getAttribute("domain") == "external" && e.getAttribute("path") == "."
            }
        }
    }

    @Test
    fun legacyFullBackup_excludesExternalFiles() {
        assertTrue(excludesExternal("backup_rules.xml", "full-backup-content"))
    }

    @Test
    fun cloudBackup_excludesExternalFiles() {
        assertTrue(excludesExternal("data_extraction_rules.xml", "cloud-backup"))
    }

    @Test
    fun deviceTransfer_excludesExternalFiles() {
        assertTrue(excludesExternal("data_extraction_rules.xml", "device-transfer"))
    }
}
