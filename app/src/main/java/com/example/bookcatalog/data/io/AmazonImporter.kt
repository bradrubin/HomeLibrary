package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.ReadStatus
import kotlinx.coroutines.delay
import java.io.InputStream
import java.time.ZoneId

/** Fills in an Amazon book from a matching print edition and HomeChat. */
fun interface AmazonBookEnricher {
    /** Never throws for a failed lookup; [EnrichResult.complete] is false when it should be tried again. */
    suspend fun enrich(book: Book): EnrichResult
}

data class EnrichResult(
    val book: Book,
    val matchedIsbn: Boolean,
    /** False when a service couldn't be reached, so the book is looked up again next time. */
    val complete: Boolean,
)

data class AmazonImportSummary(
    val added: Int = 0,
    val alreadyInLibrary: Int = 0,
    val libraryLoans: Int = 0,
    val borrowed: Int = 0,
    val unavailable: Int = 0,
    val otherFormats: Int = 0,
    val matched: Int = 0,
    val unmatched: Int = 0,
    /** Books still waiting to be looked up, which another import of the file will finish. */
    val remaining: Int = 0,
    val stoppedEarly: Boolean = false,
    val error: String? = null,
)

/**
 * Imports the Kindle and Audible books from an Amazon book list in two phases, so a long first
 * import can be picked up again: every new book is saved straight away with Amazon's details and
 * [SOURCE] as its data source, then each book still marked that way is looked up and saved in turn.
 * A book whose lookup failed is tried once more at the end. Importing the file again skips books whose ASIN is already in the library and finishes the rest.
 */
class AmazonImporter(
    private val store: LibraryStore,
    private val enricher: AmazonBookEnricher,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun import(input: InputStream, onProgress: (TransferProgress) -> Unit = {}): AmazonImportSummary {
        val list = try {
            AmazonCsv.read(input)
        } catch (exception: IllegalArgumentException) {
            return AmazonImportSummary(error = exception.message ?: "The file isn't an Amazon book list.")
        }
        var summary = AmazonImportSummary(
            libraryLoans = list.libraryLoans,
            borrowed = list.borrowed,
            unavailable = list.unavailable,
            otherFormats = list.otherFormats,
        )
        store.inTransaction {
            val asins = store.allBooks().mapNotNullTo(HashSet()) { it.asin }
            list.items.forEachIndexed { index, item ->
                onProgress(TransferProgress("Adding books", index, list.items.size))
                if (!asins.add(item.asin)) {
                    summary = summary.copy(alreadyInLibrary = summary.alreadyInLibrary + 1)
                } else {
                    store.insert(item.toBook())
                    summary = summary.copy(added = summary.added + 1)
                }
            }
        }

        val pending = store.allBooks().filter { it.dataSource == SOURCE }
        val failed = mutableListOf<Book>()
        var failuresInARow = 0
        for ((index, book) in pending.withIndex()) {
            onProgress(TransferProgress("Looking up books", index, pending.size))
            if (index > 0) pause(LOOKUP_INTERVAL_MS)
            val result = lookUp(book)
            if (result == null) {
                failed += book
                failuresInARow++
                if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                    val notTried = pending.size - index - 1
                    return summary.copy(remaining = failed.size + notTried, stoppedEarly = true)
                }
                continue
            }
            failuresInARow = 0
            summary = summary.counting(result)
        }

        // A failure is usually a model or service that was briefly too slow, so each gets one more try.
        var remaining = failed.size
        for ((index, book) in failed.withIndex()) {
            onProgress(TransferProgress("Retrying books", index, failed.size))
            pause(LOOKUP_INTERVAL_MS)
            val result = lookUp(book) ?: continue
            remaining--
            summary = summary.counting(result)
        }
        return summary.copy(remaining = remaining)
    }

    /** Looks the book up and saves the result, returning null when it should be tried again. */
    private suspend fun lookUp(book: Book): EnrichResult? {
        val result = enricher.enrich(book)
        // The book may have been deleted while it was being looked up.
        if (store.getBook(book.id) != null) {
            store.update(result.book.copy(id = book.id, dateModified = now()))
        }
        return result.takeIf(EnrichResult::complete)
    }

    private fun AmazonImportSummary.counting(result: EnrichResult): AmazonImportSummary =
        if (result.matchedIsbn) copy(matched = matched + 1) else copy(unmatched = unmatched + 1)

    private fun AmazonItem.toBook(): Book = Book(
        title = title,
        subtitle = subtitle,
        authors = authors,
        format = format,
        pageCount = pageCount,
        audioMinutes = audioMinutes,
        // Kept until lookup, when HomeChat turns them into BISAC headings.
        subjects = genres.joinToString("; ").ifEmpty { null },
        asin = asin,
        readStatus = ReadStatus.UNREAD,
        dataSource = SOURCE,
        dateAdded = acquired?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: now(),
        dateModified = now(),
    )

    companion object {
        /** Marks a book that hasn't been looked up yet; lookups add their sources after it. */
        const val SOURCE = "AmazonCSV"
        private const val LOOKUP_INTERVAL_MS = 1_000L
        private const val MAX_FAILURES_IN_A_ROW = 5
    }
}
