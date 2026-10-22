package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.domain.IsbnUtils
import com.squareup.moshi.JsonDataException
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

class BookMetadataRepository(
    private val sources: List<BookMetadataSource>,
    private val coverStore: CoverStore,
    private val subjectMapper: HomeChatBisacSubjectMapper? = null,
    private val descriptionWriter: HomeChatDescriptionWriter? = null,
) {
    /** Returns null when there is no description to summarize and the model doesn't know the book. */
    suspend fun writeDescription(book: com.example.bookcatalog.data.db.Book): String? {
        val writer = descriptionWriter
            ?: throw IllegalStateException("Description writing is not configured.")
        return try {
            // withTimeout, not withTimeoutOrNull, since null already means "no description".
            withTimeout(DESCRIPTION_TIMEOUT_MS) { writer.write(book) }
        } catch (exception: TimeoutCancellationException) {
            Log.w(TAG, "Description writing timed out for ${book.isbn13}", exception)
            throw IOException("Description writing timed out.", exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            Log.w(TAG, "Could not write description for ${book.isbn13}", exception)
            throw exception
        } catch (exception: HttpException) {
            Log.w(TAG, "Could not write description for ${book.isbn13}", exception)
            throw exception
        } catch (exception: JsonDataException) {
            Log.w(TAG, "Could not parse description response for ${book.isbn13}", exception)
            throw exception
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Local model returned an invalid description for ${book.isbn13}", exception)
            throw exception
        }
    }

    suspend fun mapSubjectsToBisac(book: com.example.bookcatalog.data.db.Book): List<String> {
        val mapper = subjectMapper
            ?: throw IllegalStateException("BISAC subject mapping is not configured.")
        return try {
            withTimeoutOrNull(SUBJECT_MAPPING_TIMEOUT_MS) {
                mapper.map(book)
            } ?: throw IOException("BISAC subject mapping timed out.")
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            Log.w(TAG, "Could not map subjects to BISAC for ${book.isbn13}", exception)
            throw exception
        } catch (exception: HttpException) {
            Log.w(TAG, "Could not map subjects to BISAC for ${book.isbn13}", exception)
            throw exception
        } catch (exception: JsonDataException) {
            Log.w(TAG, "Could not parse BISAC subject response for ${book.isbn13}", exception)
            throw exception
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Local model returned invalid BISAC subjects for ${book.isbn13}", exception)
            throw exception
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

        var coverDownloadFailed = false
        var mappedBook = book
        val coverUrls = listOfNotNull(
            openResult?.metadata?.coverUrl,
            googleResult?.metadata?.coverUrl,
        ).distinct()
        for (coverUrl in coverUrls) {
            try {
                val coverFile = withContext(Dispatchers.IO) {
                    coverStore.download(isbn13, coverUrl)
                }
                if (coverFile != null) {
                    mappedBook = book.copy(
                        coverFile = coverFile,
                        coverSourceUrl = coverUrl,
                    )
                    break
                }
            } catch (exception: IOException) {
                Log.w(TAG, "Could not download cover for $isbn13", exception)
                coverDownloadFailed = true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Invalid cover URL for $isbn13", exception)
                coverDownloadFailed = true
            }
        }
        var subjectMappingFailed = false
        val usedLibraryOfCongress = book.dataSource.orEmpty().split('+').contains("LibraryOfCongress")
        val hasPublisherBisac = usedLibraryOfCongress &&
            locResult?.metadata?.bisacSubjects.orEmpty().isNotEmpty()
        if (subjectMapper != null && !hasPublisherBisac) {
            try {
                val subjects = mapSubjectsToBisac(mappedBook)
                if (subjects.isNullOrEmpty()) {
                    subjectMappingFailed = true
                } else {
                    mappedBook = mappedBook.copy(
                        subjects = subjects.joinToString("; "),
                        dataSource = mappedBook.dataSource.withHomeChat(),
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                Log.w(TAG, "Could not map subjects to BISAC for $isbn13", exception)
                subjectMappingFailed = true
            } catch (exception: HttpException) {
                Log.w(TAG, "Could not map subjects to BISAC for $isbn13", exception)
                subjectMappingFailed = true
            } catch (exception: JsonDataException) {
                Log.w(TAG, "Could not parse BISAC subject response for $isbn13", exception)
                subjectMappingFailed = true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Local model returned invalid BISAC subjects for $isbn13", exception)
                subjectMappingFailed = true
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                descriptionFailed = true
            } catch (exception: HttpException) {
                descriptionFailed = true
            } catch (exception: JsonDataException) {
                descriptionFailed = true
            } catch (exception: IllegalArgumentException) {
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

    private suspend fun fetchSafely(
        source: BookMetadataSource,
        isbn13: String,
    ): SourceResult {
        return try {
            val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                SourceResult(metadata = source.fetch(isbn13), failed = false)
            }
            if (result == null) {
                Log.w(TAG, "${source.name} lookup timed out for $isbn13")
                SourceResult(metadata = null, failed = true)
            } else {
                result
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            logSourceFailure(source, isbn13, exception)
            SourceResult(metadata = null, failed = true)
        } catch (exception: HttpException) {
            logSourceFailure(source, isbn13, exception)
            SourceResult(metadata = null, failed = true)
        } catch (exception: JsonDataException) {
            logSourceFailure(source, isbn13, exception)
            SourceResult(metadata = null, failed = true)
        }
    }

    private fun logSourceFailure(
        source: BookMetadataSource,
        isbn13: String,
        exception: Exception,
    ) {
        Log.w(TAG, "${source.name} lookup failed for $isbn13", exception)
    }

    private fun String?.withHomeChat(): String =
        if (orEmpty().split('+').contains("HomeChat")) this.orEmpty() else listOfNotNull(this, "HomeChat").joinToString("+")

    private fun emptyLookupBook(isbn13: String) =
        com.example.bookcatalog.data.db.Book(
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
