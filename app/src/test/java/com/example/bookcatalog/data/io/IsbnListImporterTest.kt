package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnListImporterTest {
    @Test
    fun parsesLinesAndCommasAndConvertsIsbn10() {
        val list = IsbnList.parse("978-0-385-53322-5\r\n0385533225, 9780306406157 ;\n\nnot-an-isbn,\n")

        // The ISBN-10 is the same book as the first entry, so it isn't repeated.
        assertEquals(listOf("9780385533225", "9780306406157"), list.isbns)
        assertEquals(listOf("not-an-isbn"), list.invalid)
    }

    @Test
    fun addsEachLookedUpBookAndSkipsOnesAlreadyInTheLibrary() = runBlocking {
        val store = FakeStore()
        store.insert(Book(title = "Owned", isbn13 = "9780385533225"))
        val looked = mutableListOf<String>()
        val importer = importer(store) { isbn13 ->
            looked += isbn13
            when (isbn13) {
                "9780306406157" -> MetadataLookupResult(Book(isbn13 = isbn13, title = "Found"), warning = "No cover was found for this ISBN.")
                else -> MetadataLookupResult(Book(isbn13 = isbn13, title = ""), message = "No book details were found.")
            }
        }

        val summary = importer.import("9780385533225\n9780306406157\n9781861972712\nbad")

        assertEquals(listOf("9780306406157", "9781861972712"), looked)
        assertEquals(1, summary.added)
        assertEquals(1, summary.alreadyInLibrary)
        assertEquals(1, summary.withWarnings)
        assertEquals(listOf("9781861972712"), summary.notFound)
        assertEquals(listOf("bad"), summary.invalid)
        val added = store.books.single { it.title == "Found" }
        assertEquals("unread", added.readStatus)
        assertEquals(1L, added.dateAdded)
    }

    @Test
    fun retriesALookupThatCouldNotReachTheServicesOnce() = runBlocking {
        val store = FakeStore()
        val looked = mutableListOf<String>()
        val importer = importer(store) { isbn13 ->
            looked += isbn13
            if (isbn13 == "9780385533225" && looked.count { it == isbn13 } == 1) {
                MetadataLookupResult(Book(isbn13 = isbn13, title = ""), sourcesFailed = true)
            } else {
                MetadataLookupResult(Book(isbn13 = isbn13, title = "Book $isbn13"))
            }
        }

        val summary = importer.import("9780385533225, 9780306406157")

        assertEquals(listOf("9780385533225", "9780306406157", "9780385533225"), looked)
        assertEquals(2, summary.added)
        assertTrue(summary.failed.isEmpty())
    }

    @Test
    fun stopsAfterFiveFailuresInARow() = runBlocking {
        var calls = 0
        val importer = importer(FakeStore()) { isbn13 ->
            calls++
            MetadataLookupResult(Book(isbn13 = isbn13, title = ""), sourcesFailed = true)
        }
        val isbns = listOf(
            "9780385533225", "9780306406157", "9781861972712", "9780140449136",
            "9780262033848", "9780131103627", "9780201633610",
        )

        val summary = importer.import(isbns.joinToString("\n"))

        assertEquals(5, calls)
        assertTrue(summary.stoppedEarly)
        assertEquals(isbns, summary.failed)
    }

    private fun importer(store: FakeStore, lookup: suspend (String) -> MetadataLookupResult) =
        IsbnListImporter(store, lookup, now = { 1L }, pause = {})

    private class FakeStore : LibraryStore {
        val books = mutableListOf<Book>()

        override suspend fun allBooks(): List<Book> = books.toList()

        override suspend fun getBook(id: Long): Book? = books.firstOrNull { it.id == id }

        override suspend fun insert(book: Book): Long {
            val id = (books.maxOfOrNull(Book::id) ?: 0) + 1
            books += book.copy(id = id)
            return id
        }

        override suspend fun update(book: Book) {
            books[books.indexOfFirst { it.id == book.id }] = book
        }

        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
}
