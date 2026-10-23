package com.example.bookcatalog.domain

import com.example.bookcatalog.data.db.Book
import java.text.Normalizer

enum class SortOrder(val label: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    DATE_ADDED("Date added"),
    PUBLICATION_YEAR("Publication year"),
}

/** Searches and sorts the library the way the library screen shows it. */
object LibraryQuery {
    private val leadingArticle = Regex("^(the|a|an)\\s+")
    private val accents = Regex("\\p{Mn}+")

    fun apply(books: List<Book>, query: String, sort: SortOrder): List<Book> =
        sort(books.filter { matches(it, query) }, sort)

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
