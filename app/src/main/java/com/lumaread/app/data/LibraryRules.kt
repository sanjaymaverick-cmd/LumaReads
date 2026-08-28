package com.lumaread.app.data

enum class LibraryFilter { ALL, BOOKS, AUDIOBOOKS, FAVOURITES, MISSING }
enum class LibrarySort { RECENT, TITLE }

object LibraryRules {
    fun visibleBooks(
        books: List<BookItem>,
        query: String,
        filter: LibraryFilter,
        sort: LibrarySort
    ): List<BookItem> = books.asSequence()
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .filter {
            when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.BOOKS -> it.mediaType == MediaType.PDF
                LibraryFilter.AUDIOBOOKS -> it.mediaType == MediaType.AUDIO
                LibraryFilter.FAVOURITES -> it.favourite
                LibraryFilter.MISSING -> it.status == BookStatus.MISSING
            }
        }
        .let { sequence ->
            when (sort) {
                LibrarySort.RECENT -> sequence.sortedWith(
                    compareByDescending<BookItem> { it.lastOpenedAt }
                        .thenByDescending { it.addedAt }
                )
                LibrarySort.TITLE -> sequence.sortedBy { it.title.lowercase() }
            }
        }
        .toList()

    fun continueReading(books: List<BookItem>, limit: Int = 5): List<BookItem> = books
        .asSequence()
        .filter { it.lastOpenedAt > 0L && it.progress in 0.0001f..0.9999f }
        .sortedByDescending { it.lastOpenedAt }
        .take(limit)
        .toList()

    fun normalizeSpeed(value: Float): Float = PLAYBACK_SPEEDS.minBy { kotlin.math.abs(it - value) }

    val PLAYBACK_SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)
}
