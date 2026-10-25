package com.example.bookcatalog.domain

import com.example.bookcatalog.data.db.Book
import java.text.Normalizer

enum class SortOrder(val label: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    DATE_ADDED("Date added"),
    PUBLICATION_YEAR("Publication year"),
}

/**
 * Limits the library to some formats and read statuses; an empty set allows any. A book with no
 * format matches [NO_FORMAT], and one with no read status counts as unread. The rating choices,
 * books rated at least [minRating] stars and [unrated] books, are combined with "or"; with
 * neither chosen any rating is allowed.
 */
data class LibraryFilter(
    val formats: Set<String> = emptySet(),
    val readStatuses: Set<String> = emptySet(),
    val minRating: Int? = null,
    val unrated: Boolean = false,
) {
    val isActive: Boolean
        get() = formats.isNotEmpty() || readStatuses.isNotEmpty() || minRating != null || unrated

    fun matches(book: Book): Boolean =
        (formats.isEmpty() || formatOf(book) in formats) &&
            (readStatuses.isEmpty() || readStatusOf(book) in readStatuses) &&
            matchesRating(ratingOf(book))

    private fun matchesRating(rating: Int): Boolean {
        if (minRating == null && !unrated) return true
        return (unrated && rating == NO_RATING) || (minRating != null && rating >= minRating)
    }

    companion object {
        const val NO_FORMAT = ""
        const val NO_RATING = 0

        fun formatOf(book: Book): String = book.format?.takeIf(String::isNotBlank) ?: NO_FORMAT

        fun readStatusOf(book: Book): String = book.readStatus?.takeIf(String::isNotBlank) ?: ReadStatus.UNREAD

        /** The book's stars, or [NO_RATING]; a stored 0, as the edit screen treats it, is unrated. */
        fun ratingOf(book: Book): Int = book.rating?.takeIf { it in 1..5 } ?: NO_RATING
    }
}

/** Searches and sorts the library the way the library screen shows it. */
object LibraryQuery {
    private val leadingArticle = Regex("^(the|a|an)\\s+")
    private val accents = Regex("\\p{Mn}+")

    fun apply(books: List<Book>, query: String, sort: SortOrder, filter: LibraryFilter = LibraryFilter()): List<Book> =
        sort(books.filter { filter.matches(it) && matches(it, query) }, sort)

    /**
     * Matches every word of [query] against the title, subtitle, authors and publisher, ignoring
     * case and accents. A query that looks like an ISBN also matches with hyphens or spaces, and
     * one that looks like an ASIN matches the ASIN.
     */
    fun matches(book: Book, query: String): Boolean {
        val words = comparable(query).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return true
        val text = comparable(listOfNotNull(book.title, book.subtitle, book.authors, book.publisher).joinToString(" "))
        if (words.all { it in text }) return true
        val digits = IsbnUtils.normalize(query)
        if (digits.length < 4) return false
        if (book.asin?.contains(digits) == true) return true
        return digits.all { it.isDigit() || it == 'X' } &&
            listOfNotNull(book.isbn13, book.isbn10).any { digits in it }
    }

    fun sort(books: List<Book>, sort: SortOrder): List<Book> {
        val byTitle = compareBy<Book> { titleKey(it.title) }.thenBy { it.id }
        // Books missing the sorted field go last, in title order.
        val comparator = when (sort) {
            SortOrder.TITLE -> byTitle
            SortOrder.AUTHOR -> compareBy<Book> { it.authors.isNullOrBlank() }
                .thenBy { AuthorNames.sortKey(it.authors) }
                .then(byTitle)
            SortOrder.DATE_ADDED -> compareByDescending<Book> { it.dateAdded }.then(byTitle)
            SortOrder.PUBLICATION_YEAR -> compareBy<Book> { it.publishedDate.isNullOrBlank() }
                .thenByDescending { it.publishedDate }
                .then(byTitle)
        }
        return books.sortedWith(comparator)
    }

    private fun titleKey(title: String) = comparable(title).replace(leadingArticle, "")

    private fun comparable(value: String) =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(accents, "")
            .lowercase()
            .trim()
}
