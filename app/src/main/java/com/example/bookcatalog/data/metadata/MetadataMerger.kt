package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.IsbnUtils
import com.example.bookcatalog.domain.TitleCase

object MetadataMerger {
    fun merge(
        isbn13: String,
        openLibrary: BookMetadata?,
        googleBooks: BookMetadata?,
    ): Book? {
        val open = openLibrary?.takeIf(BookMetadata::hasDetails)
        val google = googleBooks?.takeIf(BookMetadata::hasDetails)
        if (open == null && google == null) return null

        val sources = listOfNotNull(
            "OpenLibrary".takeIf { open != null },
            "Google".takeIf { google != null },
        )
        val publishedDate = when {
            open?.publishedDate == null -> google?.publishedDate
            google?.publishedDate == null -> open.publishedDate
            google.publishedDate.length > open.publishedDate.length -> google.publishedDate
            else -> open.publishedDate
        }

        return Book(
            isbn13 = isbn13,
            isbn10 = IsbnUtils.isbn13To10(isbn13),
            title = TitleCase.format(open?.title ?: google?.title.orEmpty()),
            subtitle = (open?.subtitle ?: google?.subtitle)?.let(TitleCase::format),
            authors = open?.authors?.takeIf(List<String>::isNotEmpty)
                ?.joinToString("; ")
                ?: google?.authors?.takeIf(List<String>::isNotEmpty)?.joinToString("; "),
            publisher = open?.publisher ?: google?.publisher,
            publishedDate = publishedDate,
            pageCount = google?.pageCount ?: open?.pageCount,
            language = google?.language ?: open?.language,
            subjects = open?.subjects?.takeIf(List<String>::isNotEmpty)
                ?.joinToString("; ")
                ?: google?.subjects?.takeIf(List<String>::isNotEmpty)?.joinToString("; "),
            description = google?.description ?: open?.description,
            coverSourceUrl = open?.coverUrl ?: google?.coverUrl,
            dataSource = sources.joinToString("+"),
        )
    }
}
