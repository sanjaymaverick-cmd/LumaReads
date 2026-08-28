package com.lumaread.app.audio

import android.content.Context
import android.net.Uri
import com.lumaread.app.data.AudioChapter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object M4bChapterReader {
    private val containers = setOf("moov", "udta", "trak", "mdia", "minf", "stbl")

    fun read(context: Context, uri: Uri): List<AudioChapter> = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                findChapters(channel, 0L, channel.size())
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun findChapters(channel: FileChannel, start: Long, end: Long): List<AudioChapter> {
        var offset = start
        while (offset + 8 <= end) {
            val header = read(channel, offset, 16) ?: return emptyList()
            var size = header.int.toLong() and 0xffffffffL
            val typeBytes = ByteArray(4).also(header::get)
            val type = String(typeBytes, Charsets.US_ASCII)
            var headerSize = 8L
            if (size == 1L) { size = header.long; headerSize = 16L }
            if (size == 0L) size = end - offset
            if (size < headerSize || offset + size > end) return emptyList()
            val payloadStart = offset + headerSize
            if (type == "chpl") return parseChapterAtom(channel, payloadStart, size - headerSize)
            if (type in containers) {
                val nested = findChapters(channel, payloadStart, offset + size)
                if (nested.isNotEmpty()) return nested
            }
            offset += size
        }
        return emptyList()
    }

    private fun parseChapterAtom(channel: FileChannel, start: Long, length: Long): List<AudioChapter> {
        if (length < 9L || length > Int.MAX_VALUE) return emptyList()
        val data = read(channel, start, length.toInt()) ?: return emptyList()
        data.position(8)
        val count = data.get().toInt() and 0xff
        return buildList {
            repeat(count) { index ->
                if (data.remaining() < 9) return@buildList
                val startMs = data.long / 10_000L
                val titleLength = data.get().toInt() and 0xff
                if (titleLength > data.remaining()) return@buildList
                val titleBytes = ByteArray(titleLength).also(data::get)
                add(AudioChapter(String(titleBytes, Charsets.UTF_8).ifBlank { "Chapter ${index + 1}" }, startMs))
            }
        }.sortedBy { it.startMs }
    }

    private fun read(channel: FileChannel, offset: Long, length: Int): ByteBuffer? {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
        channel.position(offset)
        while (buffer.hasRemaining() && channel.read(buffer) >= 0) Unit
        if (buffer.hasRemaining()) return null
        return buffer.flip().order(ByteOrder.BIG_ENDIAN)
    }
}
