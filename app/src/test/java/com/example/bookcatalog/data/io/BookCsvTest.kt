package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset

class BookCsvTest {
    @Test
    fun writesBomHeaderAndCrlf() {
        val bytes = write(listOf(Book(title = "Plain", dateAdded = epochMillis("2026-10-21"))))
        val text = bytes.toString(Charsets.UTF_8)

        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xff })
        assertTrue(
            text.startsWith(
                "\uFEFFisbn13,isbn10,asin,title,subtitle,authors,publisher,published_date,page_count,audio_minutes,format,language," +
                    "subjects,description,location,notes,rating,read_status,date_added,cover_file,cover_source_url\r\n",
            ),
        )
        assertTrue(text.endsWith(",,,Plain,,,,,,,,,,,,,,,2026-10-21,,\r\n"))
    }

    @Test
    fun roundTripsTrickyText() {
        val book = Book(
            isbn13 = "9780385533225",
            title = "Commas, \"Quotes\" and More",
            authors = "Brontë, Charlotte; Žižek, Slavoj",
            description = "First line\nSecond line, with comma\r\nThird \"quoted\" line",
            notes = "日本語のメモ",
            rating = 4,
            dateAdded = epochMillis("2026-10-21"),
        )

        val row = BookCsv.read(ByteArrayInputStream(write(listOf(book)))).rows.single()

        assertEquals("Commas, \"Quotes\" and More", row.values[CsvColumn.TITLE])
        assertEquals("Brontë, Charlotte; Žižek, Slavoj", row.values[CsvColumn.AUTHORS])
        assertEquals(book.description, row.values[CsvColumn.DESCRIPTION])
        assertEquals("日本語のメモ", row.values[CsvColumn.NOTES])
        assertEquals("", row.values[CsvColumn.SUBTITLE])
        assertEquals("4", row.values[CsvColumn.RATING])
        assertEquals("2026-10-21", row.values[CsvColumn.DATE_ADDED])
    }

    @Test
    fun mapsHeadersInAnyOrderAndCaseIgnoringExtras() {
        val csv = "Shelf Color,TITLE,isbn13\nred,Dune,9780441013593\n"

        val rows = BookCsv.read(ByteArrayInputStream(csv.toByteArray())).rows

        assertEquals(1, rows.size)
        assertEquals(2, rows[0].rowNumber)
        assertEquals(mapOf(CsvColumn.TITLE to "Dune", CsvColumn.ISBN13 to "9780441013593"), rows[0].values)
    }

    @Test
    fun leavesOutColumnsMissingFromShortRows() {
        val csv = "title,isbn13,notes\nDune\n"

        val row = BookCsv.read(ByteArrayInputStream(csv.toByteArray())).rows.single()

        assertEquals("Dune", row.values[CsvColumn.TITLE])
        assertNull(row.values[CsvColumn.NOTES])
    }

    @Test
    fun requiresTitleColumn() {
        assertThrows(IllegalArgumentException::class.java) {
            BookCsv.read(ByteArrayInputStream("isbn13,authors\n9780441013593,Herbert\n".toByteArray()))
        }
    }

    @Test
    fun keepsRowsBeforeMalformedQuote() {
        val csv = "title,notes\nDune,fine\nEmma,\"never closed\n"

        val result = BookCsv.read(ByteArrayInputStream(csv.toByteArray()))

        assertEquals(listOf("Dune"), result.rows.map { it.values[CsvColumn.TITLE] })
        assertNotNull(result.error)
        assertTrue(result.error!!, result.error!!.startsWith("Row 3"))
    }

    private fun write(books: List<Book>): ByteArray =
        ByteArrayOutputStream().also { BookCsv.write(books, it, ZoneOffset.UTC) }.toByteArray()

    private fun epochMillis(date: String) =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
