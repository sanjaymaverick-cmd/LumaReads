package com.lumaread.app.data

data class BookItem(
    val id: String,
    val title: String,
    val uri: String,
    val totalPages: Int,
    val lastPage: Int = 0,
    val bookmarks: Set<Int> = emptySet(),
    val addedAt: Long = System.currentTimeMillis(),
    val mediaType: MediaType = MediaType.PDF,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastOpenedAt: Long = 0L
) {
    val progress: Float
        get() = when (mediaType) {
            MediaType.AUDIO -> if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> if (totalPages <= 1) 0f else (lastPage.toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
        }
}

enum class MediaType { PDF, AUDIO }
