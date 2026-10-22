package com.example.bookcatalog.data.metadata

interface BookMetadataSource {
    val name: String
    suspend fun fetch(isbn13: String): BookMetadata?
}

data class BookMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val language: String? = null,
    val subjects: List<String> = emptyList(),
    val bisacSubjects: List<String> = emptyList(),
    val description: String? = null,
    val coverUrl: String? = null,
) {
    val hasDetails: Boolean
        get() = listOf(
            title,
            subtitle,
            authors.takeIf(List<String>::isNotEmpty)?.joinToString(),
            publisher,
            publishedDate,
            pageCount?.toString(),
            language,
            subjects.takeIf(List<String>::isNotEmpty)?.joinToString(),
            bisacSubjects.takeIf(List<String>::isNotEmpty)?.joinToString(),
            description,
            coverUrl,
        ).any { !it.isNullOrBlank() }
}

data class MetadataLookupResult(
    val book: com.example.bookcatalog.data.db.Book,
    val message: String? = null,
    val warning: String? = null,
)
