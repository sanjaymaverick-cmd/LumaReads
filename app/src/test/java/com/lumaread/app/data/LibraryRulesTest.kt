package com.lumaread.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryRulesTest {
    @Test
    fun `filters and sorts books deterministically`() {
        val alpha = book(id = "alpha", title = "Alpha", addedAt = 3, lastOpenedAt = 5)
        val audio = book(id = "audio", title = "Audio", mediaType = MediaType.AUDIO, addedAt = 4, lastOpenedAt = 10)
        val missing = book(id = "missing", title = "Missing", status = BookStatus.MISSING, addedAt = 9)

        assertEquals(listOf("alpha", "missing"), LibraryRules.visibleBooks(listOf(audio, missing, alpha), "", LibraryFilter.BOOKS, LibrarySort.TITLE).map { it.id })
        assertEquals(listOf("audio"), LibraryRules.visibleBooks(listOf(alpha, audio, missing), "", LibraryFilter.AUDIOBOOKS, LibrarySort.RECENT).map { it.id })
        assertEquals(listOf("missing"), LibraryRules.visibleBooks(listOf(alpha, audio, missing), "", LibraryFilter.MISSING, LibrarySort.RECENT).map { it.id })
        assertEquals(listOf("audio", "alpha"), LibraryRules.continueReading(listOf(alpha, audio, missing)).map { it.id })
    }

    @Test
    fun `calculates bounded page and audio progress`() {
        assertEquals(0.5f, book(totalPages = 3, lastPage = 1).progress)
        assertEquals(0f, book(totalPages = 1, lastPage = 1).progress)
        assertEquals(0.25f, book(mediaType = MediaType.AUDIO, durationMs = 400L, positionMs = 100L).progress)
        assertEquals(1f, book(mediaType = MediaType.AUDIO, durationMs = 400L, positionMs = 999L).progress)
        assertFalse(book(mediaType = MediaType.AUDIO, durationMs = 0L, positionMs = 100L).progress > 0f)
    }

    @Test
    fun `normalizes audiobook speed to supported choices`() {
        assertEquals(1f, LibraryRules.normalizeSpeed(0.7f))
        assertEquals(1.25f, LibraryRules.normalizeSpeed(1.3f))
        assertEquals(2f, LibraryRules.normalizeSpeed(3f))
    }

    @Test
    fun `migrates legacy comma separated bookmark pages to locators`() {
        val legacy = LibraryRepository.legacyBookmarks("2,5")
        assertEquals(listOf(2, 5), legacy.map { it.locator.pageIndex })
        assertEquals(listOf("page-2", "page-5"), legacy.map { it.id })
    }

    private fun book(
        id: String = "book",
        title: String = id,
        mediaType: MediaType = MediaType.PDF,
        totalPages: Int = 2,
        lastPage: Int = 1,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        addedAt: Long = 0L,
        lastOpenedAt: Long = 0L,
        status: BookStatus = BookStatus.LINKED
    ) = BookItem(
        id = id,
        title = title,
        uri = "content://$id",
        mediaType = mediaType,
        totalPages = totalPages,
        lastPage = lastPage,
        durationMs = durationMs,
        positionMs = positionMs,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        status = status
    )
}
