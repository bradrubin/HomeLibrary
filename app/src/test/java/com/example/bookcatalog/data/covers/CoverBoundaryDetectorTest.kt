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
        val detected = CoverBoundaryDetector.detect(400, 600) { x, y ->
            if (x in 80 until 320 && y in 60 until 540) WHITE else BLACK
        }

        assertNotNull(detected)
        assertEquals(0f, requireNotNull(detected).rotationDegrees)
        val crop = detected.crop
        assertEquals(0.2f, crop.left, 0.02f)
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
        }?.crop
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
        }?.crop

        assertNotNull(crop)
        assertTrue(requireNotNull(crop).left in 0.18f..0.22f)
        assertTrue(crop.top in 0.08f..0.12f)
        assertTrue(crop.right in 0.78f..0.82f)
        assertTrue(crop.bottom in 0.88f..0.92f)
    }

    @Test
    fun straightensBlurredTiltedDarkBookOnWhite() {
        val detected = CoverBoundaryDetector.detect(1200, 1600) { x, y ->
            tiltedBook(x, y, degrees = 3.0, background = LIGHT, book = DARK)
        }

        requireNotNull(detected)
        assertEquals(-3f, detected.rotationDegrees, 0.3f)
        // Turned back 3 degrees the photo is about 1282x1661, with the 700x1050 book centered.
        assertEquals(0.227f, detected.crop.left, 0.02f)
        assertEquals(0.184f, detected.crop.top, 0.02f)
        assertEquals(0.773f, detected.crop.right, 0.02f)
        assertEquals(0.816f, detected.crop.bottom, 0.02f)
    }

    @Test
    fun straightensBookTiltedCounterclockwise() {
        val detected = CoverBoundaryDetector.detect(1200, 1600) { x, y ->
            tiltedBook(x, y, degrees = -8.0, background = LIGHT, book = PURPLE)
        }

        requireNotNull(detected)
        assertEquals(8f, detected.rotationDegrees, 0.5f)
        // Turned forward 8 degrees the photo is about 1411x1751, with the 700x1050 book centered.
        assertEquals(0.252f, detected.crop.left, 0.02f)
        assertEquals(0.200f, detected.crop.top, 0.02f)
        assertEquals(0.748f, detected.crop.right, 0.02f)
        assertEquals(0.800f, detected.crop.bottom, 0.02f)
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
        }?.crop

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
        }?.crop

        requireNotNull(crop)
        assertEquals(0.25f, crop.left, 0.02f)
        assertEquals(0.75f, crop.right, 0.02f)
    }

    /** A 700x1050 book turned clockwise by [degrees] about the photo's center, with a soft edge. */
    private fun tiltedBook(x: Int, y: Int, degrees: Double, background: Int, book: Int): Int {
        val angle = Math.toRadians(degrees)
        val dx = x - 600.0
        val dy = y - 800.0
        val u = dx * cos(angle) + dy * sin(angle)
        val v = -dx * sin(angle) + dy * cos(angle)
        val outside = maxOf(abs(u) - 350, abs(v) - 525)
        return blend(background, book, ((4 - outside) / 8).coerceIn(0.0, 1.0))
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
