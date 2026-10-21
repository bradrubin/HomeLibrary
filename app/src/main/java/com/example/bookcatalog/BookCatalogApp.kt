package com.example.bookcatalog

import android.app.Application
import androidx.room.Room
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.GoogleBooksMetadataSource
import com.example.bookcatalog.data.metadata.OpenLibraryMetadataSource
import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
import com.example.bookcatalog.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

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
    )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    val bookRepository = BookRepository(database.bookDao())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val coverStore = CoverStore(application.filesDir, httpClient)
    val metadataRepository = BookMetadataRepository(
        sources = listOf(
            OpenLibraryMetadataSource(
                retrofit("https://openlibrary.org/").create(OpenLibraryApi::class.java),
            ),
            GoogleBooksMetadataSource(
                retrofit("https://www.googleapis.com/").create(GoogleBooksApi::class.java),
                BuildConfig.GOOGLE_BOOKS_API_KEY,
            ),
        ),
        coverStore = coverStore,
    )
}
