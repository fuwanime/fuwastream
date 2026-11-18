package com.fuwanime

import androidx.compose.ui.text.font.FontWeight
import com.fuwanime.ui.home.releaseNotesText
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun rendersMarkdownAsPlainStyledText() {
        val md = "## Yang baru\n\n### Manga\n- **Lebih baik:** teks\n\n## Unduh\n- [APK](https://x/y.apk)\n1. Buka `file`"
        val out = releaseNotesText(md)
        assertEquals("Yang baru\n\nManga\n• Lebih baik: teks\n\nUnduh\n• APK\n1. Buka file", out.text)
        assertEquals(FontWeight.Bold, out.spanStyles.first().item.fontWeight)
    }
}
