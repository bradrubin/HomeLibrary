package com.example.bookcatalog.domain

import java.util.Locale

object TitleCase {
    private val smallWords = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "if", "in", "nor", "of",
        "off", "on", "or", "per", "so", "the", "to", "up", "via", "vs", "with", "yet",
    )
    private val acronyms = setOf(
        "FBI", "LGBTQ", "LGBTQIA", "NASA", "NATO", "UN", "UNESCO", "USA", "USSR",
    )
    private val words = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")

    fun format(value: String): String {
        val matches = words.findAll(value).toList()
        if (matches.isEmpty()) return value

        return buildString(value.length) {
            var cursor = 0
            matches.forEachIndexed { index, match ->
                append(value, cursor, match.range.first)
                val word = match.value
                val previousSeparator = value.substring(cursor, match.range.first)
                val isBoundary = index == 0 || index == matches.lastIndex ||
                    previousSeparator.contains(':') ||
                    (previousSeparator.contains('-') && word.lowercase(Locale.ROOT) !in smallWords)
                append(formatWord(word, isBoundary || word.lowercase(Locale.ROOT) !in smallWords))
                cursor = match.range.last + 1
            }
            append(value, cursor, value.length)
        }
    }

    private fun formatWord(word: String, capitalize: Boolean): String {
        if (word in acronyms) return word
        if (word.any(Char::isLowerCase) && word.any(Char::isUpperCase)) return word
        val lowercase = word.lowercase(Locale.ROOT)
        if (!capitalize) return lowercase
        val firstLetterIndex = lowercase.indexOfFirst(Char::isLetter)
        if (firstLetterIndex < 0) return lowercase
        return lowercase.replaceRange(
            firstLetterIndex,
            firstLetterIndex + 1,
            lowercase[firstLetterIndex].uppercaseChar().toString(),
        )
    }
}
