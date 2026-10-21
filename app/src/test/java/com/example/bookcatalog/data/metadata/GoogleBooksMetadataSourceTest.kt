package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.remote.GoogleBooksApi
import com.example.bookcatalog.data.remote.GoogleBooksIdentifierDto
import com.example.bookcatalog.data.remote.GoogleBooksResponseDto
import com.example.bookcatalog.data.remote.GoogleBooksVolumeDto
import com.example.bookcatalog.data.remote.GoogleBooksVolumeInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBooksMetadataSourceTest {
    @Test
    fun fallsBackToUnqualifiedIsbnAndUsesMatchingVolume() {
        val queries = mutableListOf<String>()
        val api = object : GoogleBooksApi {
            override suspend fun search(query: String, apiKey: String?): GoogleBooksResponseDto {
                queries += query
                if (query.startsWith("isbn:")) return GoogleBooksResponseDto()
                return GoogleBooksResponseDto(
                    items = listOf(
                        GoogleBooksVolumeDto(
                            volumeInfo = GoogleBooksVolumeInfoDto(
                                title = "The Particular Sadness of Lemon Cake",
                                pageCount = 292,
                                industryIdentifiers = listOf(
                                    GoogleBooksIdentifierDto("ISBN_13", "9780385533225"),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        val result = kotlinx.coroutines.runBlocking {
            GoogleBooksMetadataSource(api, "test-key").fetch("9780385533225")
        }

        assertEquals(listOf("isbn:9780385533225", "9780385533225"), queries)
        assertEquals("The Particular Sadness of Lemon Cake", result?.title)
        assertEquals(292, result?.pageCount)
    }

    @Test
    fun rejectsUnqualifiedSearchResultsForAnotherIsbn() {
        val api = object : GoogleBooksApi {
            override suspend fun search(query: String, apiKey: String?): GoogleBooksResponseDto {
                return if (query.startsWith("isbn:")) {
                    GoogleBooksResponseDto()
                } else {
                    GoogleBooksResponseDto(
                        items = listOf(
                            GoogleBooksVolumeDto(
                                volumeInfo = GoogleBooksVolumeInfoDto(
                                    title = "Another book",
                                    industryIdentifiers = listOf(
                                        GoogleBooksIdentifierDto("ISBN_13", "9780547928227"),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        val result = kotlinx.coroutines.runBlocking {
            GoogleBooksMetadataSource(api, null).fetch("9780385533225")
        }

        assertNull(result)
    }

    @Test
    fun rejectsUnqualifiedResultsWithoutIdentifiers() {
        val api = object : GoogleBooksApi {
            override suspend fun search(query: String, apiKey: String?): GoogleBooksResponseDto {
                return if (query.startsWith("isbn:")) {
                    GoogleBooksResponseDto()
                } else {
                    GoogleBooksResponseDto(
                        items = listOf(
                            GoogleBooksVolumeDto(
                                volumeInfo = GoogleBooksVolumeInfoDto(title = "Unverified result"),
                            ),
                        ),
                    )
                }
            }
        }

        val result = kotlinx.coroutines.runBlocking {
            GoogleBooksMetadataSource(api, null).fetch("9780385533225")
        }

        assertNull(result)
    }
}
