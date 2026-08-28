package com.lumaread.app.data

data class Locator(
    val pageIndex: Int = 0,
    val lineIndex: Int = 0
) {
    fun skipKey(): String = "p${pageIndex}:s$lineIndex"

    companion object {
        fun fromSkipKey(key: String): Locator? {
            val match = Regex("^p(\\d+):s(\\d+)$").matchEntire(key) ?: return null
            return Locator(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}

data class Bookmark(
    val id: String,
    val locator: Locator,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BookStatus { AVAILABLE, LINKED, MISSING, IMPORTING, INDEXING, NEEDS_REPAIR, UNSUPPORTED }

data class SkipRules(val skippedKeys: Set<String> = emptySet()) {
    fun skips(locator: Locator): Boolean = locator.skipKey() in skippedKeys
    fun plus(locator: Locator): SkipRules = copy(skippedKeys = skippedKeys + locator.skipKey())
    fun plusAll(locators: Iterable<Locator>): SkipRules =
        copy(skippedKeys = skippedKeys + locators.map { it.skipKey() })
}
