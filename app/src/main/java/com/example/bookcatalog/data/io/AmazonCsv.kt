package com.example.bookcatalog.data.io

import com.example.bookcatalog.domain.AuthorNames
import org.apache.commons.csv.CSVFormat
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A Kindle or Audible book from an Amazon book list. */
data class AmazonItem(
    val asin: String,
    val title: String,
    val subtitle: String?,
    /** "; "-separated names in natural order. */
    val authors: String?,
    /** A [com.example.bookcatalog.domain.BookFormat] choice. */
    val format: String,
    val pageCount: Int?,
    val acquired: LocalDate?,
    val genres: List<String>,
)

data class AmazonBookList(
    val items: List<AmazonItem>,
    val libraryLoans: Int = 0,
    /** Kindle Unlimited, Prime Reading and lending library books, and samples. */
    val borrowed: Int = 0,
    /** Rows Amazon no longer has details for. */
    val unavailable: Int = 0,
    /** Print books and anything else that isn't a Kindle or Audible book. */
    val otherFormats: Int = 0,
)

/**
 * Reads the CSV that the Kindle Book List Exporter browser extension saves, which has a few
 * lines of notes above the header and wraps each Amazon ID in an Excel formula.
 */
object AmazonCsv {
    private const val ID = "ISBN / ASIN (Amazon ID)"
    private const val ACQUIRED = "Acquired Date"
    private const val OWNERSHIP = "Ownership"
    private const val FORMAT = "Format"
    private const val TITLE = "Title"
    private const val PAGES = "Pages"
    private const val GENRES = "Genres"
    private const val FIRST_AUTHOR = "First Author"
    private const val ALL_AUTHORS = "All Authors"

    /** Ways of owning a book that keep it in the library; everything else is a loan or a sample. */
    private val ownedTypes = setOf("Purchase", "InsideAmazon", "AudibleComplimentaryOriginal")
    private val formats = mapOf("Kindle eBook" to "Kindle", "Audible Audiobook" to "Audiobook")

    /** Throws [IllegalArgumentException] when the file isn't an Amazon book list. */
    fun read(input: InputStream): AmazonBookList {
        val reader = InputStreamReader(input, Charsets.UTF_8).buffered()
        reader.mark(1)
        if (reader.read() != '﻿'.code) reader.reset()
        val records = try {
            CSVFormat.RFC4180.parse(reader).records
        } catch (exception: UncheckedIOException) {
            throw IllegalArgumentException("The Amazon book list couldn't be read: ${exception.cause?.message}", exception)
        }
        val headerIndex = records.indexOfFirst { it.size() > 0 && it[0].trim() == ID }
        require(headerIndex >= 0) { "The file isn't an Amazon book list: it has no \"$ID\" column." }
        val columns = records[headerIndex].toList().withIndex().associate { (index, name) -> name.trim() to index }

        var list = AmazonBookList(items = emptyList())
        val items = mutableListOf<AmazonItem>()
        for (record in records.drop(headerIndex + 1)) {
            fun value(column: String): String =
                columns[column]?.takeIf { it < record.size() }?.let { record[it].trim() }.orEmpty()

            // The notes at the end of the file have no ID.
            val asin = value(ID).removePrefix("=").trim('"').trim().uppercase()
            if (asin.isEmpty()) continue
            val format = formats[value(FORMAT)]
            val title = value(TITLE)
            list = when {
                format == null -> list.copy(otherFormats = list.otherFormats + 1)
                value(OWNERSHIP) == "PublicLibraryLending" -> list.copy(libraryLoans = list.libraryLoans + 1)
                value(OWNERSHIP) !in ownedTypes -> list.copy(borrowed = list.borrowed + 1)
                title.isEmpty() || title.startsWith("(Information no longer available") ->
                    list.copy(unavailable = list.unavailable + 1)
                else -> {
                    items += AmazonItem(
                        asin = asin,
                        title = title.substringBefore(": ").trim(),
                        subtitle = title.substringAfter(": ", "").trim().takeIf(String::isNotEmpty),
                        authors = authors(format, value(FIRST_AUTHOR), value(ALL_AUTHORS)),
                        format = format,
                        pageCount = value(PAGES).toIntOrNull()?.takeIf { it > 0 },
                        acquired = try {
                            LocalDate.parse(value(ACQUIRED))
                        } catch (exception: DateTimeParseException) {
                            null
                        },
                        genres = value(GENRES).split('|').map(String::trim).filter(String::isNotEmpty),
                    )
                    list
                }
            }
        }
        return list.copy(items = items)
    }

    /**
     * Audible's author list also names narrators and studios, so only the first author is kept
     * for an audiobook. A Kindle book keeps every author, which includes co-authors and translators.
     */
    internal fun authors(format: String, firstAuthor: String, allAuthors: String): String? {
        val names = if (format == "Kindle" && allAuthors.isNotBlank()) {
            // Roles such as "Jesmyn Ward - introduction" are dropped along with their suffix.
            allAuthors.split(", ").map { it.substringBefore(" - ").trim() }.filter(String::isNotEmpty).distinct()
        } else {
            listOf(firstAuthor).filter(String::isNotBlank)
        }
        return AuthorNames.normalize(names.joinToString("; "))
    }
}
