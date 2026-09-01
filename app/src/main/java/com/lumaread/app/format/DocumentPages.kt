package com.lumaread.app.format

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.lumaread.app.data.BookFormat
import com.lumaread.app.io.OpenUri
import com.lumaread.app.pdf.PdfPageRenderer
import java.io.File
import java.util.zip.ZipFile

data class LoadedDocument(
    val pages: List<String>,
    val imageNames: List<String> = emptyList(),
    val title: String? = null,
    val unsupportedReason: String? = null
)

object DocumentPages {
    fun load(context: Context, uri: Uri, format: BookFormat): LoadedDocument = when (format) {
        BookFormat.TXT, BookFormat.MARKDOWN ->
            LoadedDocument(PlainText.pagesFromText(PlainText.decode(readBytes(context, uri))))
        BookFormat.HTML -> LoadedDocument(PlainText.pagesFromText(PlainText.stripTags(readString(context, uri))))
        BookFormat.RTF -> LoadedDocument(PlainText.pagesFromText(PlainText.stripRtf(readString(context, uri))))
        BookFormat.FB2 -> LoadedDocument(PlainText.pagesFromText(PlainText.stripTags(readString(context, uri))))
        BookFormat.EPUB -> epub(context, uri)
        BookFormat.DOCX -> officeXml(context, uri, "word/document.xml")
        BookFormat.ODT -> officeXml(context, uri, "content.xml")
        BookFormat.DOC, BookFormat.MOBI -> bestEffortText(context, uri)
        BookFormat.CBZ -> comicZip(context, uri)
        BookFormat.CBR -> LoadedDocument(emptyList(), unsupportedReason = "CBR needs a CBZ/ZIP file")
        BookFormat.DJVU -> LoadedDocument(emptyList(), unsupportedReason = "DJVU decoder comes later")
        BookFormat.PDF -> LoadedDocument(
            List(runCatching { PdfPageRenderer.pageCount(context, uri) }.getOrDefault(1).coerceAtLeast(1)) { "" }
        )
        else -> LoadedDocument(listOf(readString(context, uri)))
    }

    fun loadImagePage(context: Context, uri: Uri, pageIndex: Int, maxEdge: Int = 1600): Bitmap? {
        val file = fileFromUri(uri) ?: return decodeStream(context, uri, maxEdge)
        if (!file.name.endsWith(".cbz", true) && !file.name.endsWith(".zip", true) && !looksLikeZip(file)) {
            return decodeStream(context, uri, maxEdge)
        }
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = imageEntries(zip)[pageIndex]
                zip.getInputStream(entry).use { input -> decodeBounds(input.readBytes(), maxEdge) }
            }
        }.getOrNull()
    }

    fun imageCount(context: Context, uri: Uri): Int {
        val file = fileFromUri(uri) ?: return 1
        return runCatching { ZipFile(file).use { imageEntries(it).size } }.getOrDefault(1)
    }

    private fun bestEffortText(context: Context, uri: Uri): LoadedDocument {
        val raw = PlainText.decode(readBytes(context, uri))
        val stripped = PlainText.stripTags(raw)
        val usable = if (stripped.count { it.isLetterOrDigit() } > 80) stripped else raw.filter {
            it.isLetterOrDigit() || it.isWhitespace() || it in ". ,;:!?-'\""
        }
        return LoadedDocument(PlainText.pagesFromText(usable))
    }

    private fun epub(context: Context, uri: Uri): LoadedDocument {
        val file = materialize(context, uri)
        return ZipFile(file).use { zip ->
            val html = zip.entries().toList().filter { !it.isDirectory }.map { it.name }
                .filter { name ->
                    val lower = name.lowercase()
                    lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                }.sorted()
            val chunks = html.mapNotNull { name ->
                zip.getInputStream(zip.getEntry(name)).use { PlainText.stripTags(PlainText.decode(it.readBytes())) }
                    .takeIf { it.isNotBlank() }
            }
            LoadedDocument(if (chunks.isEmpty()) listOf("") else chunks.flatMap { PlainText.pagesFromText(it) })
        }
    }

    private fun officeXml(context: Context, uri: Uri, entryName: String): LoadedDocument {
        val file = materialize(context, uri)
        return ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: return LoadedDocument(listOf(""))
            val xml = zip.getInputStream(entry).use { PlainText.decode(it.readBytes()) }
            LoadedDocument(PlainText.pagesFromText(PlainText.stripTags(xml.replace("<w:p", "\n<p"))))
        }
    }

    private fun comicZip(context: Context, uri: Uri): LoadedDocument {
        val file = materialize(context, uri)
        val names = ZipFile(file).use { zip -> imageEntries(zip).map { it.name } }
        return LoadedDocument(List(names.size.coerceAtLeast(1)) { "" }, imageNames = names)
    }

    private fun imageEntries(zip: ZipFile) = zip.entries().toList()
        .filter { !it.isDirectory }
        .filter { entry ->
            val name = entry.name.lowercase()
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".webp") || name.endsWith(".gif")
        }
        .sortedBy { it.name.lowercase() }

    private fun readString(context: Context, uri: Uri) = PlainText.decode(readBytes(context, uri))
    private fun readBytes(context: Context, uri: Uri) =
        runCatching { OpenUri.inputStream(context, uri).use { it.readBytes() } }.getOrDefault(ByteArray(0))
    private fun fileFromUri(uri: Uri) = if (uri.scheme == "file") uri.path?.let(::File) else null
    private fun materialize(context: Context, uri: Uri): File {
        fileFromUri(uri)?.let { if (it.exists()) return it }
        val tmp = File(context.cacheDir, "doc-${uri.hashCode()}-${uri.lastPathSegment ?: "file"}")
        OpenUri.inputStream(context, uri).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp
    }
    private fun looksLikeZip(file: File) = file.inputStream().use { input ->
        val sig = ByteArray(2)
        input.read(sig) == 2 && sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte()
    }
    private fun decodeStream(context: Context, uri: Uri, maxEdge: Int) =
        runCatching { OpenUri.inputStream(context, uri).use { decodeBounds(it.readBytes(), maxEdge) } }.getOrNull()
    private fun decodeBounds(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = (maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1) / maxEdge).coerceAtLeast(1)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
