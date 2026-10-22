package com.example.bookcatalog.domain

object BookFormat {
    val choices = listOf("Hardcover", "Softcover", "Box set", "Kindle", "PDF", "ePub", "Audiobook", "Other")

    // Checked in order, so "Hardcover box set" is a box set and "Audio CD" an audiobook.
    private val sourceTerms = listOf(
        "Box set" to listOf("box", "slipcase"),
        "Audiobook" to listOf("audio", "mp3", "cd"),
        "Kindle" to listOf("kindle", "azw", "mobi"),
        "PDF" to listOf("pdf"),
        "ePub" to listOf("epub"),
        "Softcover" to listOf("paperback", "pbk", "softcover", "soft cover", "mass market", "trade paper", "perfect bound"),
        "Hardcover" to listOf("hardcover", "hardback", "hard cover", "cloth", "library binding", "board book", "leather"),
        "Other" to listOf("spiral", "ring bound", "loose-leaf", "loose leaf", "comb bound"),
    )

    /**
     * Maps a format a source describes, such as "Mass Market Paperback" or "pbk.", to the closest
     * choice. A generic "eBook" or "electronic" maps to nothing, since it could be any ebook choice.
     */
    fun fromSource(value: String?): String? {
        val text = value?.lowercase() ?: return null
        // Phrases match anywhere; single terms match the start of a word, so "box" matches "boxed".
        val words = Regex("[a-z0-9]+").findAll(text).map { it.value }.toSet()
        return sourceTerms.firstOrNull { (_, terms) ->
            terms.any { term -> if (' ' in term || '-' in term) term in text else words.any { it.startsWith(term) } }
        }?.first
    }

    /** Matches a choice ignoring case, so "hardcover" or "EPUB" import as the listed spelling. */
    fun normalize(value: String?): String? =
        value?.trim()?.let { raw -> choices.firstOrNull { it.equals(raw, ignoreCase = true) } }
}
