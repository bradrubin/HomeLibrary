package com.example.bookcatalog.data.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

    @Test
    fun enclosesBlurredTiltedDarkBookOnWhite() {
        val angle = Math.toRadians(3.0)
        val centerX = 600.0
        val centerY = 800.0
        val crop = CoverBoundaryDetector.detect(1200, 1600) { x, y ->
            // Signed distance outside a 700x1050 rectangle rotated about the center.
            val dx = x - centerX
            val dy = y - centerY
            val u = dx * cos(angle) + dy * sin(angle)
            val v = -dx * sin(angle) + dy * cos(angle)
            val outside = maxOf(abs(u) - 350, abs(v) - 525)
            blend(LIGHT, DARK, ((4 - outside) / 8).coerceIn(0.0, 1.0))
        }

        // The rotated book spans about 0.19-0.81 across and 0.15-0.85 down.
        requireNotNull(crop)
        assertEquals(0.19f, crop.left, 0.02f)
        assertEquals(0.15f, crop.top, 0.02f)
        assertEquals(0.81f, crop.right, 0.02f)
        assertEquals(0.85f, crop.bottom, 0.02f)
    }

    @Test
    fun ignoresThinVeinTouchingBook() {
        val crop = CoverBoundaryDetector.detect(1200, 1600) { x, y ->
            when {
                x in 300 until 900 && y in 300 until 1300 -> PURPLE
                x in 196 until 204 -> VEIN
                y in 596 until 604 && x in 196 until 300 -> VEIN
                else -> LIGHT
            }
        }

        requireNotNull(crop)
        assertEquals(0.25f, crop.left, 0.02f)
        assertEquals(0.1875f, crop.top, 0.02f)
        assertEquals(0.75f, crop.right, 0.02f)
        assertEquals(0.8125f, crop.bottom, 0.02f)
    }

    @Test
    fun leavesOutSoftShadowBesideBook() {
        val crop = CoverBoundaryDetector.detect(1200, 1600) { x, y ->
            when {
                x in 300 until 900 && y in 300 until 1300 -> PURPLE
                x in 160 until 300 && y in 300 until 1300 -> blend(LIGHT, SHADOW, (x - 160) / 140.0)
                else -> LIGHT
            }
        }

        requireNotNull(crop)
        assertEquals(0.25f, crop.left, 0.02f)
        assertEquals(0.75f, crop.right, 0.02f)
    }

    private fun blend(from: Int, to: Int, amount: Double): Int {
        fun channel(shift: Int) =
            ((from ushr shift and 0xff) * (1 - amount) + (to ushr shift and 0xff) * amount).toInt()
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private companion object {
        const val LIGHT = 0xffebebe6.toInt()
        const val DARK = 0xff1e2328.toInt()
        const val PURPLE = 0xffb45a9e.toInt()
        const val VEIN = 0xffc8784b.toInt()
        const val SHADOW = 0xff5f534d.toInt()
        const val BLACK = 0xff000000.toInt()
        const val DARK_GRAY = 0xff333333.toInt()
        const val GRAY = 0xff808080.toInt()
        const val WHITE = 0xffffffff.toInt()
    }
}
