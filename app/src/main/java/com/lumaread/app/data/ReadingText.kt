package com.lumaread.app.data

import java.text.BreakIterator
import java.util.Locale

data class SpokenUnit(
    val text: String,
    val pageIndex: Int,
    val lineIndex: Int,
    val paragraphIndex: Int
) {
    val locator: Locator get() = Locator(pageIndex, lineIndex)
}

object ReadingText {
    fun detectLocale(text: String): Locale {
        val devanagari = text.count { it.code in 0x0900..0x097F }
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        return if (devanagari.toFloat() / letters > 0.08f) Locale("hi", "IN") else Locale("en", "IN")
    }

    fun units(text: String, pageIndex: Int, locale: Locale = detectLocale(text)): List<SpokenUnit> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOf(text.trim()).filter { it.isNotBlank() } }
        val result = mutableListOf<SpokenUnit>()
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            splitSentences(paragraph, locale).forEach { sentence ->
                result += SpokenUnit(sentence, pageIndex, result.size, paragraphIndex)
            }
        }
        return result
    }

    fun applySkips(units: List<SpokenUnit>, rules: SkipRules): List<SpokenUnit> =
        units.filterNot { rules.skips(it.locator) }

    fun skipParagraphFrom(units: List<SpokenUnit>, currentLine: Int): List<Locator> {
        val current = units.getOrNull(currentLine) ?: return emptyList()
        return units.filter { it.paragraphIndex == current.paragraphIndex && it.lineIndex >= current.lineIndex }
            .map { it.locator }
    }

    fun splitSentences(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
            if (sentence.isNotBlank()) {
                if (sentence.length <= 3500) result += sentence
                else sentence.chunked(3200).forEach { chunk -> result += chunk.trim() }
            }
            start = end
            end = iterator.next()
        }
        if (result.isEmpty() && text.isNotBlank()) result += text.replace(Regex("\\s+"), " ").trim()
        return result.filter { it.isNotBlank() }
    }
}
