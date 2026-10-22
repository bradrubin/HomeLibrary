package com.example.bookcatalog.domain

import java.text.Normalizer

/** Authors are stored as "First Last" names separated by "; ", and sorted by surname. */
object AuthorNames {
    private val suffixes = setOf("Jr.", "Jr", "Sr.", "Sr", "II", "III", "IV")
    private val companyWords = setOf("Inc.", "Inc", "Ltd.", "Ltd", "Co.", "Corp.", "LLC")

    // Lowercase name particles that belong with the surname, as in "Le Guin" or "van Gogh".
    private val particles = setOf("de", "del", "della", "der", "di", "du", "da", "la", "le", "van", "von", "st.")

    /**
     * Turns an inverted name such as "Doe, Jane" or "King, Martin Luther, Jr." into natural
     * order, dropping catalog extras like "(Francis Scott)" or "1896-1940". Other names are kept.
     */
    fun naturalOrder(name: String): String {
        val cleaned = name.replace(Regex("""\s*\([^)]*\)"""), "").replace(Regex("""\s+"""), " ").trim()
        val parts = cleaned.split(",").map(String::trim).filter(String::isNotEmpty)
        if (parts.size < 2 || parts[1] in companyWords || parts[1] in suffixes) return cleaned
        val kept = parts.drop(2).filter { it in suffixes }
        return (listOf("${parts[1]} ${parts[0]}") + kept).joinToString(", ")
    }

    /** Puts each name in a "; "-separated list into natural order. */
    fun normalize(authors: String?): String? =
        authors?.split(';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.map(::naturalOrder)
            ?.takeIf(List<String>::isNotEmpty)
            ?.joinToString("; ")

    /** Sorts by the first author's surname, then the rest of the name. */
    fun sortKey(authors: String?): String {
        val first = naturalOrder(authors?.substringBefore(';').orEmpty())
        val words = first.substringBefore(',').split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return ""
        var surnameStart = words.lastIndex
        while (surnameStart > 1 && words[surnameStart - 1].lowercase() in particles) surnameStart--
        val surname = words.drop(surnameStart).joinToString(" ")
        val given = words.take(surnameStart).joinToString(" ")
        return Normalizer.normalize("$surname $given".trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
    }
}
