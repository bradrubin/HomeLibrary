package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationYearTest {
    @Test
    fun extractsYearFromSourceFormats() {
        listOf("1999", "1999-05", "1999-05-12", "May 1999", "May 12, 1999", "c1999", "[1999?]", "©1999.")
            .forEach { assertEquals(it, "1999", PublicationYear.from(it)) }
    }

    @Test
    fun skipsNumbersThatArentYears() {
        assertEquals("2001", PublicationYear.from("0123 printing, 2001"))
        assertNull(PublicationYear.from("19th century"))
        assertNull(PublicationYear.from("199"))
        assertNull(PublicationYear.from(""))
        assertNull(PublicationYear.from(null))
    }

    @Test
    fun validatesEnteredYear() {
        assertTrue(PublicationYear.isValid("1999"))
        assertFalse(PublicationYear.isValid("May 1999"))
        assertFalse(PublicationYear.isValid("99"))
        assertFalse(PublicationYear.isValid("0999"))
    }
}
