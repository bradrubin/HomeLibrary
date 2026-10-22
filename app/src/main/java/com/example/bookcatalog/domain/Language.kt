package com.example.bookcatalog.domain

import java.util.Locale
import java.util.MissingResourceException

object Language {
    /** Used when no source says what language a book is in. */
    const val DEFAULT = "English"

    private val code = Regex("[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]+)*")

    // Catalog records use ISO 639-2/B codes; Java only knows the ISO 639-2/T forms.
    private val bibliographicToTerminology = mapOf(
        "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
        "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
        "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
        "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
    )

    /** Spells out a language code such as "en", "fre" or "zh-CN" in English; other text is kept. */
    fun name(value: String?): String? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (text.matches(code)) {
            val tag = text.replace('_', '-')
            val language = tag.substringBefore('-').lowercase()
            val twoLetter = if (language.length == 3) twoLetterCode(language) ?: language else language
            val name = Locale.forLanguageTag(twoLetter).getDisplayLanguage(Locale.ENGLISH)
            if (name.isNotEmpty() && !name.equals(twoLetter, ignoreCase = true)) return name
        }
        return text.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }

    private fun twoLetterCode(threeLetter: String): String? {
        val terminology = bibliographicToTerminology[threeLetter] ?: threeLetter
        return Locale.getISOLanguages().firstOrNull { twoLetter ->
            try {
                Locale.forLanguageTag(twoLetter).isO3Language == terminology
            } catch (_: MissingResourceException) {
                false
            }
        }
    }
}
