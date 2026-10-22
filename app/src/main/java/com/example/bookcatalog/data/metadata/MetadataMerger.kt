package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.IsbnUtils
import com.example.bookcatalog.domain.Language
import com.example.bookcatalog.domain.PublicationYear
import com.example.bookcatalog.domain.TitleCase

object MetadataMerger {
    fun merge(
        isbn13: String,
        openLibrary: BookMetadata?,
        googleBooks: BookMetadata?,
        libraryOfCongress: BookMetadata? = null,
    ): Book? {
        val open = openLibrary?.takeIf(BookMetadata::hasDetails)
        val google = googleBooks?.takeIf(BookMetadata::hasDetails)
        // The catalog occasionally attaches an ISBN to an unrelated record, so it must agree on the title.
        val loc = libraryOfCongress?.takeIf(BookMetadata::hasDetails)
            ?.takeIf { catalog -> titlesAgree(catalog.title, open?.title ?: google?.title) }
        if (open == null && google == null && loc == null) return null

        val sources = listOfNotNull(
            "OpenLibrary".takeIf { open != null },
            "Google".takeIf { google != null },
            "LibraryOfCongress".takeIf { loc != null },
        )
        // Only the year is kept, so Open Library is preferred as for the other bibliographic fields.
        val publishedDate = listOf(open?.publishedDate, google?.publishedDate, loc?.publishedDate)
            .firstNotNullOfOrNull(PublicationYear::from)

        fun List<String>?.joined(): String? = this?.takeIf(List<String>::isNotEmpty)?.joinToString("; ")

        return Book(
            isbn13 = isbn13,
            isbn10 = IsbnUtils.isbn13To10(isbn13),
            title = TitleCase.format(open?.title ?: google?.title ?: loc?.title.orEmpty()),
            subtitle = (open?.subtitle ?: google?.subtitle ?: loc?.subtitle)?.let(TitleCase::format),
            authors = open?.authors.joined() ?: google?.authors.joined() ?: loc?.authors.joined(),
            publisher = open?.publisher ?: google?.publisher ?: loc?.publisher,
            publishedDate = publishedDate,
            pageCount = google?.pageCount ?: open?.pageCount ?: loc?.pageCount,
            format = open?.format ?: loc?.format ?: google?.format,
            language = Language.name(google?.language ?: loc?.language ?: open?.language) ?: Language.DEFAULT,
            // Publisher-supplied BISAC headings beat cataloged subjects, which beat retail categories.
            subjects = loc?.bisacSubjects.joined()
                ?: loc?.subjects.joined()
                ?: open?.subjects.joined()
                ?: google?.subjects.joined(),
            description = google?.description ?: loc?.description ?: open?.description,
            coverSourceUrl = open?.coverUrl ?: google?.coverUrl,
            dataSource = sources.joinToString("+"),
        )
    }

    private fun titlesAgree(catalogTitle: String?, otherTitle: String?): Boolean {
        if (catalogTitle == null || otherTitle == null) return true
        val catalog = comparableTitle(catalogTitle)
        val other = comparableTitle(otherTitle)
        return catalog.isEmpty() || other.isEmpty() || catalog.startsWith(other) || other.startsWith(catalog)
    }

    private fun comparableTitle(title: String): String =
        title.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .removePrefix("the ")
            .removePrefix("a ")
            .removePrefix("an ")
}
