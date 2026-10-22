package com.example.bookcatalog.data

import androidx.room.withTransaction
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.io.LibraryStore
import kotlinx.coroutines.flow.Flow

class BookRepository(private val database: AppDatabase) : LibraryStore {
    private val bookDao = database.bookDao()

    val books: Flow<List<Book>> = bookDao.observeAll()

    override suspend fun getBook(id: Long): Book? = bookDao.getById(id)

    suspend fun findByIsbn13(isbn13: String): Book? = bookDao.getByIsbn13(isbn13)

    override suspend fun allBooks(): List<Book> = bookDao.getAll()

    override suspend fun insert(book: Book): Long = bookDao.insert(book)

    override suspend fun update(book: Book) = bookDao.update(book)

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)

    suspend fun save(book: Book): Long {
        return if (book.id == 0L) {
            bookDao.insert(book)
        } else {
            bookDao.update(book)
            book.id
        }
    }

    suspend fun delete(book: Book) {
        bookDao.delete(book)
    }
}
