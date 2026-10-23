package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorNamesTest {
    @Test
    fun putsInvertedNamesInNaturalOrder() {
        assertEquals("Stanley Tucci", AuthorNames.naturalOrder("Tucci, Stanley"))
        assertEquals("F. Scott Fitzgerald", AuthorNames.naturalOrder("Fitzgerald, F. Scott (Francis Scott), 1896-1940"))
        assertEquals("Martin Luther King, Jr.", AuthorNames.naturalOrder("King, Martin Luther, Jr."))
    }

    @Test
    fun keepsNamesAlreadyInNaturalOrder() {
        assertEquals("Frank Herbert", AuthorNames.naturalOrder("Frank Herbert"))
        assertEquals("Martin Luther King, Jr.", AuthorNames.naturalOrder("Martin Luther King, Jr."))
        assertEquals("National Audubon Society", AuthorNames.naturalOrder("National Audubon Society"))
        assertEquals("Penguin Books, Ltd.", AuthorNames.naturalOrder("Penguin Books, Ltd."))
    }

    @Test
    fun normalizesEachNameInList() {
        assertEquals("Sergio Adorni; Karen Primorac", AuthorNames.normalize(" Adorni, Sergio ;Karen Primorac; "))
        assertNull(AuthorNames.normalize(" ; "))
    }

    @Test
    fun dropsASingleMiddleInitial() {
        assertEquals("Richard Ablin", AuthorNames.withoutMiddleInitial("Richard J. Ablin"))
        assertEquals("Ursula Le Guin", AuthorNames.withoutMiddleInitial("Ursula K. Le Guin"))
        assertEquals("Harry Truman", AuthorNames.withoutMiddleInitial("Harry S Truman"))
        assertEquals("Frederick Brooks, Jr.", AuthorNames.withoutMiddleInitial("Frederick P. Brooks, Jr."))
        assertEquals("F. Scott Fitzgerald", AuthorNames.withoutMiddleInitial("F. Scott Fitzgerald"))
        assertEquals("J. K. Rowling", AuthorNames.withoutMiddleInitial("J. K. Rowling"))
        assertEquals("George R. R. Martin", AuthorNames.withoutMiddleInitial("George R. R. Martin"))
        assertEquals("Martin Luther King, Jr.", AuthorNames.withoutMiddleInitial("Martin Luther King, Jr."))
        assertEquals("Amir Aczel; David Griffiths", AuthorNames.normalize("Aczel, Amir D.; David J. Griffiths"))
    }

    @Test
    fun sortsBySurnameWithParticles() {
        assertEquals("herbert frank", AuthorNames.sortKey("Frank Herbert; Brian Herbert"))
        assertEquals("le guin ursula k.", AuthorNames.sortKey("Ursula K. Le Guin"))
        assertEquals("tucci stanley", AuthorNames.sortKey("Tucci, Stanley"))
        assertEquals("king martin luther", AuthorNames.sortKey("Martin Luther King, Jr."))
        assertEquals("bronte charlotte", AuthorNames.sortKey("Charlotte Brontë"))
        assertEquals("homer", AuthorNames.sortKey("Homer"))
    }
}
