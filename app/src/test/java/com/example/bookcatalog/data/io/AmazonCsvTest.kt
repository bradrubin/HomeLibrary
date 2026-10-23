package com.example.bookcatalog.data.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class AmazonCsvTest {
    @Test
    fun readsPurchasedKindleAndAudibleBooksBelowTheNotes() {
        val list = AmazonCsv.read(requireNotNull(javaClass.classLoader).getResourceAsStream("amazon-book-list.csv"))

        assertEquals(listOf("B07XB49J92", "B00HBQ6NY8", "B00006QFA6", "B0045XSWNA"), list.items.map { it.asin })
        assertEquals(1, list.libraryLoans)
        assertEquals(2, list.borrowed)
        assertEquals(1, list.unavailable)
        assertEquals(1, list.otherFormats)

        val solitaire = list.items[0]
        assertEquals("Desert Solitaire", solitaire.title)
        assertNull(solitaire.subtitle)
        assertEquals("Edward Abbey", solitaire.authors)
        assertEquals("Kindle", solitaire.format)
        assertEquals(354, solitaire.pageCount)
        assertEquals(LocalDate.of(2020, 11, 8), solitaire.acquired)
        assertEquals(listOf("Science & Math", "Biographies & Memoirs"), solitaire.genres)

        val hoax = list.items[1]
        assertEquals("The Great Prostate Hoax", hoax.title)
        assertEquals("How Big Medicine Hijacked the PSA Test", hoax.subtitle)
        assertEquals("Richard J. Ablin; Ronald Piana", hoax.authors)

        // Audible's author list names the narrator and studio too, so only the first author is kept.
        val entanglement = list.items[2]
        assertEquals("Audiobook", entanglement.format)
        assertEquals("Amir D. Aczel", entanglement.authors)
        assertNull(entanglement.pageCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAFileWithoutTheAmazonHeader() {
        read("isbn13,title\r\n9780385533225,Plain\r\n")
    }

    @Test
    fun dropsRolesFromKindleAuthors() {
        assertEquals(
            "William Faulkner; Jesmyn Ward",
            AmazonCsv.authors("Kindle", "Faulkner, William", "William Faulkner, Jesmyn Ward - introduction"),
        )
    }

    private fun read(text: String) = AmazonCsv.read(ByteArrayInputStream(text.toByteArray()))
}
