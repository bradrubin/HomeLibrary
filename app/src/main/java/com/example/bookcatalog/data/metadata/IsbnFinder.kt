package com.example.bookcatalog.data.metadata

import android.util.Log
import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
import com.example.bookcatalog.domain.AuthorNames
import com.example.bookcatalog.domain.IsbnUtils
import kotlinx.coroutines.CancellationException
import java.text.Normalizer

/**
 * Finds the ISBN of an English print edition for a book known only by title and author, such as
 * a Kindle or Audible book. Open Library is searched first; Google Books only when it finds
 * nothing, which keeps within Google's daily quota and lets imports work if Google's API goes away.
 */
class IsbnFinder(
    private val openLibrary: OpenLibraryApi,
    private val googleBooks: GoogleBooksApi,
    private val googleApiKey: String?,
) {
    /**
     * Returns null when no edition matches. Open Library failures are thrown, so the caller can
     * try again later rather than record the book as unmatched.
     */
    suspend fun find(title: String, author: String?): String? {
        val searchTitle = searchTitle(title)
        if (comparable(searchTitle).isEmpty()) return null
        val surname = author?.let(::surname)
        return fromOpenLibrary(searchTitle, surname) ?: fromGoogleBooks(searchTitle, surname)
    }

    private suspend fun fromOpenLibrary(title: String, surname: String?): String? {
        val docs = openLibrary.search(title, surname).docs
            .filter { titlesAgree(it.title, title) && authorAgrees(it.authorNames, surname) }
        var fallback: String? = null
        for (doc in docs) {
            for (isbn13 in englishIsbn13s(doc.isbn.orEmpty()).take(MAX_CANDIDATES)) {
                val record = openLibrary.getBook("ISBN:$isbn13")["ISBN:$isbn13"] ?: continue
                if (!titlesAgree(record.title, title)) continue
                // Audio editions are cataloged with little more than a title, so a print one is preferred.
                val audio = record.publishers.orEmpty().any { it.name.orEmpty().contains("audio", ignoreCase = true) }
                if (audio) continue
                if (record.cover != null) return isbn13
                if (fallback == null) fallback = isbn13
            }
            if (fallback != null) return fallback
        }
        return null
    }

    private suspend fun fromGoogleBooks(title: String, surname: String?): String? {
        val query = buildString {
            append("intitle:\"").append(title.replace("\"", "")).append('"')
            if (!surname.isNullOrBlank()) append(" inauthor:").append(surname)
        }
        val volumes = try {
            googleBooks.search(query, googleApiKey?.takeIf(String::isNotBlank)).items.orEmpty()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Google Books search failed for \"$title\"", exception)
            return null
        }
        return volumes.asSequence()
            .mapNotNull { it.volumeInfo }
            .filter { titlesAgree(it.title, title) && authorAgrees(it.authors, surname) }
            .flatMap { volume -> englishIsbn13s(volume.industryIdentifiers.orEmpty().mapNotNull { it.identifier }) }
            .firstOrNull()
    }

    companion object {
        private const val TAG = "IsbnFinder"
        private const val MAX_CANDIDATES = 3
        private val suffixes = setOf("jr", "sr", "ii", "iii", "iv", "md", "phd")

        /** Drops a subtitle or series note, so "Inferno: A Novel (Robert Langdon Book 4)" searches as "Inferno". */
        internal fun searchTitle(title: String): String =
            title.substringBefore(':').substringBefore(" (").trim()

        internal fun surname(author: String): String? =
            AuthorNames.naturalOrder(author.substringBefore(';'))
                .substringBefore(',')
                .split(' ')
                .filter { it.isNotBlank() && comparable(it) !in suffixes }
                .lastOrNull()

        /** ISBN-13s in the English-language groups 978-0 and 978-1, in the order given. */
        internal fun englishIsbn13s(isbns: List<String>): List<String> =
            isbns.mapNotNull(IsbnUtils::toIsbn13)
                .filter { it.startsWith("9780") || it.startsWith("9781") }
                .distinct()

        internal fun titlesAgree(found: String?, wanted: String): Boolean {
            val a = comparable(found.orEmpty()).removeArticle()
            val b = comparable(wanted).removeArticle()
            return a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
        }

        internal fun authorAgrees(authors: List<String>?, surname: String?): Boolean {
            if (surname.isNullOrBlank()) return true
            val wanted = comparable(surname)
            return authors.orEmpty().any { wanted in comparable(it).split(' ') }
        }

        private fun comparable(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        private fun String.removeArticle(): String = removePrefix("the ").removePrefix("a ").removePrefix("an ")
    }
}
