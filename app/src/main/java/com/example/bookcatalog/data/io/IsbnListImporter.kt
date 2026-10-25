package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.metadata.MetadataLookupResult
import com.example.bookcatalog.domain.IsbnUtils
import com.example.bookcatalog.domain.ReadStatus
import kotlinx.coroutines.delay

/** The ISBNs in a pasted list, as ISBN-13s without repeats, and the entries that weren't valid ISBNs. */
data class IsbnList(
    val isbns: List<String>,
    val invalid: List<String>,
) {
    companion object {
        /** Entries can be separated by line breaks, commas or semicolons; hyphens and spaces inside one are fine. */
        fun parse(text: String): IsbnList {
            val entries = text.split('\n', '\r', ',', ';').map(String::trim).filter(String::isNotEmpty)
            val isbns = LinkedHashSet<String>()
            val invalid = mutableListOf<String>()
            entries.forEach { entry -> IsbnUtils.toIsbn13(entry)?.let(isbns::add) ?: invalid.add(entry) }
            return IsbnList(isbns.toList(), invalid)
        }
    }
}

data class IsbnImportSummary(
    val added: Int = 0,
    val alreadyInLibrary: Int = 0,
    /** ISBNs no service had details for, to be entered by hand. */
    val notFound: List<String> = emptyList(),
    val invalid: List<String> = emptyList(),
    /** ISBNs whose lookup couldn't reach any service, which importing the list again will retry. */
    val failed: List<String> = emptyList(),
    /** Added books whose cover, subjects or description couldn't be filled in. */
    val withWarnings: Int = 0,
    val stoppedEarly: Boolean = false,
)

/**
 * Adds a book for each ISBN in a list, looked up and filled in the same way as an ISBN entered
 * by hand. ISBNs already in the library are skipped, so importing the same list again finishes
 * any whose lookup failed.
 */
class IsbnListImporter(
    private val store: LibraryStore,
    private val lookup: suspend (String) -> MetadataLookupResult,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun import(text: String, onProgress: (TransferProgress) -> Unit = {}): IsbnImportSummary {
        val list = IsbnList.parse(text)
        val known = store.allBooks().mapNotNullTo(HashSet()) { it.isbn13 }
        val pending = list.isbns.filter { it !in known }
        var summary = IsbnImportSummary(alreadyInLibrary = list.isbns.size - pending.size, invalid = list.invalid)

        val failed = mutableListOf<String>()
        var failuresInARow = 0
        for ((index, isbn13) in pending.withIndex()) {
            onProgress(TransferProgress("Looking up books", index, pending.size))
            if (index > 0) pause(LOOKUP_INTERVAL_MS)
            val result = lookup(isbn13)
            if (result.sourcesFailed) {
                failed += isbn13
                failuresInARow++
                if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                    return summary.copy(failed = failed + pending.drop(index + 1), stoppedEarly = true)
                }
                continue
            }
            failuresInARow = 0
            summary = save(isbn13, result, summary)
        }

        // A failure is usually a service that was briefly unreachable, so each gets one more try.
        val stillFailed = mutableListOf<String>()
        for ((index, isbn13) in failed.withIndex()) {
            onProgress(TransferProgress("Retrying books", index, failed.size))
            pause(LOOKUP_INTERVAL_MS)
            val result = lookup(isbn13)
            if (result.sourcesFailed) stillFailed += isbn13 else summary = save(isbn13, result, summary)
        }
        return summary.copy(failed = stillFailed)
    }

    private suspend fun save(isbn13: String, result: MetadataLookupResult, summary: IsbnImportSummary): IsbnImportSummary {
        if (result.book.title.isBlank()) return summary.copy(notFound = summary.notFound + isbn13)
        // The defaults the editor gives a new book when it is saved.
        store.insert(
            result.book.copy(
                id = 0L,
                readStatus = result.book.readStatus ?: ReadStatus.UNREAD,
                dateAdded = now(),
                dateModified = now(),
            ),
        )
        return summary.copy(
            added = summary.added + 1,
            withWarnings = summary.withWarnings + if (result.warning != null) 1 else 0,
        )
    }

    private companion object {
        const val LOOKUP_INTERVAL_MS = 1_000L
        const val MAX_FAILURES_IN_A_ROW = 5
    }
}
