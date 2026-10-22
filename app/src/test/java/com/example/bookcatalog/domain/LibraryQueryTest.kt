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
    fun putsBooksMissingTheSortedFieldLast() {
        assertEquals(listOf(dune, odyssey, amelie), LibraryQuery.sort(listOf(amelie, odyssey, dune), SortOrder.AUTHOR))
        assertEquals(
            listOf(odyssey, dune, amelie),
            LibraryQuery.sort(listOf(amelie, dune, odyssey), SortOrder.PUBLICATION_YEAR),
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
}
