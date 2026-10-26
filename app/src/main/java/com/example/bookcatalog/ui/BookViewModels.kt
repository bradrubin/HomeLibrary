package com.example.bookcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.FetchedDetails
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import android.content.ContentResolver
import android.net.Uri
import com.example.bookcatalog.data.io.AmazonImportSummary
import com.example.bookcatalog.data.io.AmazonImporter
import com.example.bookcatalog.data.io.ImportOptions
import com.example.bookcatalog.data.io.IsbnImportSummary
import com.example.bookcatalog.data.io.IsbnListImporter
import com.example.bookcatalog.data.io.ImportSummary
import com.example.bookcatalog.data.io.LibraryExporter
import com.example.bookcatalog.data.io.LibraryImporter
import com.example.bookcatalog.data.io.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import com.example.bookcatalog.domain.LibraryFilter
import com.example.bookcatalog.domain.LibraryQuery
import com.example.bookcatalog.domain.SortOrder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

data class LibraryControls(
    val query: String = "",
    val sort: SortOrder = SortOrder.TITLE,
    val filter: LibraryFilter = LibraryFilter(),
    val grid: Boolean = false,
    val selecting: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)

data class LibraryUiState(
    /** The books matching the search, in sort order. */
    val books: List<Book> = emptyList(),
    val libraryIsEmpty: Boolean = true,
    /** How many books in the whole library have each format, read status and rating, to filter by. */
    val formatCounts: Map<String, Int> = emptyMap(),
    val readStatusCounts: Map<String, Int> = emptyMap(),
    val ratingCounts: Map<Int, Int> = emptyMap(),
    val controls: LibraryControls = LibraryControls(),
)

class LibraryViewModel(
    private val repository: BookRepository,
    private val exporter: LibraryExporter,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val controls = MutableStateFlow(LibraryControls())
    private val mutableMessages = Channel<String>(Channel.BUFFERED)

    /** One-off results, such as a finished export, to show as a snackbar. */
    val messages = mutableMessages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(repository.books, controls) { books, controls ->
        val ids = books.mapTo(HashSet(), Book::id)
        LibraryUiState(
            books = LibraryQuery.apply(books, controls.query, controls.sort, controls.filter),
            libraryIsEmpty = books.isEmpty(),
            formatCounts = books.groupingBy(LibraryFilter::formatOf).eachCount(),
            readStatusCounts = books.groupingBy(LibraryFilter::readStatusOf).eachCount(),
            ratingCounts = books.groupingBy(LibraryFilter::ratingOf).eachCount(),
            // Books deleted elsewhere drop out of the selection.
            controls = controls.copy(selectedIds = controls.selectedIds.filterTo(HashSet()) { it in ids }),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setQuery(query: String) = controls.update { it.copy(query = query) }

    fun setSort(sort: SortOrder) = controls.update { it.copy(sort = sort) }

    fun setFilter(filter: LibraryFilter) = controls.update { it.copy(filter = filter) }

    fun toggleGrid() = controls.update { it.copy(grid = !it.grid) }

    fun startSelecting(bookId: Long? = null) =
        controls.update { it.copy(selecting = true, selectedIds = setOfNotNull(bookId)) }

    fun stopSelecting() = controls.update { it.copy(selecting = false, selectedIds = emptySet()) }

    fun toggleSelected(bookId: Long) = controls.update {
        it.copy(selectedIds = if (bookId in it.selectedIds) it.selectedIds - bookId else it.selectedIds + bookId)
    }

    /** Selects every book the search currently shows. */
    fun selectAllShown() = controls.update { it.copy(selectedIds = it.selectedIds + state.value.books.map(Book::id)) }

    fun deselectAll() = controls.update { it.copy(selectedIds = emptySet()) }

    fun deleteSelected() {
        viewModelScope.launch {
            val books = selectedBooks()
            repository.delete(books)
            stopSelecting()
            mutableMessages.send(if (books.size == 1) "Deleted 1 book." else "Deleted ${books.size} books.")
        }
    }

    fun exportSelected(uri: Uri, zip: Boolean) {
        viewModelScope.launch {
            try {
                val books = selectedBooks()
                val summary = withContext(Dispatchers.IO) {
                    contentResolver.openForExport(uri).use { stream ->
                        if (zip) exporter.exportZip(stream, books) else exporter.exportCsv(stream, books)
                    }
                }
                stopSelecting()
                mutableMessages.send(summary.message(zip))
            } catch (exception: IOException) {
                mutableMessages.send("The export failed: ${exception.message ?: "the file couldn't be written"}.")
            } catch (exception: SecurityException) {
                mutableMessages.send("The export failed: the app wasn't allowed to use that file.")
            }
        }
    }

    /** Includes selected books the current search hides. */
    private suspend fun selectedBooks(): List<Book> {
        val ids = controls.value.selectedIds
        return LibraryQuery.sort(repository.allBooks().filter { it.id in ids }, controls.value.sort)
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

    suspend fun fetchDetails(isbn13: String): FetchedDetails =
        metadataRepository.fetchDetails(isbn13)

    val canMapSubjects: Boolean get() = metadataRepository.canMapSubjects
    val canWriteDescriptions: Boolean get() = metadataRepository.canWriteDescriptions

    suspend fun writeDescription(book: Book): String? =
        metadataRepository.writeDescription(book)

    suspend fun duplicate(book: Book): Long = repository.duplicate(book)

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
    val amazonSummary: AmazonImportSummary? = null,
    val isbnSummary: IsbnImportSummary? = null,
    val error: String? = null,
)

/** Runs in the view model's scope, so an import or export keeps going through rotation. */
class ImportExportViewModel(
    private val exporter: LibraryExporter,
    private val importer: LibraryImporter,
    private val amazonImporter: AmazonImporter,
    private val isbnListImporter: IsbnListImporter,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImportExportUiState())
    val state: StateFlow<ImportExportUiState> = mutableState.asStateFlow()

    fun export(uri: Uri, zip: Boolean) = run("export") {
        val summary = withContext(Dispatchers.IO) {
            contentResolver.openForExport(uri).use { stream ->
                if (zip) exporter.exportZip(stream, onProgress = ::report) else exporter.exportCsv(stream)
            }
        }
        mutableState.update { it.copy(exportMessage = summary.message(zip)) }
    }

    fun import(uri: Uri, options: ImportOptions) = run("import") {
        val summary = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("The import file couldn't be opened.")
            input.use { importer.import(it, options, ::report) }
        }
        mutableState.update { it.copy(importSummary = summary) }
    }

    fun importAmazon(uri: Uri) = run("import") {
        val summary = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("The import file couldn't be opened.")
            input.use { amazonImporter.import(it, ::report) }
        }
        mutableState.update { it.copy(amazonSummary = summary) }
    }

    fun importIsbns(text: String) = run("import") {
        val summary = isbnListImporter.import(text, ::report)
        mutableState.update { it.copy(isbnSummary = summary) }
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

/** Opens a file chosen with CreateDocument for writing, replacing anything already in it. */
internal fun ContentResolver.openForExport(uri: Uri): OutputStream =
    // "wt" truncates a file being replaced, but not every provider supports it.
    runCatching { openOutputStream(uri, "wt") }.getOrNull()
        ?: openOutputStream(uri, "w")
        ?: throw FileNotFoundException("The export file couldn't be opened.")
