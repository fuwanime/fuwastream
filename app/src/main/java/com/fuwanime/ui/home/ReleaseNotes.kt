package com.fuwanime.ui.home

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val INLINE = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\([^)]*\)""")
private val HEADING = Regex("""^#{1,6}\s+(.*)$""")
private val BULLET = Regex("""^[-*]\s+(.*)$""")

/** GitHub release notes (Markdown) as plain styled text: headings and **bold** in bold, "- " as a bullet, links as their label. */
fun releaseNotesText(markdown: String): AnnotatedString = buildAnnotatedString {
    var blank = true
    for (raw in markdown.lines()) {
        val line = raw.trim().replace("`", "")
        if (line.isEmpty()) {
            if (!blank) append('\n')
            blank = true
            continue
        }
        if (!blank && length > 0 && !endsWith('\n')) append('\n')
        val heading = HEADING.find(line)?.groupValues?.get(1)
        if (heading != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(heading) }
        } else {
            appendInline(BULLET.find(line)?.let { "• " + it.groupValues[1] } ?: line)
        }
        append('\n')
        blank = false
    }
}.let { it.subSequence(0, it.text.trimEnd().length) }

private fun AnnotatedString.Builder.appendInline(text: String) {
    var at = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(at, m.range.first))
        val bold = m.groups[1]?.value
        if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) } else append(m.groupValues[2])
        at = m.range.last + 1
    }
    append(text.substring(at))
}
