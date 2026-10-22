package com.example.bookcatalog.data.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverBoundaryDetectorTest {
    @Test
    fun detectsHighContrastBookCoverRectangle() {
        val crop = CoverBoundaryDetector.detect(400, 600) { x, y ->
            if (x in 80 until 320 && y in 60 until 540) WHITE else BLACK
        }

        assertNotNull(crop)
        assertEquals(0.2f, requireNotNull(crop).left, 0.02f)
        assertEquals(0.1f, crop.top, 0.02f)
        assertEquals(0.8f, crop.right, 0.02f)
        assertEquals(0.9f, crop.bottom, 0.02f)
    }

    @Test
    fun doesNotGuessCoverEdgesForUniformImage() {
        assertNull(CoverBoundaryDetector.detect(400, 600) { _, _ -> GRAY })
    }

    @Test
    fun ignoresStrongEdgesThatDoNotFormBookShape() {
        val crop = CoverBoundaryDetector.detect(400, 600) { x, y ->
            if (x in 40 until 360 && y in 180 until 350) WHITE else BLACK
        }
        assertNull(crop)
    }

    @Test
    fun detectsRectangleWithNonBlackBackground() {
        val crop = CoverBoundaryDetector.detect(500, 700) { x, y ->
            if (x in 100 until 400 && y in 70 until 630) {
                WHITE
            } else {
                DARK_GRAY
            }
        }

        assertNotNull(crop)
        assertTrue(requireNotNull(crop).left in 0.18f..0.22f)
        assertTrue(crop.top in 0.08f..0.12f)
        assertTrue(crop.right in 0.78f..0.82f)
        assertTrue(crop.bottom in 0.88f..0.92f)
    }

    private companion object {
        const val BLACK = 0xff000000.toInt()
        const val DARK_GRAY = 0xff333333.toInt()
        const val GRAY = 0xff808080.toInt()
        const val WHITE = 0xffffffff.toInt()
    }
}
