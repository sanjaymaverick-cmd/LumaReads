package com.lumaread.app.format

import android.content.Context
import android.net.Uri
import com.lumaread.app.data.BookFormat
import com.lumaread.app.pdf.TextPageLoader
import com.lumaread.app.ocr.OcrEngine

object PageTextLoader {
    suspend fun load(context: Context, uri: Uri, pageIndex: Int, format: BookFormat): String {
        return when (format) {
            BookFormat.PDF -> TextPageLoader.load(context, uri, pageIndex)
            BookFormat.CBZ -> {
                val bitmap = DocumentPages.loadImagePage(context, uri, pageIndex) ?: return ""
                try {
                    OcrEngine.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            BookFormat.CBR, BookFormat.DJVU -> ""
            BookFormat.MP3, BookFormat.M4B, BookFormat.AUDIO, BookFormat.UNSUPPORTED -> ""
            else -> {
                val document = DocumentPages.load(context, uri, format)
                document.pages.getOrNull(pageIndex).orEmpty()
            }
        }
    }

    fun pageCount(context: Context, uri: Uri, format: BookFormat): Int = when (format) {
        BookFormat.PDF -> runCatching { com.lumaread.app.pdf.PdfPageRenderer.pageCount(context, uri) }.getOrDefault(1)
        BookFormat.CBZ -> DocumentPages.imageCount(context, uri).coerceAtLeast(1)
        BookFormat.CBR, BookFormat.DJVU -> 1
        BookFormat.MP3, BookFormat.M4B, BookFormat.AUDIO -> 1
        else -> DocumentPages.load(context, uri, format).pages.size.coerceAtLeast(1)
    }
}
