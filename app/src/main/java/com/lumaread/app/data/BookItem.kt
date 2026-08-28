package com.lumaread.app.data

data class BookItem(
    val id: String,
    val title: String,
    val uri: String,
    val totalPages: Int,
    val lastPage: Int = 0,
    val lastLine: Int = 0,
    val bookmarks: List<Bookmark> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
    val mediaType: MediaType = MediaType.PDF,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val favourite: Boolean = false,
    val playbackSpeed: Float = 1f,
    val chapters: List<AudioChapter> = emptyList(),
    val status: BookStatus = BookStatus.LINKED,
    val skipRules: SkipRules = SkipRules()
) {
    val resumeLocator: Locator get() = Locator(lastPage, lastLine)

    val progress: Float
        get() = when (mediaType) {
            MediaType.AUDIO -> if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> if (totalPages <= 1) 0f else (lastPage.toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
        }

    fun hasBookmark(locator: Locator): Boolean =
        bookmarks.any { it.locator == locator }

    fun toggleBookmark(locator: Locator): BookItem {
        val existing = bookmarks.filter { it.locator == locator }
        return if (existing.isNotEmpty()) copy(bookmarks = bookmarks.filterNot { it.locator == locator }, lastPage = locator.pageIndex, lastLine = locator.lineIndex)
        else copy(
            bookmarks = bookmarks + Bookmark(id = "${id}:${locator.skipKey()}:${System.currentTimeMillis()}", locator = locator),
            lastPage = locator.pageIndex,
            lastLine = locator.lineIndex
        )
    }
}

enum class MediaType { PDF, AUDIO }

data class AudioChapter(val title: String, val startMs: Long)
