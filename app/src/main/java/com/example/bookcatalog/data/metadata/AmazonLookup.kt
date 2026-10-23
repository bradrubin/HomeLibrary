package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.io.AmazonBookEnricher
import com.example.bookcatalog.data.io.AmazonImporter
import com.example.bookcatalog.data.io.EnrichResult
import com.example.bookcatalog.domain.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Looks up an Amazon book by the ISBN of a matching print edition, keeping Amazon's format, date
 * added and ASIN. Amazon's genres join the edition's subjects as input to BISAC mapping.
 */
class AmazonLookup(
    private val finder: IsbnFinder,
    private val metadata: BookMetadataRepository,
) : AmazonBookEnricher {
    override suspend fun enrich(book: Book): EnrichResult {
        val sources = mutableListOf(AmazonImporter.SOURCE)
        var current = book
        var hasPublisherBisac = false

        val isbn13 = try {
            book.isbn13 ?: finder.find(book.title, book.authors)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "ISBN search failed for ${book.asin}", exception)
            return EnrichResult(book, matchedIsbn = false, complete = false)
        }
        if (isbn13 != null) {
            val details = metadata.fetchDetails(isbn13)
            if (details.allSourcesFailed) return EnrichResult(book, matchedIsbn = false, complete = false)
            val found = details.book
            if (found != null && IsbnFinder.titlesAgree(found.title, IsbnFinder.searchTitle(book.title))) {
                current = withEdition(book, found)
                sources += found.dataSource.orEmpty().split('+').filter(String::isNotEmpty)
                hasPublisherBisac = details.hasPublisherBisac
                // The publisher's own BISAC headings beat anything mapped from Amazon's genres.
                if (hasPublisherBisac) current = current.copy(subjects = found.subjects)
            }
        }
        val matched = current.isbn13 != null
        if (!matched) current = current.copy(isbn13 = null, isbn10 = null, language = current.language ?: Language.DEFAULT)

        var complete = true
        var usedHomeChat = false
        if (metadata.canMapSubjects && !hasPublisherBisac) {
            try {
                val headings = metadata.mapSubjectsToBisac(current)
                if (headings.isNotEmpty()) {
                    current = current.copy(subjects = headings.joinToString("; "))
                    usedHomeChat = true
                }
            } catch (exception: ModelException) {
                complete = complete && !exception.isTemporary()
            }
        }
        if (metadata.canWriteDescriptions) {
            try {
                metadata.writeDescription(current)?.let { description ->
                    current = current.copy(description = description)
                    usedHomeChat = true
                }
            } catch (exception: ModelException) {
                complete = complete && !exception.isTemporary()
            }
        }
        if (usedHomeChat) sources += "HomeChat"
        // A book left marked with only the Amazon source is looked up again on the next import.
        val dataSource = if (complete) sources.distinct().joinToString("+") else AmazonImporter.SOURCE
        return EnrichResult(current.copy(dataSource = dataSource), matchedIsbn = matched, complete = complete)
    }

    /**
     * Takes the bibliographic details and cover from the print edition. Amazon's genres stay in
     * the subjects alongside the edition's, so BISAC mapping sees both.
     */
    private fun withEdition(amazon: Book, edition: Book): Book {
        val subjects = (amazon.subjects.splitList() + edition.subjects.splitList()).distinct()
        return amazon.copy(
            isbn13 = edition.isbn13,
            isbn10 = edition.isbn10,
            title = edition.title.ifBlank { amazon.title },
            subtitle = edition.subtitle ?: amazon.subtitle,
            authors = edition.authors ?: amazon.authors,
            publisher = edition.publisher,
            publishedDate = edition.publishedDate,
            pageCount = edition.pageCount ?: amazon.pageCount,
            language = edition.language,
            subjects = subjects.joinToString("; ").ifEmpty { null },
            description = edition.description,
            coverFile = edition.coverFile ?: amazon.coverFile,
            coverSourceUrl = edition.coverSourceUrl ?: amazon.coverSourceUrl,
        )
    }

    private fun String?.splitList(): List<String> =
        orEmpty().split(';').map(String::trim).filter(String::isNotEmpty)

    /** A model that couldn't be reached or timed out is worth retrying; a bad answer isn't. */
    private fun ModelException.isTemporary(): Boolean =
        cause is IOException || cause is HttpException || cause is TimeoutCancellationException

    private companion object {
        const val TAG = "AmazonLookup"
    }
}
