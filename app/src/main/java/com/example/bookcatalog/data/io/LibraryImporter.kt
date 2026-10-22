package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.BookFormat
import com.example.bookcatalog.domain.IsbnUtils
import com.example.bookcatalog.domain.PublicationYear
import com.example.bookcatalog.domain.ReadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Imports a catalog CSV, or a ZIP bundle of books.csv plus a covers folder. Books with an ISBN
 * already in the library are handled by [ImportOptions.duplicates]; all rows are written in one
 * transaction, and missing covers are fetched afterwards at about one per second.
 */
class LibraryImporter(
    private val store: LibraryStore,
    private val coverDirectory: File,
    /** Scratch space for covers unpacked from a bundle; emptied after each import. */
    private val stagingDirectory: File,
    /** Downloads a cover and returns its file name, or null when the image isn't usable. */
    private val fetchCover: suspend (isbn13: String, url: String) -> String?,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun import(
        input: InputStream,
        options: ImportOptions,
        onProgress: (TransferProgress) -> Unit = {},
    ): ImportSummary {
        stagingDirectory.deleteRecursively()
        try {
            val csv = unpack(input) ?: return ImportSummary(errors = listOf("The ZIP file has no ${ZipBundle.CSV_ENTRY}."))
            val parsed = try {
                BookCsv.read(ByteArrayInputStream(csv))
            } catch (exception: IllegalArgumentException) {
                return ImportSummary(errors = listOf(exception.message ?: "The file isn't a book catalog CSV."))
            }
            return importRows(parsed, options, onProgress)
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }

    /** Returns the CSV bytes, unpacking covers to the staging folder when the input is a ZIP. */
    private fun unpack(input: InputStream): ByteArray? {
        val buffered = BufferedInputStream(input)
        buffered.mark(ZIP_SIGNATURE.size)
        val start = ByteArray(ZIP_SIGNATURE.size)
        val read = buffered.readNBytes(start, 0, start.size)
        buffered.reset()
        if (read < ZIP_SIGNATURE.size || !start.contentEquals(ZIP_SIGNATURE)) return buffered.readBytes()

        var csv: ByteArray? = null
        stagingDirectory.mkdirs()
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/').removePrefix("/")
                when {
                    entry.isDirectory -> Unit
                    name.equals(ZipBundle.CSV_ENTRY, ignoreCase = true) -> csv = zip.readBytes()
                    name.startsWith(ZipBundle.COVER_FOLDER) -> {
                        val fileName = name.removePrefix(ZipBundle.COVER_FOLDER)
                        if (ZipBundle.isSafeCoverName(fileName)) {
                            File(stagingDirectory, fileName).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        return csv
    }

    private suspend fun importRows(
        parsed: CsvReadResult,
        options: ImportOptions,
        onProgress: (TransferProgress) -> Unit,
    ): ImportSummary {
        var summary = ImportSummary()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val invalidIsbnRows = mutableListOf<Int>()
        val coversToFetch = mutableListOf<CoverRequest>()

        store.inTransaction {
            // Duplicates are judged against the library as it was, so copies within the file all import.
            val existingByIsbn = store.allBooks()
                .filter { it.isbn13 != null }
                .groupBy { requireNotNull(it.isbn13) }
                .mapValues { (_, books) -> books.minBy(Book::id) }
            parsed.rows.forEachIndexed { index, row ->
                onProgress(TransferProgress("Importing books", index, parsed.rows.size))
                val title = row.values[CsvColumn.TITLE]?.trim().orEmpty()
                if (title.isEmpty()) {
                    errors += "Row ${row.rowNumber}: no title, so the row was skipped."
                    return@forEachIndexed
                }
                val isbn = parseIsbn(row)
                if (isbn is IsbnValue.Invalid) invalidIsbnRows += row.rowNumber
                val isbn13 = (isbn as? IsbnValue.Valid)?.isbn13
                val existing = isbn13?.let(existingByIsbn::get)
                val base = when {
                    existing == null -> null
                    options.duplicates == DuplicateHandling.SKIP -> {
                        summary = summary.copy(skipped = summary.skipped + 1)
                        return@forEachIndexed
                    }
                    options.duplicates == DuplicateHandling.OVERWRITE -> existing
                    else -> null
                }
                val rowWarnings = mutableListOf<String>()
                var book = row.applyTo(base ?: Book(title = title, dataSource = IMPORT_SOURCE), isbn, rowWarnings)
                val cover = installCover(row, book)
                if (cover.copied) summary = summary.copy(coversCopied = summary.coversCopied + 1)
                book = cover.book
                warnings += rowWarnings.map { "Row ${row.rowNumber}: $it" }
                val id = if (base != null) {
                    store.update(book.copy(id = base.id, dateModified = now()))
                    summary = summary.copy(updated = summary.updated + 1)
                    base.id
                } else {
                    summary = summary.copy(imported = summary.imported + 1)
                    store.insert(book.copy(id = 0, dateModified = now()))
                }
                val url = cover.missingUrl
                if (options.fetchMissingCovers && url != null && isbn13 != null) {
                    coversToFetch += CoverRequest(id, isbn13, url)
                }
            }
        }
        parsed.error?.let { errors += "$it. Rows from there on were not imported." }

        coversToFetch.forEachIndexed { index, request ->
            onProgress(TransferProgress("Fetching covers", index, coversToFetch.size))
            if (index > 0) pause(COVER_FETCH_INTERVAL_MS)
            val fileName = try {
                fetchCover(request.isbn13, request.url)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                null
            } catch (exception: IllegalArgumentException) {
                null
            }
            val book = fileName?.let { store.getBook(request.bookId) }
            if (book != null) {
                store.update(book.copy(coverFile = fileName, coverSourceUrl = request.url))
                summary = summary.copy(coversFetched = summary.coversFetched + 1)
            } else {
                summary = summary.copy(coverFetchFailures = summary.coverFetchFailures + 1)
            }
        }
        return summary.copy(invalidIsbnRows = invalidIsbnRows, errors = errors, warnings = warnings)
    }

    private fun parseIsbn(row: CsvRow): IsbnValue {
        if (CsvColumn.ISBN13 !in row.values && CsvColumn.ISBN10 !in row.values) return IsbnValue.Absent
        val raw = listOf(CsvColumn.ISBN13, CsvColumn.ISBN10)
            .mapNotNull { row.values[it]?.trim()?.takeIf(String::isNotEmpty) }
        if (raw.isEmpty()) return IsbnValue.Blank
        val isbn13 = raw.firstNotNullOfOrNull(IsbnUtils::toIsbn13) ?: return IsbnValue.Invalid
        return IsbnValue.Valid(isbn13)
    }

    /** Copies the row's cover from the bundle, or works out which URL to fetch it from later. */
    private fun installCover(row: CsvRow, book: Book): CoverResult {
        if (CsvColumn.COVER_FILE !in row.values && CsvColumn.COVER_SOURCE_URL !in row.values) {
            return CoverResult(book)
        }
        val name = row.values[CsvColumn.COVER_FILE]?.trim().orEmpty()
        val url = row.values[CsvColumn.COVER_SOURCE_URL]?.trim()?.takeIf(String::isNotEmpty)
        val staged = File(stagingDirectory, name).takeIf { ZipBundle.isSafeCoverName(name) && it.isFile }
        if (staged != null) {
            coverDirectory.mkdirs()
            var target = File(coverDirectory, name)
            if (target.exists() && !target.readBytes().contentEquals(staged.readBytes())) {
                target = File(coverDirectory, "import-${UUID.randomUUID()}.jpg")
            }
            if (!target.exists()) staged.copyTo(target)
            return CoverResult(book.copy(coverFile = target.name, coverSourceUrl = url), copied = true)
        }
        // A CSV re-imported on the same phone can point at a cover that is already here.
        if (ZipBundle.isSafeCoverName(name) && File(coverDirectory, name).isFile) {
            return CoverResult(book.copy(coverFile = name, coverSourceUrl = url))
        }
        val fetchable = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        return CoverResult(book.copy(coverFile = null, coverSourceUrl = url), missingUrl = fetchable)
    }

    /** Sets each field the file has a column for; columns the file lacks leave [base] unchanged. */
    private fun CsvRow.applyTo(base: Book, isbn: IsbnValue, warnings: MutableList<String>): Book {
        fun text(column: CsvColumn, current: String?): String? =
            if (column in values) values[column]?.trim()?.takeIf(String::isNotEmpty) else current

        fun number(column: CsvColumn, label: String, current: Int?, range: IntRange): Int? {
            if (column !in values) return current
            val raw = values[column]?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return raw.toIntOrNull()?.takeIf { it in range } ?: run {
                warnings += "$label \"$raw\" was ignored."
                null
            }
        }

        val (isbn13, isbn10) = when (isbn) {
            IsbnValue.Absent -> base.isbn13 to base.isbn10
            IsbnValue.Blank, IsbnValue.Invalid -> null to null
            is IsbnValue.Valid -> isbn.isbn13 to IsbnUtils.isbn13To10(isbn.isbn13)
        }
        fun year(): String? {
            val raw = text(CsvColumn.PUBLISHED_DATE, base.publishedDate) ?: return null
            return PublicationYear.from(raw) ?: run {
                warnings += "Published date \"$raw\" has no year and was ignored."
                null
            }
        }

        fun format(): String? {
            val raw = text(CsvColumn.FORMAT, base.format) ?: return null
            return BookFormat.normalize(raw) ?: run {
                warnings += "Format \"$raw\" was ignored."
                null
            }
        }

        fun readStatus(): String? {
            if (CsvColumn.READ_STATUS !in values) return base.readStatus
            val raw = values[CsvColumn.READ_STATUS]?.trim()?.lowercase().orEmpty()
            return when {
                raw.isEmpty() -> null
                raw in ReadStatus.choices -> raw
                else -> {
                    warnings += "Read status \"$raw\" was ignored."
                    null
                }
            }
        }
        return base.copy(
            isbn13 = isbn13,
            isbn10 = isbn10,
            title = requireNotNull(text(CsvColumn.TITLE, base.title)),
            subtitle = text(CsvColumn.SUBTITLE, base.subtitle),
            authors = text(CsvColumn.AUTHORS, base.authors),
            publisher = text(CsvColumn.PUBLISHER, base.publisher),
            publishedDate = year(),
            pageCount = number(CsvColumn.PAGE_COUNT, "Page count", base.pageCount, 1..100_000),
            format = format(),
            language = text(CsvColumn.LANGUAGE, base.language),
            subjects = text(CsvColumn.SUBJECTS, base.subjects),
            description = text(CsvColumn.DESCRIPTION, base.description),
            location = text(CsvColumn.LOCATION, base.location),
            notes = text(CsvColumn.NOTES, base.notes),
            rating = number(CsvColumn.RATING, "Rating", base.rating, 0..5),
            readStatus = readStatus(),
            dateAdded = parseDateAdded(values[CsvColumn.DATE_ADDED], base.dateAdded, warnings),
        )
    }

    private fun parseDateAdded(raw: String?, current: Long, warnings: MutableList<String>): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return current
        return try {
            LocalDate.parse(value.take(10)).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (exception: DateTimeParseException) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (exception: DateTimeParseException) {
                warnings += "Date added \"$value\" was ignored."
                current
            }
        }
    }

    private sealed interface IsbnValue {
        /** The file has no ISBN columns. */
        data object Absent : IsbnValue
        data object Blank : IsbnValue
        data object Invalid : IsbnValue
        data class Valid(val isbn13: String) : IsbnValue
    }

    private data class CoverResult(
        val book: Book,
        val copied: Boolean = false,
        val missingUrl: String? = null,
    )

    private data class CoverRequest(val bookId: Long, val isbn13: String, val url: String)

    private companion object {
        val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        const val IMPORT_SOURCE = "Import"
        const val COVER_FETCH_INTERVAL_MS = 1_000L
    }
}
