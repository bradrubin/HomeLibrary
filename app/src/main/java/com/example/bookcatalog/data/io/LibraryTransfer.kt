package com.example.bookcatalog.data.io

import com.example.bookcatalog.data.db.Book

/** The library operations import and export need, kept small so they can be faked in tests. */
interface LibraryStore {
    suspend fun allBooks(): List<Book>

    suspend fun getBook(id: Long): Book?

    suspend fun insert(book: Book): Long

    suspend fun update(book: Book)

    suspend fun <T> inTransaction(block: suspend () -> T): T
}

enum class DuplicateHandling(val label: String) {
    SKIP("Skip"),
    OVERWRITE("Overwrite"),
    KEEP_BOTH("Keep both"),
}

data class ImportOptions(
    val duplicates: DuplicateHandling = DuplicateHandling.SKIP,
    val fetchMissingCovers: Boolean = false,
)

data class TransferProgress(
    val stage: String,
    val done: Int,
    val total: Int,
)

data class ImportSummary(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val coversCopied: Int = 0,
    val coversFetched: Int = 0,
    val coverFetchFailures: Int = 0,
    /** Rows imported with the ISBN left blank because it failed validation. */
    val invalidIsbnRows: List<Int> = emptyList(),
    /** Problems that kept a row out, or that stopped the import, each starting with its row. */
    val errors: List<String> = emptyList(),
    /** Values that were ignored while the rest of the row was imported. */
    val warnings: List<String> = emptyList(),
)

data class ExportSummary(
    val books: Int,
    val covers: Int,
    /** Books whose cover file is listed but missing, so the bundle has no image for them. */
    val missingCovers: Int = 0,
) {
    fun message(zip: Boolean): String = buildString {
        append("Exported ${if (books == 1) "1 book" else "$books books"}")
        if (zip) append(" and ${if (covers == 1) "1 cover" else "$covers covers"}")
        append(".")
        if (missingCovers > 0) append(" $missingCovers cover files were missing and left out.")
    }
}
