package com.lumaread.app.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val prefs = context.getSharedPreferences("lumaread_library", Context.MODE_PRIVATE)

    init {
        writableDatabase
        migrateLegacyLibrary()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE books (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                uri TEXT NOT NULL,
                media_type TEXT NOT NULL,
                total_pages INTEGER NOT NULL DEFAULT 1,
                last_page INTEGER NOT NULL DEFAULT 0,
                bookmarks TEXT NOT NULL DEFAULT '',
                duration_ms INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                last_opened_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadBooks(): List<BookItem> {
        return readableDatabase.query("books", null, null, null, null, null, "last_opened_at DESC, added_at DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                    fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    add(BookItem(
                        id = string("id"), title = string("title"), uri = string("uri"),
                        mediaType = runCatching { MediaType.valueOf(string("media_type")) }.getOrDefault(MediaType.PDF),
                        totalPages = long("total_pages").toInt(), lastPage = long("last_page").toInt(),
                        bookmarks = string("bookmarks").split(',').mapNotNull(String::toIntOrNull).toSet(),
                        durationMs = long("duration_ms"), positionMs = long("position_ms"),
                        addedAt = long("added_at"), lastOpenedAt = long("last_opened_at")
                    ))
                }
            }
        }
    }

    fun saveBooks(books: List<BookItem>) {
        writableDatabase.beginTransaction()
        try {
            val ids = books.map { it.id }.toSet()
            books.forEach(::upsert)
            val storedIds = mutableListOf<String>()
            readableDatabase.query("books", arrayOf("id"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) storedIds += cursor.getString(0)
            }
            storedIds.filterNot(ids::contains).forEach { id -> writableDatabase.delete("books", "id = ?", arrayOf(id)) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun upsert(book: BookItem) {
        val values = ContentValues().apply {
            put("id", book.id); put("title", book.title); put("uri", book.uri)
            put("media_type", book.mediaType.name); put("total_pages", book.totalPages)
            put("last_page", book.lastPage); put("bookmarks", book.bookmarks.sorted().joinToString(","))
            put("duration_ms", book.durationMs); put("position_ms", book.positionMs)
            put("added_at", book.addedAt); put("last_opened_at", book.lastOpenedAt)
        }
        writableDatabase.insertWithOnConflict("books", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun migrateLegacyLibrary() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val raw = prefs.getString(KEY_BOOKS, null)
        if (raw != null) runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val marksArray = obj.optJSONArray("bookmarks") ?: JSONArray()
                val marks = buildSet { for (j in 0 until marksArray.length()) add(marksArray.getInt(j)) }
                upsert(BookItem(
                    id = obj.getString("id"), title = obj.getString("title"), uri = obj.getString("uri"),
                    totalPages = obj.optInt("totalPages", 1), lastPage = obj.optInt("lastPage", 0),
                    bookmarks = marks, addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                ))
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    companion object {
        private const val KEY_BOOKS = "books"
        private const val KEY_MIGRATED = "sqlite_migrated"
        private const val DATABASE_NAME = "lumaread.db"
        private const val DATABASE_VERSION = 1
    }
}
