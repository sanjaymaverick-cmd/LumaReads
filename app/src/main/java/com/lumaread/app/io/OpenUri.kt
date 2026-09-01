package com.lumaread.app.io

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object OpenUri {
    fun fileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: error("Missing file path"))
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open $uri")
    }

    fun inputStream(context: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") return FileInputStream(File(uri.path ?: error("Missing file path")))
        return context.contentResolver.openInputStream(uri) ?: error("Unable to open $uri")
    }
}
