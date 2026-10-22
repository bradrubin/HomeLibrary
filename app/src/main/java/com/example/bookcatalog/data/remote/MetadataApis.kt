package com.example.bookcatalog.data.remote

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface OpenLibraryApi {
    @Headers("User-Agent: BookCatalog/1.0 (personal home library)")
    @GET("api/books")
    suspend fun getBook(
        @Query("bibkeys") bibkey: String,
        @Query("format") format: String = "json",
        @Query("jscmd") command: String = "data",
    ): Map<String, OpenLibraryBookDto>
}

interface GoogleBooksApi {
    @GET("books/v1/volumes")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String?,
    ): GoogleBooksResponseDto
}

interface LibraryOfCongressApi {
    @GET("LCDB")
    suspend fun searchRetrieve(
        @Query("query") query: String,
        @Query("version") version: String = "1.1",
        @Query("operation") operation: String = "searchRetrieve",
        @Query("maximumRecords") maximumRecords: Int = 5,
        @Query("recordSchema") recordSchema: String = "mods",
    ): ResponseBody
}

data class OpenLibraryBookDto(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<OpenLibraryNameDto>? = null,
    val publishers: List<OpenLibraryNameDto>? = null,
    @Json(name = "publish_date") val publishDate: String? = null,
    @Json(name = "number_of_pages") val numberOfPages: Int? = null,
    val subjects: List<OpenLibraryNameDto>? = null,
    val cover: OpenLibraryCoverDto? = null,
)

data class OpenLibraryNameDto(val name: String? = null)

data class OpenLibraryCoverDto(
    val large: String? = null,
    val medium: String? = null,
    val small: String? = null,
)

data class GoogleBooksResponseDto(
    val items: List<GoogleBooksVolumeDto>? = null,
)

data class GoogleBooksVolumeDto(
    @Json(name = "volumeInfo") val volumeInfo: GoogleBooksVolumeInfoDto? = null,
)

data class GoogleBooksVolumeInfoDto(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    @Json(name = "publishedDate") val publishedDate: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    val categories: List<String>? = null,
    val language: String? = null,
    @Json(name = "imageLinks") val imageLinks: GoogleBooksImageLinksDto? = null,
    @Json(name = "industryIdentifiers") val industryIdentifiers: List<GoogleBooksIdentifierDto>? = null,
)

data class GoogleBooksIdentifierDto(
    val type: String? = null,
    val identifier: String? = null,
)

data class GoogleBooksImageLinksDto(
    val thumbnail: String? = null,
)
