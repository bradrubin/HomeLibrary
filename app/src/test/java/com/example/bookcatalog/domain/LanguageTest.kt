package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {
    @Test
    fun spellsOutCodes() {
        assertEquals("English", Language.name("en"))
        assertEquals("Italian", Language.name("it"))
        assertEquals("French", Language.name("fra"))
        assertEquals("French", Language.name("fre"))
        assertEquals("English", Language.name("eng"))
        assertEquals("Chinese", Language.name("zh-CN"))
    }

    @Test
    fun keepsNamesAndUnknownText() {
        assertEquals("Italian", Language.name("italian"))
        assertEquals("English", Language.name(" English "))
        assertEquals("Xx", Language.name("xx"))
        assertNull(Language.name(" "))
        assertNull(Language.name(null))
    }
}
