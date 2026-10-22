package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormatTest {
    @Test
    fun normalizesToListedSpelling() {
        assertEquals("ePub", BookFormat.normalize(" EPUB "))
        assertEquals("PDF", BookFormat.normalize("pdf"))
        assertEquals("Box set", BookFormat.normalize("BOX SET"))
        assertNull(BookFormat.normalize("Paperback"))
        assertNull(BookFormat.normalize(null))
    }
}
