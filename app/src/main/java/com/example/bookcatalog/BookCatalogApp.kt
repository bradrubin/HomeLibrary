package com.example.bookcatalog

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.bookcatalog.BuildConfig
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.GoogleBooksMetadataSource
import com.example.bookcatalog.data.metadata.HomeChatApi
import com.example.bookcatalog.data.metadata.HomeChatBisacSubjectMapper
import com.example.bookcatalog.data.metadata.OpenLibraryMetadataSource
import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
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
    private val localLlmHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient = httpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val coverStore = CoverStore(application.filesDir, httpClient)
    private val localLlmBaseUrl = BuildConfig.HOMECHAT_LLM_BASE_URL
    private val subjectMapper = createSubjectMapper()
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
        subjectMapper = subjectMapper,
    )

    private fun String.ensureTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"

    private fun createSubjectMapper(): HomeChatBisacSubjectMapper? {
        if (localLlmBaseUrl.isBlank()) return null
        if (BuildConfig.HOMECHAT_LLM_API_KEY.isBlank() || BuildConfig.HOMECHAT_LLM_MODEL.isBlank()) {
            Log.w(TAG, "HomeChat BISAC mapping is disabled: configure its model and API key.")
            return null
        }
        return HomeChatBisacSubjectMapper(
            api = retrofit(localLlmBaseUrl.ensureTrailingSlash(), localLlmHttpClient)
                .create(HomeChatApi::class.java),
            model = BuildConfig.HOMECHAT_LLM_MODEL,
            apiKey = BuildConfig.HOMECHAT_LLM_API_KEY,
            moshi = moshi,
        )
    }

    private companion object {
        const val TAG = "BookCatalogApp"
    }
}
