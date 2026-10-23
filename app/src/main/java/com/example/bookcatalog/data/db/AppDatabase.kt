package com.example.bookcatalog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bookcatalog.domain.AuthorNames

@Database(
    entities = [Book::class],
    version = 3,
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
    }
}
