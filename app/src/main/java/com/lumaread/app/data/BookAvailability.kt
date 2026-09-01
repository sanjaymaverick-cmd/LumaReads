package com.lumaread.app.data

import android.content.Context
import android.net.Uri
import java.io.File

object BookAvailability {
    fun resolve(context: Context, book: BookItem, importing: Boolean = false): BookStatus {
        if (importing) return BookStatus.IMPORTING
        if (book.format == BookFormat.UNSUPPORTED) return BookStatus.UNSUPPORTED
        if (book.uri.isBlank()) return BookStatus.NEEDS_REPAIR
        return if (isReadable(context, book.uri)) {
            if (book.copied) BookStatus.AVAILABLE else BookStatus.LINKED
        } else BookStatus.MISSING
    }

    fun isReadable(context: Context, uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") return@runCatching uri.path?.let { File(it).exists() } == true
        com.lumaread.app.io.OpenUri.fileDescriptor(context, uri).use { true }
    }.getOrDefault(false)
}
