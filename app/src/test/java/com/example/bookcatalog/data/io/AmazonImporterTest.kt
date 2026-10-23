package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset

class AmazonImporterTest {
    private val header =
        "\"ISBN / ASIN (Amazon ID)\",\"Acquired Date\",\"Ownership\",\"Format\",\"Title\",\"Pages\",\"Genres\",\"First Author\",\"All Authors\"\r\n"

    private fun row(asin: String, title: String) =
        "\"=\"\"$asin\"\"\",\"2020-11-08\",\"Purchase\",\"Kindle eBook\",\"$title\",\"300\",\"History | Travel\",\"Doe, Jane\",\"Jane Doe\"\r\n"

    @Test
    fun addsBooksThenLooksEachOneUp() = runBlocking {
        val store = FakeStore()
        val looked = mutableListOf<Book>()
        val importer = importer(store) { book ->
            looked += book
            EnrichResult(book.copy(isbn13 = "9780000000002", dataSource = "AmazonCSV+OpenLibrary"), matchedIsbn = true, complete = true)
        }

        val summary = importer.import(csv(row("B0000000A1", "First: A Novel"), row("B0000000A2", "Second")))

        assertEquals(2, summary.added)
        assertEquals(2, summary.matched)
        assertEquals(0, summary.remaining)
        // The lookup sees Amazon's details, with genres held in the subjects until they are mapped.
        val first = looked.first()
        assertEquals("First", first.title)
        assertEquals("A Novel", first.subtitle)
        assertEquals("History; Travel", first.subjects)
        assertEquals("B0000000A1", first.asin)
        assertEquals("unread", first.readStatus)
        assertEquals(LocalDate.of(2020, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), first.dateAdded)
        assertTrue(store.books.all { it.dataSource == "AmazonCSV+OpenLibrary" })
    }

    @Test
    fun aSecondImportSkipsKnownBooksAndFinishesUnfinishedOnes() = runBlocking {
        val store = FakeStore()
        var online = false
        val importer = importer(store) { book ->
            if (online) {
                EnrichResult(book.copy(dataSource = "AmazonCSV+HomeChat"), matchedIsbn = false, complete = true)
            } else {
                EnrichResult(book, matchedIsbn = false, complete = false)
            }
        }
        val rows = arrayOf(row("B0000000A1", "First"), row("B0000000A2", "Second"))

        val first = importer.import(csv(*rows))
        assertEquals(2, first.added)
        assertEquals(2, first.remaining)

        online = true
        val second = importer.import(csv(*rows))
        assertEquals(0, second.added)
        assertEquals(2, second.alreadyInLibrary)
        assertEquals(2, second.unmatched)
        assertEquals(0, second.remaining)
        assertEquals(2, store.books.size)
        assertTrue(store.books.all { it.dataSource == "AmazonCSV+HomeChat" })
    }

    @Test
    fun stopsAfterFiveFailuresInARow() = runBlocking {
        val store = FakeStore()
        var calls = 0
        val importer = importer(store) { book ->
            calls++
            EnrichResult(book, matchedIsbn = false, complete = false)
        }
        val rows = (1..8).map { row("B00000000$it", "Book $it") }.toTypedArray()

        val summary = importer.import(csv(*rows))

        assertEquals(5, calls)
        assertTrue(summary.stoppedEarly)
        assertEquals(8, summary.remaining)
    }

    @Test
    fun reportsAFileThatIsNotAnAmazonList() = runBlocking {
        val summary = importer(FakeStore()) { error("not called") }
            .import(ByteArrayInputStream("isbn13,title\r\n".toByteArray()))

        assertTrue(summary.error!!.contains("Amazon"))
    }

    private fun importer(store: FakeStore, enrich: suspend (Book) -> EnrichResult) =
        AmazonImporter(store, { enrich(it) }, zone = ZoneOffset.UTC, now = { 1L }, pause = {})

    private fun csv(vararg rows: String) = ByteArrayInputStream((header + rows.joinToString("")).toByteArray())

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
