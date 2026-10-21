package com.example.bookcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

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

class BookEditViewModel(private val repository: BookRepository) : ViewModel() {
    suspend fun getBook(id: Long): Book? = repository.getBook(id)

    suspend fun save(book: Book): Long = repository.save(book)

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
