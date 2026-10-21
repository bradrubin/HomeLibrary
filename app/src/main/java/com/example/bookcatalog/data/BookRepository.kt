package com.example.bookcatalog.data

import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.db.BookDao
import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val books: Flow<List<Book>> = bookDao.observeAll()

    suspend fun getBook(id: Long): Book? = bookDao.getById(id)

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
