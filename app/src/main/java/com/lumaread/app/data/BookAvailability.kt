package com.lumaread.app.data

import android.content.Context
import android.net.Uri

object BookAvailability {
    fun resolve(context: Context, book: BookItem, importing: Boolean = false): BookStatus {
        if (importing) return BookStatus.IMPORTING
        if (book.uri.isBlank()) return BookStatus.NEEDS_REPAIR
        return if (isReadable(context, book.uri)) BookStatus.LINKED else BookStatus.MISSING
    }

    fun isReadable(context: Context, uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
