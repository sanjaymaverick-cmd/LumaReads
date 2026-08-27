package com.lumaread.app.data

data class BookItem(
    val id: String,
    val title: String,
    val uri: String,
    val totalPages: Int,
    val lastPage: Int = 0,
    val bookmarks: Set<Int> = emptySet(),
    val addedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalPages <= 1) 0f else (lastPage.toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
}
