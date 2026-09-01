package com.lumaread.app.backup

import android.content.Context
import android.net.Uri
import com.lumaread.app.io.BookStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupArchive {
    const val MANIFEST = "manifest.json"

    fun suggestedName(): String =
        "LumaRead-Backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.lrbak"

    fun write(context: Context, destination: Uri, bookCount: Int): Boolean = runCatching {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                val manifest = JSONObject()
                    .put("app", "LumaRead")
                    .put("schema", 4)
                    .put("createdAt", System.currentTimeMillis())
                    .put("bookCount", bookCount)
                    .toString()
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
                putFile(zip, context.getDatabasePath("lumaread.db"), "lumaread.db")
                BookStorage.booksDir(context).walkTopDown().filter { it.isFile }.forEach { file ->
                    putFile(zip, file, "books/${file.relativeTo(BookStorage.booksDir(context)).invariantSeparatorsPath}")
                }
            }
        } ?: error("no output")
        true
    }.getOrDefault(false)

    fun restore(context: Context, source: Uri): Boolean = runCatching {
        val staging = File(context.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val dest = File(staging, entry.name)
                    if (!entry.isDirectory) {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("no input")
        val manifest = JSONObject(File(staging, MANIFEST).readText())
        if (manifest.optString("app") != "LumaRead") error("not a LumaRead backup")
        val dbFile = File(staging, "lumaread.db")
        if (dbFile.exists()) {
            dbFile.copyTo(context.getDatabasePath("lumaread.db"), overwrite = true)
            File(context.getDatabasePath("lumaread.db").path + "-wal").delete()
            File(context.getDatabasePath("lumaread.db").path + "-shm").delete()
        }
        val books = File(staging, "books")
        if (books.exists()) {
            val target = BookStorage.booksDir(context)
            target.deleteRecursively()
            books.copyRecursively(target, overwrite = true)
        }
        true
    }.getOrDefault(false)

    private fun putFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

object AnnotationExport {
    fun markdown(title: String, notes: List<Pair<String, String>>): String = buildString {
        appendLine("# $title")
        notes.forEach { (location, body) -> appendLine().appendLine("## $location").appendLine(body) }
    }
    fun csv(notes: List<Triple<String, String, String>>): String = buildString {
        appendLine("title,location,note")
        notes.forEach { (title, location, note) ->
            appendLine(listOf(title, location, note).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
    }
    fun json(bookId: String, notes: List<Pair<String, String>>): String {
        val array = JSONArray()
        notes.forEach { (location, body) ->
            array.put(JSONObject().put("bookId", bookId).put("location", location).put("body", body))
        }
        return array.toString()
    }
}
