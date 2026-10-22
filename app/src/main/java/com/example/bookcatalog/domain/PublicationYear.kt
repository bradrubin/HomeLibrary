package com.example.bookcatalog.domain

/** Reduces the dates sources return, such as "May 1999", "1999-05-12" or "c1999", to a year. */
object PublicationYear {
    private val fourDigits = Regex("(?<!\\d)\\d{4}(?!\\d)")
    private val plausible = 1400..2100

    fun from(value: String?): String? =
        value?.let(fourDigits::findAll)
            ?.map(MatchResult::value)
            ?.firstOrNull { it.toInt() in plausible }

    fun isValid(value: String): Boolean = value.matches(fourDigits) && value.toInt() in plausible
}
