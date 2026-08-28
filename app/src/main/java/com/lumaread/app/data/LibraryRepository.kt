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

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_BOOKS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE books ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE books ADD COLUMN playback_speed REAL NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE books ADD COLUMN chapters TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE books ADD COLUMN last_line INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE books ADD COLUMN skip_rules TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE books ADD COLUMN status TEXT NOT NULL DEFAULT 'LINKED'")
            migrateBookmarkColumn(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.enableWriteAheadLogging()
    }

    fun loadBooks(): List<BookItem> {
        return readableDatabase.query("books", null, null, null, null, null, "last_opened_at DESC, added_at DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                    fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    val lastLineIndex = cursor.getColumnIndex("last_line")
                    val skipIndex = cursor.getColumnIndex("skip_rules")
                    val statusIndex = cursor.getColumnIndex("status")
                    add(BookItem(
                        id = string("id"), title = string("title"), uri = string("uri"),
                        mediaType = runCatching { MediaType.valueOf(string("media_type")) }.getOrDefault(MediaType.PDF),
                        totalPages = long("total_pages").toInt(), lastPage = long("last_page").toInt(),
                        lastLine = if (lastLineIndex >= 0) cursor.getInt(lastLineIndex) else 0,
                        bookmarks = decodeBookmarks(string("bookmarks")),
                        durationMs = long("duration_ms"), positionMs = long("position_ms"),
                        addedAt = long("added_at"), lastOpenedAt = long("last_opened_at"),
                        favourite = long("favourite") != 0L,
                        playbackSpeed = cursor.getFloat(cursor.getColumnIndexOrThrow("playback_speed")),
                        chapters = decodeChapters(string("chapters")),
                        skipRules = if (skipIndex >= 0) decodeSkipRules(cursor.getString(skipIndex)) else SkipRules(),
                        status = if (statusIndex >= 0) decodeStatus(cursor.getString(statusIndex)) else BookStatus.LINKED
                    ))
                }
            }
        }
    }

    fun upsert(book: BookItem) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict("books", null, valuesOf(book), SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun saveBooks(books: List<BookItem>) {
        writableDatabase.beginTransaction()
        try {
            val ids = books.map { it.id }.toSet()
            books.forEach { book ->
                writableDatabase.insertWithOnConflict("books", null, valuesOf(book), SQLiteDatabase.CONFLICT_REPLACE)
            }
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

    private fun valuesOf(book: BookItem): ContentValues = ContentValues().apply {
        put("id", book.id); put("title", book.title); put("uri", book.uri)
        put("media_type", book.mediaType.name); put("total_pages", book.totalPages)
        put("last_page", book.lastPage); put("last_line", book.lastLine)
        put("bookmarks", encodeBookmarks(book.bookmarks))
        put("duration_ms", book.durationMs); put("position_ms", book.positionMs)
        put("added_at", book.addedAt); put("last_opened_at", book.lastOpenedAt)
        put("favourite", if (book.favourite) 1 else 0); put("playback_speed", book.playbackSpeed)
        put("chapters", encodeChapters(book.chapters))
        put("skip_rules", encodeSkipRules(book.skipRules))
        put("status", book.status.name)
    }

    private fun migrateBookmarkColumn(db: SQLiteDatabase) {
        db.query("books", arrayOf("id", "bookmarks"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val raw = cursor.getString(1).orEmpty()
                val upgraded = encodeBookmarks(legacyBookmarks(raw))
                val values = ContentValues().apply { put("bookmarks", upgraded) }
                db.update("books", values, "id = ?", arrayOf(id))
            }
        }
    }

    private fun encodeChapters(chapters: List<AudioChapter>): String = JSONArray().apply {
        chapters.forEach { chapter -> put(JSONObject().put("title", chapter.title).put("startMs", chapter.startMs)) }
    }.toString()

    private fun decodeChapters(raw: String): List<AudioChapter> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val chapter = array.getJSONObject(i)
                add(AudioChapter(chapter.optString("title", "Chapter ${i + 1}"), chapter.optLong("startMs")))
            }
        }
    }.getOrDefault(emptyList())

    private fun migrateLegacyLibrary() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val raw = prefs.getString(KEY_BOOKS, null)
        if (raw != null) runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val marksArray = obj.optJSONArray("bookmarks") ?: JSONArray()
                val marks = buildList {
                    for (j in 0 until marksArray.length()) {
                        add(Bookmark(id = "${obj.getString("id")}:p${marksArray.getInt(j)}", locator = Locator(marksArray.getInt(j))))
                    }
                }
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
        private const val DATABASE_VERSION = 3
        private const val CREATE_BOOKS = """
            CREATE TABLE books (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                uri TEXT NOT NULL,
                media_type TEXT NOT NULL,
                total_pages INTEGER NOT NULL DEFAULT 1,
                last_page INTEGER NOT NULL DEFAULT 0,
                last_line INTEGER NOT NULL DEFAULT 0,
                bookmarks TEXT NOT NULL DEFAULT '[]',
                duration_ms INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                last_opened_at INTEGER NOT NULL DEFAULT 0,
                favourite INTEGER NOT NULL DEFAULT 0,
                playback_speed REAL NOT NULL DEFAULT 1,
                chapters TEXT NOT NULL DEFAULT '[]',
                skip_rules TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'LINKED'
            )
        """

        fun encodeBookmarks(bookmarks: List<Bookmark>): String = JSONArray().apply {
            bookmarks.forEach { mark ->
                put(JSONObject().put("id", mark.id).put("page", mark.locator.pageIndex).put("line", mark.locator.lineIndex).put("createdAt", mark.createdAt))
            }
        }.toString()

        fun decodeBookmarks(raw: String): List<Bookmark> {
            if (raw.isBlank()) return emptyList()
            if (!raw.trimStart().startsWith("[")) return legacyBookmarks(raw)
            return runCatching {
                val array = JSONArray(raw)
                if (array.length() == 0) emptyList()
                else if (array.opt(0) is Int) legacyBookmarks(raw)
                else buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(Bookmark(obj.optString("id", "b$i"), Locator(obj.optInt("page"), obj.optInt("line")), obj.optLong("createdAt")))
                    }
                }
            }.getOrDefault(legacyBookmarks(raw))
        }

        fun legacyBookmarks(raw: String): List<Bookmark> {
            val pages = if (raw.trimStart().startsWith("[")) {
                runCatching {
                    val array = JSONArray(raw)
                    buildList { for (i in 0 until array.length()) add(array.getInt(i)) }
                }.getOrDefault(emptyList())
            } else raw.split(',').mapNotNull(String::toIntOrNull)
            return pages.map { page -> Bookmark(id = "page-$page", locator = Locator(page)) }
        }

        fun encodeSkipRules(rules: SkipRules): String = JSONArray(rules.skippedKeys.toList().sorted()).toString()

        fun decodeSkipRules(raw: String?): SkipRules {
            if (raw.isNullOrBlank()) return SkipRules()
            return runCatching {
                val array = JSONArray(raw)
                SkipRules(buildSet { for (i in 0 until array.length()) add(array.getString(i)) })
            }.getOrDefault(SkipRules())
        }

        fun decodeStatus(raw: String?): BookStatus =
            runCatching { BookStatus.valueOf(raw ?: "LINKED") }.getOrDefault(BookStatus.LINKED)
    }
}
