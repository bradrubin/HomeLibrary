package com.example.bookcatalog.data.metadata

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeChatBisacSubjectMapperTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun acceptsFullHeadingsAndRemovesDuplicates() {
        val subjects = HomeChatBisacSubjectMapper.parseSubjects(
            """{"subjects":["MAJOR CATEGORY / First Topic","MAJOR CATEGORY / First Topic","OTHER CATEGORY / Second Topic / Qualifier"]}""",
            moshi,
        )

        assertEquals(
            listOf(
                "MAJOR CATEGORY / First Topic",
                "OTHER CATEGORY / Second Topic / Qualifier",
            ),
            subjects,
        )
    }

    @Test
    fun acceptsJerusalemHistoryHeading() {
        assertEquals(
            listOf("HISTORY / Middle East / Israel & Palestine"),
            HomeChatBisacSubjectMapper.parseSubjects(
                """{"subjects":["HISTORY / Middle East / Israel & Palestine"]}""",
                moshi,
            ),
        )
    }

    @Test
    fun rejectsHeadingsWithoutAtLeastTwoParts() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeChatBisacSubjectMapper.parseSubjects(
                """{"subjects":["MAJOR CATEGORY"]}""",
                moshi,
            )
        }
    }

    @Test
    fun rejectsHeadingWithNoncanonicalMajorHeadingCase() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeChatBisacSubjectMapper.parseSubjects(
                """{"subjects":["History / Middle East / General"]}""",
                moshi,
            )
        }
    }

    @Test
    fun rejectsHeadingsWithTooManyLevels() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeChatBisacSubjectMapper.parseSubjects(
                """{"subjects":["MAJOR CATEGORY / First / Second / Third / Fourth"]}""",
                moshi,
            )
        }
    }

    @Test
    fun acceptsEmptyClassification() {
        assertEquals(
            emptyList<String>(),
            HomeChatBisacSubjectMapper.parseSubjects("""{"subjects":[]}""", moshi),
        )
    }
}
