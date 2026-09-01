package com.lumaread.app.format

import java.nio.charset.Charset

object PlainText {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, Charset.forName("UTF-16LE"))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, Charset.forName("UTF-16BE"))
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun stripTags(html: String): String {
        var text = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " "
            }
        return text.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun stripRtf(rtf: String): String {
        val withoutGroups = rtf.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
            .replace(Regex("[{}]"), " ")
            .replace("\\*", " ")
        return withoutGroups.replace(Regex("\\s+"), " ").trim()
    }

    fun pagesFromText(text: String, targetChars: Int = 1800): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return listOf("")
        val paragraphs = clean.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val pages = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) pages += current
            current = StringBuilder()
        }
        paragraphs.forEach { paragraph ->
            if (current.length + paragraph.length + 2 > targetChars && current.isNotBlank()) flush()
            if (paragraph.length > targetChars * 2) {
                paragraph.chunked(targetChars).forEach { chunk ->
                    if (current.isNotBlank()) flush()
                    current.append(chunk)
                    flush()
                }
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }
        flush()
        return pages.map { it.toString() }.ifEmpty { listOf(clean) }
    }
}
