package com.example.bookcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import android.content.ContentResolver
import android.net.Uri
import com.example.bookcatalog.data.io.ImportOptions
import com.example.bookcatalog.data.io.ImportSummary
import com.example.bookcatalog.data.io.LibraryExporter
import com.example.bookcatalog.data.io.LibraryImporter
import com.example.bookcatalog.data.io.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

class LibraryViewModel(repository: BookRepository) : ViewModel() {
    val books = repository.books.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val bookRepository = repository

    suspend fun delete(book: Book) {
        bookRepository.delete(book)
    }
}

class BookEditViewModel(
    private val repository: BookRepository,
    private val metadataRepository: BookMetadataRepository,
) : ViewModel() {
    suspend fun getBook(id: Long): Book? = repository.getBook(id)

    suspend fun save(book: Book): Long = repository.save(book)

    suspend fun mapSubjectsToBisac(book: Book): List<String> =
        metadataRepository.mapSubjectsToBisac(book)

    suspend fun writeDescription(book: Book): String? =
        metadataRepository.writeDescription(book)

    suspend fun delete(book: Book) {
        repository.delete(book)
    }
}

class IsbnLookupViewModel(
    private val metadataRepository: BookMetadataRepository,
) : ViewModel() {
    suspend fun lookup(isbn13: String): MetadataLookupResult =
        metadataRepository.lookup(isbn13)
}

class BarcodeScanViewModel(
    private val repository: BookRepository,
    private val metadataRepository: BookMetadataRepository,
) : ViewModel() {
    suspend fun findExistingBook(isbn13: String): Book? =
        repository.findByIsbn13(isbn13)

    suspend fun lookup(isbn13: String): MetadataLookupResult =
        metadataRepository.lookup(isbn13)
}

data class ImportExportUiState(
    val busy: Boolean = false,
    val progress: TransferProgress? = null,
    val exportMessage: String? = null,
    val importSummary: ImportSummary? = null,
    val error: String? = null,
)

/** Runs in the view model's scope, so an import or export keeps going through rotation. */
class ImportExportViewModel(
    private val exporter: LibraryExporter,
    private val importer: LibraryImporter,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImportExportUiState())
    val state: StateFlow<ImportExportUiState> = mutableState.asStateFlow()

    fun export(uri: Uri, zip: Boolean) = run("export") {
        val summary = withContext(Dispatchers.IO) {
            // "wt" truncates a file being replaced, but not every provider supports it.
            val output = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: contentResolver.openOutputStream(uri, "w")
                ?: throw FileNotFoundException("The export file couldn't be opened.")
            output.use { stream ->
                if (zip) exporter.exportZip(stream, ::report) else exporter.exportCsv(stream)
            }
        }
        val books = if (summary.books == 1) "1 book" else "${summary.books} books"
        val message = buildString {
            append("Exported $books")
            if (zip) append(" and ${summary.covers} ${if (summary.covers == 1) "cover" else "covers"}")
            append(".")
            if (summary.missingCovers > 0) {
                append(" ${summary.missingCovers} cover files were missing and left out.")
            }
        }
        mutableState.update { it.copy(exportMessage = message) }
    }

    fun import(uri: Uri, options: ImportOptions) = run("import") {
        val summary = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("The import file couldn't be opened.")
            input.use { importer.import(it, options, ::report) }
        }
        mutableState.update { it.copy(importSummary = summary) }
    }

    private fun report(progress: TransferProgress) {
        mutableState.update { it.copy(progress = progress) }
    }

    private fun run(action: String, work: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = ImportExportUiState(busy = true)
        viewModelScope.launch {
            try {
                work()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                mutableState.update { it.copy(error = "The $action failed: ${exception.message ?: "the file couldn't be read or written"}.") }
            } catch (exception: SecurityException) {
                mutableState.update { it.copy(error = "The $action failed: the app wasn't allowed to use that file.") }
            } catch (exception: android.database.SQLException) {
                mutableState.update { it.copy(error = "The $action failed and nothing was saved: ${exception.message}.") }
            } finally {
                mutableState.update { it.copy(busy = false, progress = null) }
            }
        }
    }
}
