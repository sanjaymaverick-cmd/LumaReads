package com.lumaread.app.format

import com.lumaread.app.data.BookFormat
import com.lumaread.app.data.MediaType

data class DetectedFormat(
    val format: BookFormat,
    val mediaType: MediaType,
    val extension: String,
    val reason: String = ""
)

object FormatDetector {
    fun detect(fileName: String, mime: String?, header: ByteArray = ByteArray(0)): DetectedFormat {
        val name = fileName.substringAfterLast('/').lowercase()
        val ext = name.substringAfterLast('.', "")
        val type = mime.orEmpty().lowercase()

        fun hit(format: BookFormat, media: MediaType, reason: String) =
            DetectedFormat(format, media, if (ext.isBlank()) defaultExt(format) else ext, reason)

        if (headerLooksLikePdf(header) || ext == "pdf" || type == "application/pdf") {
            return hit(BookFormat.PDF, MediaType.PDF, "pdf")
        }
        if (ext in setOf("mp3") || type == "audio/mpeg") return hit(BookFormat.MP3, MediaType.AUDIO, "mp3")
        if (ext in setOf("m4b", "m4a") || type in setOf("audio/mp4", "audio/x-m4b", "audio/m4b")) {
            return hit(BookFormat.M4B, MediaType.AUDIO, "m4b")
        }
        if (type.startsWith("audio/") || ext in setOf("wav", "ogg", "opus", "flac", "aac")) {
            return hit(BookFormat.AUDIO, MediaType.AUDIO, "audio")
        }
        if (ext == "epub" || type == "application/epub+zip") return hit(BookFormat.EPUB, MediaType.REFLOW, "epub")
        if (ext == "txt" || type == "text/plain") return hit(BookFormat.TXT, MediaType.REFLOW, "txt")
        if (ext in setOf("html", "htm") || type == "text/html") return hit(BookFormat.HTML, MediaType.REFLOW, "html")
        if (ext in setOf("md", "markdown") || type == "text/markdown") return hit(BookFormat.MARKDOWN, MediaType.REFLOW, "md")
        if (ext == "docx" || type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
            return hit(BookFormat.DOCX, MediaType.REFLOW, "docx")
        }
        if (ext == "doc" || type == "application/msword") return hit(BookFormat.DOC, MediaType.REFLOW, "doc")
        if (ext == "rtf" || type == "application/rtf" || type == "text/rtf") return hit(BookFormat.RTF, MediaType.REFLOW, "rtf")
        if (ext == "odt" || type == "application/vnd.oasis.opendocument.text") return hit(BookFormat.ODT, MediaType.REFLOW, "odt")
        if (ext == "fb2" || type.contains("fictionbook")) return hit(BookFormat.FB2, MediaType.REFLOW, "fb2")
        if (ext in setOf("mobi", "azw", "azw3") || type.contains("mobipocket")) {
            return if (looksEncryptedMobi(header)) {
                DetectedFormat(BookFormat.UNSUPPORTED, MediaType.REFLOW, ext, "drm")
            } else hit(BookFormat.MOBI, MediaType.REFLOW, "mobi")
        }
        if (ext == "cbz" || type == "application/vnd.comicbook+zip") return hit(BookFormat.CBZ, MediaType.IMAGE, "cbz")
        if (ext == "cbr") return hit(BookFormat.CBR, MediaType.IMAGE, "cbr")
        if (ext in setOf("djvu", "djv") || type == "image/vnd.djvu") return hit(BookFormat.DJVU, MediaType.IMAGE, "djvu")
        if (headerLooksLikeZip(header) && ext.isBlank()) return hit(BookFormat.EPUB, MediaType.REFLOW, "zip-guess")
        return DetectedFormat(BookFormat.UNSUPPORTED, MediaType.REFLOW, ext, "unknown")
    }

    fun defaultExt(format: BookFormat): String = when (format) {
        BookFormat.PDF -> "pdf"
        BookFormat.EPUB -> "epub"
        BookFormat.TXT -> "txt"
        BookFormat.HTML -> "html"
        BookFormat.MARKDOWN -> "md"
        BookFormat.DOC -> "doc"
        BookFormat.DOCX -> "docx"
        BookFormat.RTF -> "rtf"
        BookFormat.ODT -> "odt"
        BookFormat.FB2 -> "fb2"
        BookFormat.MOBI -> "mobi"
        BookFormat.CBZ -> "cbz"
        BookFormat.CBR -> "cbr"
        BookFormat.DJVU -> "djvu"
        BookFormat.MP3 -> "mp3"
        BookFormat.M4B -> "m4b"
        BookFormat.AUDIO -> "m4a"
        BookFormat.UNSUPPORTED -> "bin"
    }

    private fun headerLooksLikePdf(header: ByteArray): Boolean =
        header.size >= 5 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()

    private fun headerLooksLikeZip(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

    fun looksEncryptedMobi(header: ByteArray): Boolean {
        if (header.size < 16) return false
        val type = if (header.size > 12) header[12].toInt() and 0xFF else 0
        return type == 1 || type == 2
    }
}
