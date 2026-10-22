package com.example.bookcatalog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Book::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
