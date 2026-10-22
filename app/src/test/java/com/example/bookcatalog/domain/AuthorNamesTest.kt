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
    fun sortsBySurnameWithParticles() {
        assertEquals("herbert frank", AuthorNames.sortKey("Frank Herbert; Brian Herbert"))
        assertEquals("le guin ursula k.", AuthorNames.sortKey("Ursula K. Le Guin"))
        assertEquals("tucci stanley", AuthorNames.sortKey("Tucci, Stanley"))
        assertEquals("king martin luther", AuthorNames.sortKey("Martin Luther King, Jr."))
        assertEquals("bronte charlotte", AuthorNames.sortKey("Charlotte Brontë"))
        assertEquals("homer", AuthorNames.sortKey("Homer"))
    }
}
