package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.IsbnUtils
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException

/** A HomeChat request that failed in an expected way, with a [message] to show the user. */
class ModelException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BookMetadataRepository(
    private val sources: List<BookMetadataSource>,
    private val coverStore: CoverStore,
    private val subjectMapper: HomeChatBisacSubjectMapper? = null,
    private val descriptionWriter: HomeChatDescriptionWriter? = null,
) {
    /** Returns null when there is no description to summarize and the model doesn't know the book. */
    suspend fun writeDescription(book: Book): String? {
        val writer = descriptionWriter ?: throw ModelException("Description writing is not configured.")
        return callModel("write a description", DESCRIPTION_TIMEOUT_MS, book) { writer.write(book) }
    }

    suspend fun mapSubjectsToBisac(book: Book): List<String> {
        val mapper = subjectMapper ?: throw ModelException("BISAC subject mapping is not configured.")
        return callModel("map subjects to BISAC", SUBJECT_MAPPING_TIMEOUT_MS, book) { mapper.map(book) }
    }

    /** Turns the ways a model call can fail into a [ModelException] saying what went wrong. */
    private suspend fun <T> callModel(task: String, timeoutMs: Long, book: Book, call: suspend () -> T): T {
        try {
            return withTimeout(timeoutMs) { call() }
        } catch (exception: TimeoutCancellationException) {
            Log.w(TAG, "Timed out trying to $task for ${book.isbn13}", exception)
            throw ModelException("Couldn't $task: the local model took too long.", exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val reason = when (exception) {
                is JsonEncodingException, is JsonDataException, is IllegalArgumentException ->
                    "the local model returned an invalid response."
                is IOException, is HttpException -> "check the local model connection and try again."
                else -> throw exception
            }
            Log.w(TAG, "Could not $task for ${book.isbn13}", exception)
            throw ModelException("Couldn't $task: $reason", exception)
        }
    }

    suspend fun lookup(isbn13: String): MetadataLookupResult = supervisorScope {
        val results = sources
            .map { source -> source.name to async { fetchSafely(source, isbn13) } }
            .associate { (name, deferred) -> name to deferred.await() }
        val openResult = results["OpenLibrary"]
        val googleResult = results["Google"]
        val locResult = results["LibraryOfCongress"]
        val book = MetadataMerger.merge(
            isbn13 = isbn13,
            openLibrary = openResult?.metadata,
            googleBooks = googleResult?.metadata,
            libraryOfCongress = locResult?.metadata,
        )
            ?: return@supervisorScope MetadataLookupResult(
                book = emptyLookupBook(isbn13),
                message = if (results.values.all(SourceResult::failed)) {
                    "Couldn’t connect to the book lookup services. Check your internet connection and try again."
                } else {
                    "No book details were found. You can enter them manually."
                },
            )

        var mappedBook = book
        var coverDownloadErrored = false
        val coverUrls = listOfNotNull(
            openResult?.metadata?.coverUrl,
            googleResult?.metadata?.coverUrl,
        ).distinct()
        for (coverUrl in coverUrls) {
            val coverFile = try {
                withContext(Dispatchers.IO) { coverStore.download(isbn13, coverUrl) }
            } catch (exception: IOException) {
                Log.w(TAG, "Could not download cover for $isbn13", exception)
                coverDownloadErrored = true
                null
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Invalid cover URL for $isbn13", exception)
                coverDownloadErrored = true
                null
            }
            if (coverFile != null) {
                mappedBook = book.copy(coverFile = coverFile, coverSourceUrl = coverUrl)
                break
            }
        }
        // Only worth mentioning when an error, not just a placeholder image, left the book without one.
        val coverDownloadFailed = coverDownloadErrored && mappedBook.coverFile == null
        var subjectMappingFailed = false
        val usedLibraryOfCongress = book.dataSource.orEmpty().split('+').contains("LibraryOfCongress")
        val hasPublisherBisac = usedLibraryOfCongress &&
            locResult?.metadata?.bisacSubjects.orEmpty().isNotEmpty()
        if (subjectMapper != null && !hasPublisherBisac) {
            val subjects = try {
                mapSubjectsToBisac(mappedBook)
            } catch (_: ModelException) {
                emptyList()
            }
            if (subjects.isEmpty()) {
                subjectMappingFailed = true
            } else {
                mappedBook = mappedBook.copy(
                    subjects = subjects.joinToString("; "),
                    dataSource = mappedBook.dataSource.withHomeChat(),
                )
            }
        }
        // Runs after subject mapping, so subjects come from the source description.
        var descriptionFailed = false
        if (descriptionWriter != null) {
            try {
                writeDescription(mappedBook)?.let { description ->
                    mappedBook = mappedBook.copy(
                        description = description,
                        dataSource = mappedBook.dataSource.withHomeChat(),
                    )
                }
            } catch (_: ModelException) {
                descriptionFailed = true
            }
        }
        MetadataLookupResult(
            book = mappedBook,
            warning = listOfNotNull(
                "Book details were found, but its cover could not be downloaded."
                    .takeIf { coverDownloadFailed },
                "Could not map subjects to BISAC; source subjects are shown instead."
                    .takeIf { subjectMappingFailed },
                when {
                    !descriptionFailed -> null
                    mappedBook.description.isNullOrBlank() -> "Could not write a description."
                    else -> "Could not shorten the description; the source description is shown instead."
                },
            ).joinToString(" ").ifEmpty { null },
        )
    }

    /** Never throws for a failing source, so one source can't keep the others from being used. */
    private suspend fun fetchSafely(source: BookMetadataSource, isbn13: String): SourceResult =
        try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) { SourceResult(source.fetch(isbn13), failed = false) }
                ?: SourceResult(null, failed = true).also { Log.w(TAG, "${source.name} lookup timed out for $isbn13") }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "${source.name} lookup failed for $isbn13", exception)
            SourceResult(null, failed = true)
        }

    private fun String?.withHomeChat(): String =
        if (orEmpty().split('+').contains("HomeChat")) this.orEmpty() else listOfNotNull(this, "HomeChat").joinToString("+")

    private fun emptyLookupBook(isbn13: String) =
        Book(
            isbn13 = isbn13,
            isbn10 = IsbnUtils.isbn13To10(isbn13),
            title = "",
        )

    private data class SourceResult(
        val metadata: BookMetadata?,
        val failed: Boolean,
    )

    companion object {
        private const val TAG = "BookMetadata"
        private const val SOURCE_TIMEOUT_MS = 10_000L
        private const val SUBJECT_MAPPING_TIMEOUT_MS = 25_000L
        // Up to three model calls and two web searches.
        private const val DESCRIPTION_TIMEOUT_MS = 60_000L
    }
}
