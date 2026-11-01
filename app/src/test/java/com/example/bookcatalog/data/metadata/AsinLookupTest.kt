package com.example.bookcatalog.data.metadata

import com.example.bookcatalog.data.io.AmazonBookEnricher
import com.example.bookcatalog.data.io.EnrichResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AsinLookupTest {
    private val asin = "B07XB49J92"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val matching = AmazonBookEnricher { book ->
        EnrichResult(book.copy(isbn13 = "9780671695880"), matchedIsbn = true, complete = true)
    }

    @Test
    fun fillsInAnIdentifiedBookFromItsEdition() = runBlocking {
        val lookup = AsinLookup({ AsinIdentity("Desert Solitaire", "Edward Abbey") }, matching)

        val result = lookup.lookup(asin)

        assertEquals(asin, result.book.asin)
        assertEquals("Desert Solitaire", result.book.title)
        assertEquals("Edward Abbey", result.book.authors)
        assertEquals("9780671695880", result.book.isbn13)
        assertTrue(result.message.orEmpty().contains("check that it's the right book"))
        assertNull(result.warning)
    }

    @Test
    fun warnsWhenNoPrintEditionMatches() = runBlocking {
        val lookup = AsinLookup(
            { AsinIdentity("Desert Solitaire", null) },
            { book -> EnrichResult(book, matchedIsbn = false, complete = true) },
        )

        val result = lookup.lookup(asin)

        assertEquals("Desert Solitaire", result.book.title)
        assertNotNull(result.warning)
    }

    @Test
    fun separatesASubtitleTheModelLeftInTheTitle() = runBlocking {
        val lookup = AsinLookup(
            { AsinIdentity("Present at the Creation: Discovering the Higgs Boson", "Amir Aczel") },
            { book -> EnrichResult(book, matchedIsbn = false, complete = true) },
        )

        val result = lookup.lookup(asin)

        assertEquals("Present at the Creation", result.book.title)
        assertEquals("Discovering the Higgs Boson", result.book.subtitle)
    }

    @Test
    fun triesTheOtherAuthorsWhenANarratorComesFirst() = runBlocking {
        val tried = mutableListOf<String?>()
        val lookup = AsinLookup(
            { AsinIdentity("Present at the Creation", "Byron Wagner; Amir Aczel") },
            { book ->
                tried += book.authors
                EnrichResult(book, matchedIsbn = book.authors == "Amir Aczel", complete = true)
            },
        )

        val result = lookup.lookup(asin)

        assertEquals(listOf("Byron Wagner; Amir Aczel", "Amir Aczel"), tried)
        assertEquals("Amir Aczel", result.book.authors)
        assertNull(result.warning)
    }

    @Test
    fun keepsEveryAuthorWhenNoneFindsAnEdition() = runBlocking {
        val lookup = AsinLookup(
            { AsinIdentity("Good Omens", "Neil Gaiman; Terry Pratchett") },
            { book -> EnrichResult(book, matchedIsbn = false, complete = true) },
        )

        assertEquals("Neil Gaiman; Terry Pratchett", lookup.lookup(asin).book.authors)
    }

    @Test
    fun opensWithOnlyTheAsinWhenTheBookIsUnknown() = runBlocking {
        val result = AsinLookup({ null }, matching).lookup(asin)

        assertEquals(asin, result.book.asin)
        assertEquals("", result.book.title)
        assertFalse(result.sourcesFailed)
    }

    @Test
    fun saysToTryAgainWhenIdentifyingFails() = runBlocking {
        val result = AsinLookup({ throw IOException("offline") }, matching, logFailure = { _, _ -> }).lookup(asin)

        assertEquals(asin, result.book.asin)
        assertTrue(result.sourcesFailed)
    }

    @Test
    fun explainsWhenIdentifyingIsNotConfigured() = runBlocking {
        val result = AsinLookup(null, matching).lookup(asin)

        assertEquals(asin, result.book.asin)
        assertTrue(result.message.orEmpty().contains("aren't configured"))
    }

    @Test
    fun readsTheModelsAnswer() {
        assertEquals(
            AsinIdentity("Good Omens", "Neil Gaiman; Terry Pratchett"),
            HomeChatAsinIdentifier.parseIdentity(
                "```json\n{\"title\":\" Good Omens \",\"authors\":[\"Neil Gaiman\",\"Terry Pratchett\",\" \"]}\n```",
                moshi,
            ),
        )
        assertNull(HomeChatAsinIdentifier.parseIdentity("""{"title":"","authors":[]}""", moshi))
    }
}
