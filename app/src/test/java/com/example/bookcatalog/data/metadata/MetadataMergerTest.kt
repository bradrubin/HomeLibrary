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
        assertEquals("1999", book.publishedDate)
        assertEquals(240, book.pageCount)
        assertEquals("English", book.language)
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
    fun spellsOutLanguageAndDefaultsToEnglish() {
        val italian = MetadataMerger.merge(
            isbn13 = "9788893884938",
            openLibrary = null,
            googleBooks = BookMetadata(title = "Ci vuole gusto", language = "it"),
        )
        val unknown = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(title = "Open title"),
            googleBooks = null,
        )

        assertEquals("Italian", italian?.language)
        assertEquals("English", unknown?.language)
    }

    @Test
    fun prefersOpenLibraryFormatThenLibraryOfCongress() {
        val both = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(title = "Title", format = "Softcover"),
            googleBooks = null,
            libraryOfCongress = BookMetadata(title = "Title", format = "Hardcover"),
        )
        val catalogOnly = MetadataMerger.merge(
            isbn13 = "9780306406157",
            openLibrary = BookMetadata(title = "Title"),
            googleBooks = null,
            libraryOfCongress = BookMetadata(title = "Title", format = "Hardcover"),
        )

        assertEquals("Softcover", both?.format)
        assertEquals("Hardcover", catalogOnly?.format)
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

    @Test
    fun prefersLibraryOfCongressBisacSubjects() {
        val book = MetadataMerger.merge(
            isbn13 = "9781250301697",
            openLibrary = BookMetadata(title = "The Silent Patient", subjects = listOf("Open subject")),
            googleBooks = BookMetadata(subjects = listOf("Fiction")),
            libraryOfCongress = BookMetadata(
                title = "The silent patient",
                subjects = listOf("Marriage -- Fiction"),
                bisacSubjects = listOf("FICTION / Thrillers / Psychological", "FICTION / Thrillers / Suspense"),
            ),
        )

        requireNotNull(book)
        assertEquals("The Silent Patient", book.title)
        assertEquals("FICTION / Thrillers / Psychological; FICTION / Thrillers / Suspense", book.subjects)
        assertEquals("OpenLibrary+Google+LibraryOfCongress", book.dataSource)
    }

    @Test
    fun fillsGapsFromLibraryOfCongressWhenGoogleIsMissing() {
        val book = MetadataMerger.merge(
            isbn13 = "9781250301697",
            openLibrary = BookMetadata(title = "Silent patient: a novel", subjects = listOf("Open subject")),
            googleBooks = null,
            libraryOfCongress = BookMetadata(
                title = "The silent patient",
                authors = listOf("Alex Michaelides"),
                pageCount = 325,
                language = "en",
                subjects = listOf("Marriage -- Fiction"),
                description = "Catalog summary",
            ),
        )

        requireNotNull(book)
        assertEquals("Alex Michaelides", book.authors)
        assertEquals(325, book.pageCount)
        assertEquals("English", book.language)
        assertEquals("Marriage -- Fiction", book.subjects)
        assertEquals("Catalog summary", book.description)
        assertEquals("OpenLibrary+LibraryOfCongress", book.dataSource)
    }

    @Test
    fun ignoresLibraryOfCongressRecordWithDifferentTitle() {
        val book = MetadataMerger.merge(
            isbn13 = "9780061120084",
            openLibrary = BookMetadata(title = "To Kill a Mockingbird"),
            googleBooks = null,
            libraryOfCongress = BookMetadata(
                title = "Dhiki ya Mtule",
                bisacSubjects = listOf("FICTION / African American & Black / General"),
            ),
        )

        requireNotNull(book)
        assertNull(book.subjects)
        assertEquals("OpenLibrary", book.dataSource)
    }

    @Test
    fun usesLibraryOfCongressAlone() {
        val book = MetadataMerger.merge(
            isbn13 = "9781250301697",
            openLibrary = null,
            googleBooks = null,
            libraryOfCongress = BookMetadata(title = "The silent patient"),
        )

        requireNotNull(book)
        assertEquals("The Silent Patient", book.title)
        assertEquals("LibraryOfCongress", book.dataSource)
    }
}
