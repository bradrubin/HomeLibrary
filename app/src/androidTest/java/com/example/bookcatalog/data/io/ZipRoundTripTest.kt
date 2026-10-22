package com.example.bookcatalog.data.io

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.db.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ZipRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "zip-round-trip").apply { deleteRecursively() }
    private val databases = mutableListOf<AppDatabase>()

    @After
    fun cleanUp() {
        databases.forEach(AppDatabase::close)
        root.deleteRecursively()
    }

    @Test
    fun zipExportThenImportRestoresIdenticalRowsAndCovers() = runBlocking {
        val sourceCovers = File(root, "source").apply { mkdirs() }
        File(sourceCovers, "9780385533225.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val source = repository()
        val books = listOf(
            Book(
                isbn13 = "9780385533225", isbn10 = "0385533225", title = "Commas, \"Quotes\"",
                authors = "A; B", description = "Line one\nLine two", notes = "Señor", rating = 4,
                readStatus = "reading", dateAdded = day("2026-10-01"), coverFile = "9780385533225.jpg",
                coverSourceUrl = "https://example.com/c.jpg", dataSource = "Import",
            ),
            Book(title = "Manual entry", location = "Den", dataSource = "Import", dateAdded = day("2026-10-02")),
        )
        books.forEach { source.insert(it) }
        val zip = ByteArrayOutputStream()
        LibraryExporter(source, sourceCovers, ZoneOffset.UTC).exportZip(zip)

        val target = repository()
        val targetCovers = File(root, "target")
        val summary = LibraryImporter(
            store = target,
            coverDirectory = targetCovers,
            stagingDirectory = File(root, "staging"),
            fetchCover = { _, _ -> null },
            zone = ZoneOffset.UTC,
        ).import(ByteArrayInputStream(zip.toByteArray()), ImportOptions())

        assertEquals(2, summary.imported)
        assertEquals(
            source.allBooks().map { it.copy(id = 0, dateModified = 0) },
            target.allBooks().map { it.copy(id = 0, dateModified = 0) },
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), File(targetCovers, "9780385533225.jpg").readBytes())
    }

    private fun repository(): BookRepository {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        databases += database
        return BookRepository(database)
    }

    private fun day(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
