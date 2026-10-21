package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
import com.example.bookcatalog.domain.IsbnUtils

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

    override suspend fun fetch(isbn13: String): BookMetadata? {
        val record = api.getBook("ISBN:$isbn13")["ISBN:$isbn13"] ?: return null
        return BookMetadata(
            title = record.title.nonBlank(),
            subtitle = record.subtitle.nonBlank(),
            authors = record.authors.orEmpty().mapNotNull { it.name.nonBlank() },
            publisher = record.publishers.orEmpty().firstNotNullOfOrNull { it.name.nonBlank() },
            publishedDate = record.publishDate.nonBlank(),
            pageCount = record.numberOfPages,
            subjects = record.subjects.orEmpty().mapNotNull { it.name.nonBlank() },
            coverUrl = (record.cover?.large ?: record.cover?.medium ?: record.cover?.small)
                .nonBlank()
                ?.replaceFirst("http://", "https://"),
        ).takeIf(BookMetadata::hasDetails)
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
        val volume = exactSearch ?: api.search(isbn13, key)
            .items
            ?.firstNotNullOfOrNull { item ->
                item.volumeInfo?.takeIf { it.matchesIsbn13(isbn13) }
            }
            ?: return null
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
