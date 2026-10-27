package com.example.bookcatalog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bookcatalog.domain.AuthorNames
import com.example.bookcatalog.domain.IsbnUtils

@Database(
    entities = [Book::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        /** Adds the ASIN column for books imported from an Amazon book list. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN asin TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_asin ON books (asin)")
            }
        }

        /** Rewrites stored author names without middle initials, as new names are now saved. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val changes = mutableListOf<Pair<Long, String?>>()
                db.query("SELECT id, authors FROM books WHERE authors IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        val authors = cursor.getString(1)
                        val normalized = AuthorNames.normalize(authors)
                        if (normalized != authors) changes += cursor.getLong(0) to normalized
                    }
                }
                for ((id, authors) in changes) {
                    db.execSQL("UPDATE books SET authors = ? WHERE id = ?", arrayOf(authors, id))
                }
            }
        }

        /** Adds an audiobook's listening length, in minutes. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN audioMinutes INTEGER")
            }
        }

        /** Fills in the ISBN-10 of books with a 978 ISBN-13 that were saved without one. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val changes = mutableListOf<Pair<Long, String>>()
                db.query(
                    "SELECT id, isbn13 FROM books WHERE isbn13 IS NOT NULL AND (isbn10 IS NULL OR isbn10 = '')",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        IsbnUtils.isbn13To10(cursor.getString(1))?.let { changes += cursor.getLong(0) to it }
                    }
                }
                for ((id, isbn10) in changes) {
                    db.execSQL("UPDATE books SET isbn10 = ? WHERE id = ?", arrayOf(isbn10, id))
                }
            }
        }
    }
}
