package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.db.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchedDetailsTest {
    private val isbn = "9780306406157"

    @Test
    fun notFoundWhenEverySourceAnswered() {
        val details = FetchedDetails(book = null, allSourcesFailed = false)

        assertEquals("No book details were found for ISBN $isbn.", details.notFoundMessage(isbn))
    }

    @Test
    fun notFoundSaysWhichSourceTimedOut() {
        val details = FetchedDetails(
            book = null,
            allSourcesFailed = false,
            sourceFailures = mapOf("Open Library" to SourceFailure.TIMED_OUT),
        )

        assertEquals(
            "No book details were found for ISBN $isbn, but Open Library didn't respond in time, " +
                "so try again in a few minutes.",
            details.notFoundMessage(isbn),
        )
    }

    @Test
    fun everySourceUnreachableSuggestsCheckingTheConnection() {
        val details = FetchedDetails(
            book = null,
            allSourcesFailed = true,
            sourceFailures = mapOf(
                "Open Library" to SourceFailure.UNREACHABLE,
                "Google Books" to SourceFailure.UNREACHABLE,
            ),
        )

        assertEquals(
            "Couldn’t connect to the book lookup services. Check your internet connection and try again.",
            details.notFoundMessage(isbn),
        )
    }

    @Test
    fun everySourceFailingInDifferentWaysListsEachOne() {
        val details = FetchedDetails(
            book = null,
            allSourcesFailed = true,
            sourceFailures = mapOf(
                "Open Library" to SourceFailure.TIMED_OUT,
                "Google Books" to SourceFailure.ERROR,
                "the Library of Congress" to SourceFailure.UNREACHABLE,
            ),
        )

        assertEquals(
            "Couldn’t look up ISBN $isbn: Open Library didn't respond in time, Google Books returned " +
                "an error and the Library of Congress couldn't be reached. Try again in a few minutes.",
            details.notFoundMessage(isbn),
        )
    }

    @Test
    fun foundBookWarnsThatASourceTimedOut() {
        val details = FetchedDetails(
            book = Book(isbn13 = isbn, title = "Found"),
            allSourcesFailed = false,
            sourceFailures = mapOf("the Library of Congress" to SourceFailure.TIMED_OUT),
        )

        assertEquals(
            "The Library of Congress didn't respond in time, so some details may be missing.",
            details.partialWarning,
        )
    }

    @Test
    fun foundBookFromEverySourceHasNoWarning() {
        assertNull(FetchedDetails(book = Book(isbn13 = isbn, title = "Found"), allSourcesFailed = false).partialWarning)
    }
}
