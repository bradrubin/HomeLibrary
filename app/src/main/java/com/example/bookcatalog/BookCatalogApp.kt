package com.example.bookcatalog

import android.app.Application
import android.content.ContentResolver
import android.util.Log
import androidx.room.Room
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.io.AmazonImporter
import com.example.bookcatalog.data.io.LibraryExporter
import com.example.bookcatalog.data.io.LibraryImporter
import com.example.bookcatalog.data.metadata.AmazonLookup
import com.example.bookcatalog.data.metadata.BookMetadataRepository
import com.example.bookcatalog.data.metadata.BraveWebSearch
import com.example.bookcatalog.data.metadata.GoogleBooksMetadataSource
import com.example.bookcatalog.data.metadata.LibraryOfCongressMetadataSource
import com.example.bookcatalog.data.metadata.HomeChatApi
import com.example.bookcatalog.data.metadata.HomeChatBisacSubjectMapper
import com.example.bookcatalog.data.metadata.HomeChatDescriptionWriter
import com.example.bookcatalog.data.metadata.IsbnFinder
import com.example.bookcatalog.data.metadata.OpenLibraryMetadataSource
import com.example.bookcatalog.data.remote.BraveSearchApi
import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.LibraryOfCongressApi
import com.example.bookcatalog.data.remote.OpenLibraryApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    ).addMigrations(AppDatabase.MIGRATION_1_2).build()

    val contentResolver: ContentResolver = application.contentResolver

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
    val bookRepository = BookRepository(database, coverStore.coverDirectory)
    private val localLlmBaseUrl = BuildConfig.HOMECHAT_LLM_BASE_URL
    private val homeChatApi = createHomeChatApi()
    private val subjectMapper = homeChatApi?.let {
        HomeChatBisacSubjectMapper(it, BuildConfig.HOMECHAT_LLM_MODEL, BuildConfig.HOMECHAT_LLM_API_KEY, moshi)
    }
    private val webSearch = BuildConfig.BRAVE_SEARCH_API_KEY.takeIf(String::isNotBlank)?.let { key ->
        BraveWebSearch(retrofit("https://api.search.brave.com/").create(BraveSearchApi::class.java), key)
    }
    private val descriptionWriter = homeChatApi?.let {
        HomeChatDescriptionWriter(
            it,
            BuildConfig.HOMECHAT_LLM_MODEL,
            BuildConfig.HOMECHAT_LLM_API_KEY,
            moshi,
            webSearch,
        )
    }
    private val openLibraryApi = retrofit("https://openlibrary.org/").create(OpenLibraryApi::class.java)
    private val googleBooksApi = retrofit("https://www.googleapis.com/").create(GoogleBooksApi::class.java)
    val metadataRepository = BookMetadataRepository(
        sources = listOf(
            OpenLibraryMetadataSource(openLibraryApi),
            GoogleBooksMetadataSource(googleBooksApi, BuildConfig.GOOGLE_BOOKS_API_KEY),
            // The catalog's SRU endpoint is only served over HTTP; see network_security_config.
            LibraryOfCongressMetadataSource(
                retrofit("http://lx2.loc.gov:210/").create(LibraryOfCongressApi::class.java),
            ),
        ),
        coverStore = coverStore,
        subjectMapper = subjectMapper,
        descriptionWriter = descriptionWriter,
    )

    val libraryExporter = LibraryExporter(bookRepository, coverStore.coverDirectory)
    val libraryImporter = LibraryImporter(
        store = bookRepository,
        coverDirectory = coverStore.coverDirectory,
        stagingDirectory = File(application.cacheDir, "import-covers"),
        fetchCover = { isbn13, url -> withContext(Dispatchers.IO) { coverStore.download(isbn13, url) } },
    )
    val amazonImporter = AmazonImporter(
        store = bookRepository,
        enricher = AmazonLookup(
            IsbnFinder(openLibraryApi, googleBooksApi, BuildConfig.GOOGLE_BOOKS_API_KEY),
            metadataRepository,
        ),
    )

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            bookRepository.deleteUnusedCovers(olderThanMillis = TimeUnit.DAYS.toMillis(1))
        }
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"

    private fun createHomeChatApi(): HomeChatApi? {
        if (localLlmBaseUrl.isBlank()) return null
        if (BuildConfig.HOMECHAT_LLM_API_KEY.isBlank() || BuildConfig.HOMECHAT_LLM_MODEL.isBlank()) {
            Log.w(TAG, "HomeChat BISAC mapping and descriptions are disabled: configure its model and API key.")
            return null
        }
        return retrofit(localLlmBaseUrl.ensureTrailingSlash(), localLlmHttpClient)
            .create(HomeChatApi::class.java)
    }

    private companion object {
        const val TAG = "BookCatalogApp"
    }
}
