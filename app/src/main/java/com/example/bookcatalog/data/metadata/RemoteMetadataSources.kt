package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
import com.example.bookcatalog.domain.BookFormat
import com.example.bookcatalog.domain.IsbnUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private fun com.example.bookcatalog.data.remote.GoogleBooksVolumeInfoDto.matchesIsbn13(
    isbn13: String,
): Boolean = industryIdentifiers.orEmpty().any { identifier ->
    identifier.type in setOf("ISBN_13", "ISBN_10") &&
        IsbnUtils.toIsbn13(identifier.identifier.orEmpty()) == isbn13
}

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

class OpenLibraryMetadataSource(
    private val api: OpenLibraryApi,
) : BookMetadataSource {
    override val name = "OpenLibrary"

    override suspend fun fetch(isbn13: String): BookMetadata? = coroutineScope {
        val edition = async { editionFormat(isbn13) }
        val record = api.getBook("ISBN:$isbn13")["ISBN:$isbn13"] ?: return@coroutineScope null
        BookMetadata(
            title = record.title.nonBlank(),
            subtitle = record.subtitle.nonBlank(),
            authors = record.authors.orEmpty().mapNotNull { it.name.nonBlank() },
            publisher = record.publishers.orEmpty().firstNotNullOfOrNull { it.name.nonBlank() },
            publishedDate = record.publishDate.nonBlank(),
            pageCount = record.numberOfPages,
            subjects = record.subjects.orEmpty().mapNotNull { it.name.nonBlank() },
            coverUrl = (record.cover?.large ?: record.cover?.medium ?: record.cover?.small)
                .nonBlank()
                ?.replaceFirst("http://", "https://")
                ?: workCover(isbn13),
            format = edition.await(),
        ).takeIf(BookMetadata::hasDetails)
    }

    /** Many editions have no cover of their own, while another edition of the same work does. */
    private suspend fun workCover(isbn13: String): String? =
        try {
            api.searchByIsbn(isbn13).docs.firstOrNull()?.coverId
                ?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }

    /** The format is a nicety, so a missing or failed edition record doesn't fail the lookup. */
    private suspend fun editionFormat(isbn13: String): String? =
        try {
            BookFormat.fromSource(api.getEdition(isbn13).physicalFormat)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
}

class GoogleBooksMetadataSource(
    private val api: GoogleBooksApi,
    private val apiKey: String?,
) : BookMetadataSource {
    override val name = "Google"

    override suspend fun fetch(isbn13: String): BookMetadata? {
        val key = apiKey?.takeIf(String::isNotBlank)
        val exactSearch = api.search("isbn:$isbn13", key)
            .items
            ?.firstOrNull()
            ?.volumeInfo
        // Google often finds nothing for "isbn:", while a plain search finds this or another edition.
        val plainResults = if (exactSearch == null) {
            api.search(isbn13, key).items.orEmpty().mapNotNull { it.volumeInfo }
        } else {
            emptyList()
        }
        val exact = exactSearch ?: plainResults.firstOrNull { it.matchesIsbn13(isbn13) }
        val volume = exact ?: plainResults.firstOrNull() ?: return null
        return BookMetadata(
            title = volume.title.nonBlank(),
            subtitle = volume.subtitle.nonBlank(),
            authors = volume.authors.orEmpty().mapNotNull { it.nonBlank() },
            publisher = volume.publisher.nonBlank(),
            publishedDate = volume.publishedDate.nonBlank(),
            pageCount = volume.pageCount,
            language = volume.language.nonBlank(),
            subjects = volume.categories.orEmpty().mapNotNull { it.nonBlank() },
            description = volume.description.nonBlank(),
            coverUrl = volume.imageLinks?.thumbnail?.nonBlank()?.let(::upgradeGoogleCoverUrl),
            exactEdition = exact != null,
        ).takeIf(BookMetadata::hasDetails)
    }

    private fun upgradeGoogleCoverUrl(url: String): String {
        val secureUrl = url.replaceFirst("http://", "https://")
        val base = secureUrl.substringBefore('?')
        val parameters = secureUrl.substringAfter('?', "")
            .split('&')
            .filter { it.isNotBlank() && !it.startsWith("edge=") && !it.startsWith("zoom=") }
        return (listOf("zoom=2") + parameters).joinToString("&", prefix = "$base?")
    }
}
