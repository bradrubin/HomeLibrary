package com.example.bookcatalog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCaseTest {
    @Test
    fun capitalizesPrincipalWordsAndKeepsShortWordsLowercase() {
        assertEquals(
            "The Lord of the Rings: The Fellowship of the Ring",
            TitleCase.format("the lord of the rings: the fellowship of the ring"),
        )
    }

    @Test
    fun preservesMixedCaseBrandsAndKnownAcronyms() {
        assertEquals(
            "Learning to Use iPhone and NASA",
            TitleCase.format("learning to use iPhone and NASA"),
        )
    }

    @Test
    fun formatsAllCapsInputWithoutLowercasingKnownAcronyms() {
        assertEquals(
            "The Great Gatsby",
            TitleCase.format("THE GREAT GATSBY"),
        )
    }
}
