package com.example.bookcatalog.data.remote

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenLibraryApi {
    @Headers("User-Agent: BookCatalog/1.0 (personal home library)")
    @GET("api/books")
    suspend fun getBook(
        @Query("bibkeys") bibkey: String,
        @Query("format") format: String = "json",
        @Query("jscmd") command: String = "data",
    ): Map<String, OpenLibraryBookDto>

    /** The edition record, which has the format that the books API leaves out. */
    @Headers("User-Agent: BookCatalog/1.0 (personal home library)")
    @GET("isbn/{isbn}.json")
    suspend fun getEdition(@Path("isbn") isbn13: String): OpenLibraryEditionDto

    /** Searches works by title and author; each result lists the ISBNs of all its editions. */
    @Headers("User-Agent: BookCatalog/1.0 (personal home library)")
    @GET("search.json")
    suspend fun search(
        @Query("title") title: String,
        @Query("author") author: String?,
        @Query("fields") fields: String = "title,author_name,isbn",
        @Query("limit") limit: Int = 5,
    ): OpenLibrarySearchResponseDto

    /** Finds the work an ISBN belongs to, whose cover may come from any of its editions. */
    @Headers("User-Agent: BookCatalog/1.0 (personal home library)")
    @GET("search.json")
    suspend fun searchByIsbn(
        @Query("isbn") isbn13: String,
        @Query("fields") fields: String = "cover_i",
        @Query("limit") limit: Int = 1,
    ): OpenLibrarySearchResponseDto
}

data class OpenLibrarySearchResponseDto(
    val docs: List<OpenLibrarySearchDocDto> = emptyList(),
)

data class OpenLibrarySearchDocDto(
    val title: String? = null,
    @Json(name = "author_name") val authorNames: List<String>? = null,
    val isbn: List<String>? = null,
    @Json(name = "cover_i") val coverId: Long? = null,
)

data class OpenLibraryEditionDto(
    @Json(name = "physical_format") val physicalFormat: String? = null,
)

interface GoogleBooksApi {
    @GET("books/v1/volumes")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String?,
    ): GoogleBooksResponseDto
}

interface BraveSearchApi {
    @Headers("Accept: application/json")
    @GET("res/v1/web/search")
    suspend fun search(
        @Header("X-Subscription-Token") apiKey: String,
        @Query("q") query: String,
        @Query("count") count: Int,
    ): BraveSearchResponseDto
}

data class BraveSearchResponseDto(
    val web: BraveWebResultsDto? = null,
)

data class BraveWebResultsDto(
    val results: List<BraveWebResultDto> = emptyList(),
)

data class BraveWebResultDto(
    val title: String? = null,
    val description: String? = null,
)

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
