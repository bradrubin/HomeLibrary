package com.example.bookcatalog.data

import androidx.room.withTransaction
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.io.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val database: AppDatabase,
    private val coverDirectory: File,
) : LibraryStore {
    private val bookDao = database.bookDao()

    val books: Flow<List<Book>> = bookDao.observeAll()

    override suspend fun getBook(id: Long): Book? = bookDao.getById(id)

    suspend fun findByIsbn13(isbn13: String): Book? = bookDao.getByIsbn13(isbn13)

    override suspend fun allBooks(): List<Book> = bookDao.getAll()

    override suspend fun insert(book: Book): Long = bookDao.insert(book)

    override suspend fun update(book: Book) = bookDao.update(book)

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)

    /** Saves the book, deleting the cover it replaced unless another copy still uses it. */
    suspend fun save(book: Book): Long {
        if (book.id == 0L) return bookDao.insert(book)
        val replacedCover = bookDao.getById(book.id)?.coverFile?.takeIf { it != book.coverFile }
        bookDao.update(book)
        replacedCover?.let { deleteCoverIfUnused(it) }
        return book.id
    }

    suspend fun delete(book: Book) {
        bookDao.delete(book)
        book.coverFile?.let { deleteCoverIfUnused(it) }
    }

    /**
     * Deletes cover files no book uses, such as photos taken for a book that was never saved.
     * Recent files are kept, since an edit in progress may be about to save one.
     */
    suspend fun deleteUnusedCovers(olderThanMillis: Long) {
        val used = bookDao.coverFiles().toSet()
        val cutoff = System.currentTimeMillis() - olderThanMillis
        withContext(Dispatchers.IO) {
            coverDirectory.listFiles()
                ?.filter { it.isFile && it.name !in used && it.lastModified() < cutoff }
                ?.forEach(File::delete)
        }
    }

    private suspend fun deleteCoverIfUnused(coverFile: String) {
        if (bookDao.countWithCover(coverFile) == 0) {
            withContext(Dispatchers.IO) { File(coverDirectory, coverFile).delete() }
        }
    }
}
