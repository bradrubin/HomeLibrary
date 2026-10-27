package com.example.bookcatalog.domain

import com.example.bookcatalog.data.db.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryTest {
    private val dune = Book(
        id = 1, title = "Dune", authors = "Frank Herbert", publisher = "Ace",
        isbn13 = "9780441013593", isbn10 = "0441013597", publishedDate = "1990", dateAdded = 3,
    )
    private val odyssey = Book(id = 2, title = "The Odyssey", authors = "Homer", publishedDate = "1996", dateAdded = 1)
    private val amelie = Book(id = 3, title = "Amélie", subtitle = "A Screenplay", dateAdded = 2)

    @Test
    fun matchesEveryWordAcrossFieldsIgnoringCaseAndAccents() {
        assertTrue(LibraryQuery.matches(dune, "herbert DUNE"))
        assertTrue(LibraryQuery.matches(dune, "ace"))
        assertTrue(LibraryQuery.matches(amelie, "amelie screenplay"))
        assertFalse(LibraryQuery.matches(dune, "herbert odyssey"))
        assertTrue(LibraryQuery.matches(dune, "  "))
    }

    @Test
    fun matchesIsbnWithHyphens() {
        assertTrue(LibraryQuery.matches(dune, "978-0-441-01359-3"))
        assertTrue(LibraryQuery.matches(dune, "0441013597"))
        assertFalse(LibraryQuery.matches(dune, "978-0-14"))
    }

    @Test
    fun sortsTitlesIgnoringLeadingArticles() {
        assertEquals(listOf(amelie, dune, odyssey), LibraryQuery.sort(listOf(odyssey, dune, amelie), SortOrder.TITLE))
    }

    @Test
    fun sortsTitlesIgnoringALeadingAn() {
        val titles = listOf("Anarchy", "An American Marriage", "Any Human Heart", "An Odyssey", "The Italian", "An Italian Education")
        val books = titles.mapIndexed { index, title -> Book(id = index + 10L, title = title) }

        assertEquals(
            listOf("An American Marriage", "Anarchy", "Any Human Heart", "The Italian", "An Italian Education", "An Odyssey"),
            LibraryQuery.sort(books, SortOrder.TITLE).map(Book::title),
        )
    }

    @Test
    fun putsBooksMissingTheSortedFieldLast() {
        assertEquals(listOf(dune, odyssey, amelie), LibraryQuery.sort(listOf(amelie, odyssey, dune), SortOrder.AUTHOR))
        assertEquals(
            listOf(odyssey, dune, amelie),
            LibraryQuery.sort(listOf(amelie, dune, odyssey), SortOrder.PUBLICATION_YEAR),
        )
    }

    @Test
    fun sortsAuthorsBySurname() {
        val leGuin = Book(id = 4, title = "The Dispossessed", authors = "Ursula K. Le Guin")
        val tucci = Book(id = 5, title = "Taste", authors = "Tucci, Stanley")
        val adams = Book(id = 6, title = "Hitchhiker", authors = "Douglas Adams")

        assertEquals(
            listOf(adams, dune, odyssey, leGuin, tucci),
            LibraryQuery.sort(listOf(tucci, leGuin, odyssey, dune, adams), SortOrder.AUTHOR),
        )
    }

    @Test
    fun sortsNewestAddedFirst() {
        assertEquals(listOf(dune, amelie, odyssey), LibraryQuery.sort(listOf(odyssey, amelie, dune), SortOrder.DATE_ADDED))
    }

    @Test
    fun filtersThenSorts() {
        assertEquals(listOf(dune), LibraryQuery.apply(listOf(odyssey, dune, amelie), "frank", SortOrder.TITLE))
    }

    @Test
    fun filtersByFormatAndReadStatus() {
        val kindleUnread = Book(id = 10, title = "A", format = "Kindle", readStatus = "unread")
        val kindleRead = Book(id = 11, title = "B", format = "Kindle", readStatus = "read")
        val audioNoStatus = Book(id = 12, title = "C", format = "Audiobook")
        val noFormat = Book(id = 13, title = "D", readStatus = "reference")
        val books = listOf(kindleUnread, kindleRead, audioNoStatus, noFormat)

        fun shown(filter: LibraryFilter) = LibraryQuery.apply(books, "", SortOrder.TITLE, filter).map { it.id }

        assertEquals(listOf(10L, 11L, 12L, 13L), shown(LibraryFilter()))
        assertEquals(listOf(10L, 11L, 12L), shown(LibraryFilter(formats = setOf("Kindle", "Audiobook"))))
        // A book with no read status counts as unread.
        assertEquals(listOf(10L, 12L), shown(LibraryFilter(readStatuses = setOf("unread"))))
        assertEquals(listOf(11L), shown(LibraryFilter(formats = setOf("Kindle"), readStatuses = setOf("read"))))
        assertEquals(listOf(13L), shown(LibraryFilter(formats = setOf(LibraryFilter.NO_FORMAT))))
    }

    @Test
    fun filtersByRating() {
        val five = Book(id = 20, title = "A", rating = 5)
        val four = Book(id = 21, title = "B", rating = 4)
        val two = Book(id = 22, title = "C", rating = 2)
        val unrated = Book(id = 23, title = "D")
        val zero = Book(id = 24, title = "E", rating = 0)
        val books = listOf(five, four, two, unrated, zero)

        fun shown(filter: LibraryFilter) = LibraryQuery.apply(books, "", SortOrder.TITLE, filter).map { it.id }

        assertEquals(listOf(20L, 21L), shown(LibraryFilter(minRating = 4)))
        // A stored rating of 0 counts as unrated.
        assertEquals(listOf(23L, 24L), shown(LibraryFilter(unrated = true)))
        assertEquals(listOf(20L, 23L, 24L), shown(LibraryFilter(minRating = 5, unrated = true)))
        assertTrue(LibraryFilter(unrated = true).isActive)
        assertFalse(LibraryFilter().isActive)
    }
}
