package com.example.bookcatalog.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMergerTest {
    @Test
    fun prefersOpenLibraryFieldsAndGoogleSpecificFields() {
        val book = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(
                title = "Open title",
                authors = listOf("Doe, Jane"),
                publisher = "Open publisher",
                publishedDate = "1999",
                pageCount = 120,
                description = "Open description",
                coverUrl = "https://covers.example/open.jpg",
            ),
            googleBooks = BookMetadata(
                title = "Google title",
                authors = listOf("Google, Author"),
                publisher = "Google publisher",
                publishedDate = "1999-05",
                pageCount = 240,
                language = "en",
                description = "Google description",
                coverUrl = "https://covers.example/google.jpg",
            ),
        )

        requireNotNull(book)
        assertEquals("Open Title", book.title)
        assertEquals("Doe, Jane", book.authors)
        assertEquals("Open publisher", book.publisher)
        assertEquals("1999-05", book.publishedDate)
        assertEquals(240, book.pageCount)
        assertEquals("en", book.language)
        assertEquals("Google description", book.description)
        assertEquals("https://covers.example/open.jpg", book.coverSourceUrl)
        assertEquals("OpenLibrary+Google", book.dataSource)
    }

    @Test
    fun usesGoogleWhenOpenLibraryFieldsAreMissing() {
        val book = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(title = "Open title"),
            googleBooks = BookMetadata(title = "Google title", authors = listOf("Doe, Jane")),
        )

        requireNotNull(book)
        assertEquals("Open Title", book.title)
        assertEquals("Doe, Jane", book.authors)
    }

    @Test
    fun returnsNullWhenNeitherSourceHasDetails() {
        assertNull(MetadataMerger.merge("9780306406157", null, null))
        assertTrue(MetadataMerger.merge("9780306406157", BookMetadata(), BookMetadata()) == null)
    }

    @Test
    fun formatsTitleAndSubtitleUsingTitleCase() {
        val book = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(
                title = "the lord of the rings",
                subtitle = "the fellowship of the ring",
            ),
            googleBooks = null,
        )

        requireNotNull(book)
        assertEquals("The Lord of the Rings", book.title)
        assertEquals("The Fellowship of the Ring", book.subtitle)
    }
}
