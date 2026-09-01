package com.lumaread.app.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object BookStorage {
    fun booksDir(context: Context): File = File(context.filesDir, "books").apply { mkdirs() }

    fun bookDir(context: Context, bookId: String): File = File(booksDir(context), bookId).apply { mkdirs() }

    fun copyFromUri(context: Context, source: Uri, bookId: String, extension: String): File {
        val dest = File(bookDir(context, bookId), "original.${extension.ifBlank { "bin" }}")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open source file")
        return dest
    }

    fun fileUri(file: File): String = Uri.fromFile(file).toString()

    fun newBookId(): String = UUID.randomUUID().toString()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun readHeader(context: Context, uri: Uri, size: Int = 64): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(size)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        } ?: ByteArray(0)
}
