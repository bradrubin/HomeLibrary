package com.example.bookcatalog

import android.app.Application
import androidx.room.Room
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.AppDatabase

class BookCatalogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "book-catalog.db",
    ).build()

    val bookRepository = BookRepository(database.bookDao())
}
