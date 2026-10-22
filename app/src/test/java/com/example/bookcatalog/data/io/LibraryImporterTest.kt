package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryImporterTest {
    private val root = Files.createTempDirectory("library-import").toFile()
    private val covers = File(root, "covers")
    private val staging = File(root, "staging")
    private val fetches = mutableListOf<Pair<String, String>>()
    private val pauses = mutableListOf<Long>()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun zipBundleRoundTripRestoresRowsAndCovers() {
        val source = FakeLibraryStore()
        val sourceCovers = File(root, "source-covers").apply { mkdirs() }
        File(sourceCovers, "9780385533225.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(sourceCovers, "user-1.jpg").writeBytes(byteArrayOf(4, 5))
        source.books += listOf(
            Book(
                id = 1, isbn13 = "9780385533225", isbn10 = "0385533225", title = "First, \"Quoted\"",
                authors = "A; B", publishedDate = "May 1999", pageCount = 320, language = "en",
                subjects = "FICTION / General", description = "Line one\nLine two", location = "Den",
                notes = "Señor", rating = 5, readStatus = "read", dateAdded = day("2026-10-01"),
                coverFile = "9780385533225.jpg", coverSourceUrl = "https://example.com/c.jpg", dataSource = "Import",
            ),
            Book(id = 2, title = "No ISBN", coverFile = "user-1.jpg", coverSourceUrl = "user-photo", dataSource = "Import", dateAdded = day("2026-10-02")),
        )
        val zip = ByteArrayOutputStream()
        runBlocking { LibraryExporter(source, sourceCovers, ZoneOffset.UTC).exportZip(zip) }

        val target = FakeLibraryStore()
        val summary = runBlocking { importer(target).import(ByteArrayInputStream(zip.toByteArray()), ImportOptions()) }

        assertEquals(2, summary.imported)
        assertEquals(2, summary.coversCopied)
        assertEquals(
            source.books.map { it.copy(id = 0, dateModified = 0) },
            target.books.map { it.copy(id = 0, dateModified = 0) },
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), File(covers, "9780385533225.jpg").readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(covers, "user-1.jpg").readBytes())
        assertFalse(staging.exists())
    }

    @Test
    fun skipsDuplicatesByDefault() {
        val store = FakeLibraryStore(Book(id = 7, isbn13 = "9780441013593", title = "Dune", notes = "mine"))

        val summary = import(store, "title,isbn13,notes\nDune,978-0-441-01359-3,theirs\n")

        assertEquals(1, summary.skipped)
        assertEquals("mine", store.books.single().notes)
    }

    @Test
    fun overwriteKeepsFieldsTheFileHasNoColumnFor() {
        val store = FakeLibraryStore(
            Book(id = 7, isbn13 = "9780441013593", title = "Dune", notes = "mine", location = "Den", dateAdded = day("2020-01-01")),
        )

        val summary = import(store, "title,isbn13,notes\nDune (Deluxe),9780441013593,\n", DuplicateHandling.OVERWRITE)

        assertEquals(1, summary.updated)
        val book = store.books.single()
        assertEquals(7, book.id)
        assertEquals("Dune (Deluxe)", book.title)
        assertNull(book.notes)
        assertEquals("Den", book.location)
        assertEquals(day("2020-01-01"), book.dateAdded)
    }

    @Test
    fun keepBothAddsAnotherCopy() {
        val store = FakeLibraryStore(Book(id = 7, isbn13 = "9780441013593", title = "Dune"))

        val summary = import(store, "title,isbn10\nDune,0441013597\n", DuplicateHandling.KEEP_BOTH)

        assertEquals(1, summary.imported)
        assertEquals(listOf("9780441013593", "9780441013593"), store.books.map { it.isbn13 })
    }

    @Test
    fun copiesWithinOneFileAreNotTreatedAsDuplicates() {
        val store = FakeLibraryStore()

        val summary = import(store, "title,isbn13\nDune,9780441013593\nDune,9780441013593\n")

        assertEquals(2, summary.imported)
    }

    @Test
    fun blanksInvalidIsbnAndReportsBadRows() {
        val store = FakeLibraryStore()

        val summary = import(
            store,
            "title,isbn13,rating,read_status,date_added\n" +
                "Dune,9780441013590,9,Finished,yesterday\n" +
                ",9780441013593,,,\n" +
                "Emma,,3,READ,2026-10-21\n",
        )

        assertEquals(2, summary.imported)
        assertEquals(listOf(2), summary.invalidIsbnRows)
        assertEquals(listOf("Row 3: no title, so the row was skipped."), summary.errors)
        assertEquals(
            listOf(
                "Row 2: Rating \"9\" was ignored.",
                "Row 2: Read status \"finished\" was ignored.",
                "Row 2: Date added \"yesterday\" was ignored.",
            ),
            summary.warnings,
        )
        val (dune, emma) = store.books
        assertNull(dune.isbn13)
        assertEquals(3, emma.rating)
        assertEquals("read", emma.readStatus)
        assertEquals(day("2026-10-21"), emma.dateAdded)
    }

    @Test
    fun renamesBundledCoverThatClashesWithADifferentFile() {
        covers.mkdirs()
        File(covers, "9780441013593.jpg").writeBytes(byteArrayOf(9))
        val bundle = zipOf(
            "books.csv" to "title,isbn13,cover_file\nDune,9780441013593,9780441013593.jpg\n".toByteArray(),
            "covers/9780441013593.jpg" to byteArrayOf(1),
        )

        val store = FakeLibraryStore()
        runBlocking { importer(store).import(ByteArrayInputStream(bundle), ImportOptions()) }

        val coverFile = store.books.single().coverFile!!
        assertTrue(coverFile, coverFile.startsWith("import-"))
        assertArrayEquals(byteArrayOf(1), File(covers, coverFile).readBytes())
        assertArrayEquals(byteArrayOf(9), File(covers, "9780441013593.jpg").readBytes())
    }

    @Test
    fun ignoresBundleEntriesOutsideTheCoversFolder() {
        val bundle = zipOf(
            "books.csv" to "title,cover_file\nDune,../evil.jpg\n".toByteArray(),
            "covers/../evil.jpg" to byteArrayOf(1),
        )

        val store = FakeLibraryStore()
        runBlocking { importer(store).import(ByteArrayInputStream(bundle), ImportOptions()) }

        assertNull(store.books.single().coverFile)
        assertFalse(File(root, "evil.jpg").exists())
    }

    @Test
    fun fetchesMissingCoversOnePerSecondWhenAsked() {
        val store = FakeLibraryStore()
        val csv = "title,isbn13,cover_file,cover_source_url\n" +
            "Dune,9780441013593,gone.jpg,https://example.com/dune.jpg\n" +
            "Emma,9780141439587,,https://example.com/emma.jpg\n" +
            "Mine,9780140449136,,user-photo\n"

        val summary = runBlocking {
            importer(store).import(ByteArrayInputStream(csv.toByteArray()), ImportOptions(fetchMissingCovers = true))
        }

        assertEquals(
            listOf("9780441013593" to "https://example.com/dune.jpg", "9780141439587" to "https://example.com/emma.jpg"),
            fetches,
        )
        assertEquals(listOf(1_000L), pauses)
        assertEquals(2, summary.coversFetched)
        assertEquals(listOf("9780441013593.jpg", "9780141439587.jpg", null), store.books.map { it.coverFile })
    }

    @Test
    fun reportsZipWithoutCsv() {
        val bundle = zipOf("covers/a.jpg" to byteArrayOf(1))

        val summary = runBlocking { importer(FakeLibraryStore()).import(ByteArrayInputStream(bundle), ImportOptions()) }

        assertEquals(listOf("The ZIP file has no books.csv."), summary.errors)
    }

    private fun import(
        store: FakeLibraryStore,
        csv: String,
        duplicates: DuplicateHandling = DuplicateHandling.SKIP,
    ): ImportSummary = runBlocking {
        importer(store).import(ByteArrayInputStream(csv.toByteArray()), ImportOptions(duplicates))
    }

    private fun importer(store: FakeLibraryStore) = LibraryImporter(
        store = store,
        coverDirectory = covers,
        stagingDirectory = staging,
        fetchCover = { isbn13, url ->
            fetches += isbn13 to url
            "$isbn13.jpg"
        },
        zone = ZoneOffset.UTC,
        now = { 0L },
        pause = { pauses += it },
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun day(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private class FakeLibraryStore(vararg initial: Book) : LibraryStore {
        val books = initial.toMutableList()

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
