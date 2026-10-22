package com.example.bookcatalog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE ASC, id ASC")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): Book?

    @Query("SELECT * FROM books WHERE isbn13 = :isbn13 ORDER BY id ASC LIMIT 1")
    suspend fun getByIsbn13(isbn13: String): Book?

    @Query("SELECT COUNT(*) FROM books WHERE coverFile = :coverFile")
    suspend fun countWithCover(coverFile: String): Int

    @Query("SELECT DISTINCT coverFile FROM books WHERE coverFile IS NOT NULL")
    suspend fun coverFiles(): List<String>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}
