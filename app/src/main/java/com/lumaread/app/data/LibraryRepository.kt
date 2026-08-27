package com.lumaread.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("lumaread_library", Context.MODE_PRIVATE)

    fun loadBooks(): List<BookItem> {
        val raw = prefs.getString(KEY_BOOKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val marksArray = obj.optJSONArray("bookmarks") ?: JSONArray()
                    val marks = buildSet {
                        for (j in 0 until marksArray.length()) add(marksArray.getInt(j))
                    }
                    add(
                        BookItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            uri = obj.getString("uri"),
                            totalPages = obj.getInt("totalPages"),
                            lastPage = obj.optInt("lastPage", 0),
                            bookmarks = marks,
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveBooks(books: List<BookItem>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject().apply {
                    put("id", book.id)
                    put("title", book.title)
                    put("uri", book.uri)
                    put("totalPages", book.totalPages)
                    put("lastPage", book.lastPage)
                    put("addedAt", book.addedAt)
                    put("bookmarks", JSONArray(book.bookmarks.sorted()))
                }
            )
        }
        prefs.edit().putString(KEY_BOOKS, array.toString()).apply()
    }

    companion object {
        private const val KEY_BOOKS = "books"
    }
}
