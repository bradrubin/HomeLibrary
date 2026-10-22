package com.example.bookcatalog.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class BraveWebSearchTest {
    @Test
    fun stripsMatchTagsAndEntities() {
        assertEquals(
            "RESULT 1\nTitle: Ci vuole gusto\nDescription: Tucci's memoir of food & family",
            BraveWebSearch.formatResults(
                "Ci",
                listOf("<strong>Ci</strong> vuole gusto" to "Tucci&#x27;s memoir of food &amp; family"),
            ),
        )
    }

    @Test
    fun reportsNoResults() {
        assertEquals("No web search results found for 'Ci'.", BraveWebSearch.formatResults("Ci", emptyList()))
    }
}
