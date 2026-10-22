package com.example.bookcatalog.domain

object BookFormat {
    val choices = listOf("Hardcover", "Softcover", "Box set", "Kindle", "PDF", "ePub", "Audiobook", "Other")

    /** Matches a choice ignoring case, so "hardcover" or "EPUB" import as the listed spelling. */
    fun normalize(value: String?): String? =
        value?.trim()?.let { raw -> choices.firstOrNull { it.equals(raw, ignoreCase = true) } }
}
