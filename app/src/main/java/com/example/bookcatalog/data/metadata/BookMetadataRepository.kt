package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.domain.IsbnUtils
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException

class BookMetadataRepository(
    private val sources: List<BookMetadataSource>,
    private val coverStore: CoverStore,
    private val subjectMapper: HomeChatBisacSubjectMapper? = null,
) {
    suspend fun lookup(isbn13: String): MetadataLookupResult = supervisorScope {
        val openLibrary = sources.first { it.name == "OpenLibrary" }
        val googleBooks = sources.first { it.name == "Google" }
        val openDeferred = async { fetchSafely(openLibrary, isbn13) }
        val googleDeferred = async { fetchSafely(googleBooks, isbn13) }
        val openResult = openDeferred.await()
        val googleResult = googleDeferred.await()
        val book = MetadataMerger.merge(isbn13, openResult.metadata, googleResult.metadata)
            ?: return@supervisorScope MetadataLookupResult(
                book = emptyLookupBook(isbn13),
                message = if (openResult.failed && googleResult.failed) {
                    "Couldn’t connect to the book lookup services. Check your internet connection and try again."
                } else {
                    "No book details were found. You can enter them manually."
                },
            )

        var coverDownloadFailed = false
        var mappedBook = book
        val coverUrls = listOfNotNull(
            openResult.metadata?.coverUrl,
            googleResult.metadata?.coverUrl,
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
        if (subjectMapper != null) {
            try {
                val subjects = withTimeoutOrNull(SUBJECT_MAPPING_TIMEOUT_MS) {
                    subjectMapper.map(mappedBook)
                }
                if (subjects.isNullOrEmpty()) {
                    subjectMappingFailed = true
                } else {
                    mappedBook = mappedBook.copy(
                        subjects = subjects.joinToString("; "),
                        dataSource = listOfNotNull(mappedBook.dataSource, "HomeChat")
                            .joinToString("+"),
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
        MetadataLookupResult(
            book = mappedBook,
            warning = listOfNotNull(
                "Book details were found, but its cover could not be downloaded."
                    .takeIf { coverDownloadFailed },
                "Could not map subjects to BISAC; source subjects are shown instead."
                    .takeIf { subjectMappingFailed },
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
    }
}
