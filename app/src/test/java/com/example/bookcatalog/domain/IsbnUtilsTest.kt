package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnUtilsTest {
    @Test
    fun normalizesAndValidatesIsbn10() {
        assertEquals("0306406152", IsbnUtils.normalize("0-306 40615-2"))
        assertTrue(IsbnUtils.isValidIsbn10("0306406152"))
        assertTrue(IsbnUtils.isValidIsbn10("0-8044-2957-X"))
        assertFalse(IsbnUtils.isValidIsbn10("0306406153"))
    }

    @Test
    fun validatesAndConvertsIsbn13() {
        assertTrue(IsbnUtils.isValidIsbn13("9780385533225"))
        assertEquals("9780306406157", IsbnUtils.isbn10To13("0306406152"))
        assertEquals("0306406152", IsbnUtils.isbn13To10("9780306406157"))
        assertNull(IsbnUtils.isbn13To10("9791090636071"))
        assertFalse(IsbnUtils.isValidIsbn13("9780385533226"))
    }

    @Test
    fun barcodeAcceptsOnlyValidBookEans() {
        assertEquals("9780385533225", IsbnUtils.fromBarcode("9780385533225"))
        assertEquals("9791090636071", IsbnUtils.fromBarcode("9791090636071"))
        assertNull(IsbnUtils.fromBarcode("978038553322599999"))
        assertNull(IsbnUtils.fromBarcode("0306406152"))
        assertNull(IsbnUtils.fromBarcode("012345678905"))
    }
}
