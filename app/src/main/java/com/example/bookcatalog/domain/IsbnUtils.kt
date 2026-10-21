package com.example.bookcatalog.domain

object IsbnUtils {
    fun normalize(input: String): String =
        input.filterNot(Char::isWhitespace).replace("-", "").uppercase()

    fun isValidIsbn10(input: String): Boolean {
        val isbn = normalize(input)
        if (isbn.length != 10 || isbn.take(9).any { !it.isDigit() }) return false
        val checkDigit = isbn.last().let { if (it == 'X') 10 else it.digitToIntOrNull() ?: return false }
        return (isbn.take(9).map(Char::digitToInt).mapIndexed { index, digit ->
            (10 - index) * digit
        }.sum() + checkDigit) % 11 == 0
    }

    fun isValidIsbn13(input: String): Boolean {
        val isbn = normalize(input)
        if (isbn.length != 13 || isbn.any { !it.isDigit() }) return false
        val sum = isbn.take(12).map(Char::digitToInt).mapIndexed { index, digit ->
            digit * if (index % 2 == 0) 1 else 3
        }.sum()
        return (10 - sum % 10) % 10 == isbn.last().digitToInt()
    }

    fun isbn10To13(input: String): String? {
        val isbn10 = normalize(input)
        if (!isValidIsbn10(isbn10)) return null
        val prefix = "978${isbn10.take(9)}"
        return prefix + isbn13CheckDigit(prefix)
    }

    fun isbn13To10(input: String): String? {
        val isbn13 = normalize(input)
        if (!isValidIsbn13(isbn13) || !isbn13.startsWith("978")) return null
        val prefix = isbn13.substring(3, 12)
        val checkDigit = (prefix.map(Char::digitToInt).mapIndexed { index, digit ->
            (10 - index) * digit
        }.sum() % 11).let { remainder ->
            when (val check = (11 - remainder) % 11) {
                10 -> "X"
                else -> check.toString()
            }
        }
        return prefix + checkDigit
    }

    fun fromBarcode(raw: String): String? {
        val isbn13 = normalize(raw)
        return isbn13.takeIf {
            it.length == 13 && (it.startsWith("978") || it.startsWith("979")) &&
                isValidIsbn13(it)
        }
    }

    fun toIsbn13(input: String): String? {
        val normalized = normalize(input)
        return when {
            isValidIsbn13(normalized) -> normalized
            isValidIsbn10(normalized) -> isbn10To13(normalized)
            else -> null
        }
    }

    private fun isbn13CheckDigit(firstTwelve: String): String {
        val sum = firstTwelve.map(Char::digitToInt).mapIndexed { index, digit ->
            digit * if (index % 2 == 0) 1 else 3
        }.sum()
        return ((10 - sum % 10) % 10).toString()
    }
}
