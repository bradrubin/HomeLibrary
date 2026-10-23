package com.example.bookcatalog.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnFinderTest {
    @Test
    fun searchesWithoutSubtitleOrSeries() {
        assertEquals("Inferno", IsbnFinder.searchTitle("Inferno: A Novel (Robert Langdon Book 4)"))
        assertEquals("Desert Solitaire", IsbnFinder.searchTitle("Desert Solitaire"))
    }

    @Test
    fun keepsOnlyEnglishLanguageIsbns() {
        // A Korean edition of a Bill Bryson book was the first ISBN Open Library listed.
        assertEquals(
            listOf("9780767903820", "9781857979992"),
            IsbnFinder.englishIsbn13s(listOf("9788950917364", "0767903820", "9780767903820", "9781857979992")),
        )
    }

    @Test
    fun findsTheSurname() {
        assertEquals("Abbey", IsbnFinder.surname("Edward Abbey"))
        assertEquals("Brooks", IsbnFinder.surname("Frederick Brooks Jr."))
        assertEquals("Ablin", IsbnFinder.surname("Richard J. Ablin; Ronald Piana"))
    }

    @Test
    fun matchesTitlesAndAuthorsLoosely() {
        assertTrue(IsbnFinder.titlesAgree("The Unspeakable : and other subjects of discussion", "The Unspeakable"))
        assertTrue(IsbnFinder.titlesAgree("I'm a stranger here myself", "I'm a Stranger Here Myself"))
        assertFalse(IsbnFinder.titlesAgree("The Stranger Beside Me", "The Plague"))
        assertTrue(IsbnFinder.authorAgrees(listOf("Marie Kondo"), "Kondō"))
        assertFalse(IsbnFinder.authorAgrees(listOf("Ann Rule"), "Camus"))
    }
}
