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

    @Test
    fun mapsSourceFormatsToClosestChoice() {
        mapOf(
            "Paperback" to "Softcover",
            "Mass Market Paperback" to "Softcover",
            "(pbk.)" to "Softcover",
            "Hardcover" to "Hardcover",
            "Library Binding" to "Hardcover",
            "Board book" to "Hardcover",
            "Hardcover Box Set" to "Box set",
            "Boxed set" to "Box set",
            "Audio CD" to "Audiobook",
            "MP3 CD" to "Audiobook",
            "Kindle Edition" to "Kindle",
            "EPUB" to "ePub",
            "PDF" to "PDF",
            "Spiral-bound" to "Other",
        ).forEach { (source, choice) -> assertEquals(source, choice, BookFormat.fromSource(source)) }
    }

    @Test
    fun leavesGenericOrUnknownFormatsUnset() {
        assertNull(BookFormat.fromSource("eBook"))
        assertNull(BookFormat.fromSource("Electronic resource"))
        assertNull(BookFormat.fromSource("alk. paper"))
        assertNull(BookFormat.fromSource("Cards"))
        assertNull(BookFormat.fromSource(null))
    }
}
