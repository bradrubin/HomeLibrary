package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UncheckedIOException
import java.time.Instant
import java.time.ZoneId

/** CSV columns in export order. */
enum class CsvColumn(val header: String) {
    ISBN13("isbn13"),
    ISBN10("isbn10"),
    TITLE("title"),
    SUBTITLE("subtitle"),
    AUTHORS("authors"),
    PUBLISHER("publisher"),
    PUBLISHED_DATE("published_date"),
    PAGE_COUNT("page_count"),
    FORMAT("format"),
    LANGUAGE("language"),
    SUBJECTS("subjects"),
    DESCRIPTION("description"),
    LOCATION("location"),
    NOTES("notes"),
    RATING("rating"),
    READ_STATUS("read_status"),
    DATE_ADDED("date_added"),
    COVER_FILE("cover_file"),
    COVER_SOURCE_URL("cover_source_url"),
}

/** One data row, holding only the columns the file has. [rowNumber] counts the header as row 1, as a spreadsheet does. */
data class CsvRow(
    val rowNumber: Int,
    val values: Map<CsvColumn, String>,
)

data class CsvReadResult(
    val rows: List<CsvRow>,
    /** Set when the file stops parsing partway, such as at an unclosed quote. */
    val error: String? = null,
)

/**
 * Reads and writes the catalog CSV: UTF-8 with a byte order mark so Excel shows accented
 * characters, RFC 4180 quoting and CRLF line endings.
 */
object BookCsv {
    private const val BYTE_ORDER_MARK = '\uFEFF'

    fun write(books: List<Book>, output: OutputStream, zone: ZoneId = ZoneId.systemDefault()) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        writer.write(BYTE_ORDER_MARK.code)
        val format = CSVFormat.RFC4180.builder()
            .setHeader(*CsvColumn.entries.map(CsvColumn::header).toTypedArray())
            .get()
        // Not closed with use, since closing would also close the caller's stream.
        val printer = CSVPrinter(writer, format)
        for (book in books) {
            printer.printRecord(CsvColumn.entries.map { column -> book.valueFor(column, zone) })
        }
        printer.flush()
    }

    /** Throws [IllegalArgumentException] when the file has no title column. */
    fun read(input: InputStream): CsvReadResult {
        val reader = InputStreamReader(input, Charsets.UTF_8).buffered()
        reader.mark(1)
        if (reader.read() != BYTE_ORDER_MARK.code) reader.reset()
        val parser = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setAllowMissingColumnNames(true)
            .setAllowDuplicateHeaderNames(true)
            .get()
            .parse(reader)
        val columnsByHeader = CsvColumn.entries.associateBy(CsvColumn::header)
        val columns = parser.headerNames.mapNotNull { name ->
            columnsByHeader[name.trim().lowercase()]?.let { column -> column to name }
        }.toMap()
        require(CsvColumn.TITLE in columns) { "The file has no title column." }

        val rows = mutableListOf<CsvRow>()
        val records = parser.iterator()
        while (true) {
            val record = try {
                if (!records.hasNext()) break
                records.next()
            } catch (exception: UncheckedIOException) {
                return CsvReadResult(rows, "Row ${rows.size + 2}: ${exception.cause?.message ?: "the file is malformed"}")
            } catch (exception: IllegalStateException) {
                return CsvReadResult(rows, "Row ${rows.size + 2}: ${exception.message ?: "the file is malformed"}")
            }
            val values = columns.mapNotNull { (column, name) ->
                record.takeIf { it.isSet(name) }?.get(name)?.let { column to it }
            }.toMap()
            rows += CsvRow(rowNumber = rows.size + 2, values = values)
        }
        return CsvReadResult(rows)
    }

    private fun Book.valueFor(column: CsvColumn, zone: ZoneId): String? = when (column) {
        CsvColumn.ISBN13 -> isbn13
        CsvColumn.ISBN10 -> isbn10
        CsvColumn.TITLE -> title
        CsvColumn.SUBTITLE -> subtitle
        CsvColumn.AUTHORS -> authors
        CsvColumn.PUBLISHER -> publisher
        CsvColumn.PUBLISHED_DATE -> publishedDate
        CsvColumn.PAGE_COUNT -> pageCount?.toString()
        CsvColumn.FORMAT -> format
        CsvColumn.LANGUAGE -> language
        CsvColumn.SUBJECTS -> subjects
        CsvColumn.DESCRIPTION -> description
        CsvColumn.LOCATION -> location
        CsvColumn.NOTES -> notes
        CsvColumn.RATING -> rating?.toString()
        CsvColumn.READ_STATUS -> readStatus
        CsvColumn.DATE_ADDED -> Instant.ofEpochMilli(dateAdded).atZone(zone).toLocalDate().toString()
        CsvColumn.COVER_FILE -> coverFile
        CsvColumn.COVER_SOURCE_URL -> coverSourceUrl
    }
}
