package com.example.bookcatalog.data.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoTransformTest {
    @Test
    fun rotationKeepsCenterCenteredInGrownBounds() {
        val transform = PhotoTransform.rotation(90f, 400, 600)

        assertEquals(600, transform.width)
        assertEquals(400, transform.height)
        assertMaps(transform, 200f to 300f, 300f to 200f)
        // Clockwise, so the top left corner ends up top right.
        assertMaps(transform, 0f to 0f, 600f to 0f)
    }

    @Test
    fun quarterTurnFollowsTheTransform() {
        val turned = PhotoTransform.identity(400, 600).thenRotateClockwise()

        assertEquals(600, turned.width)
        assertEquals(400, turned.height)
        assertMaps(turned, 0f to 0f, 600f to 0f)
        assertMaps(turned, 0f to 600f, 0f to 0f)
    }

    @Test
    fun perspectiveMapsCornersOntoRectangle() {
        val from = listOf(380.0 to 300.0, 820.0 to 300.0, 860.0 to 1300.0, 340.0 to 1300.0)
        val to = listOf(370.0 to 300.0, 830.0 to 300.0, 830.0 to 1300.0, 370.0 to 1300.0)

        val transform = requireNotNull(PhotoTransform.perspective(from, to, 1200, 1600))

        // The result is shifted so the whole photo fits, so the corners land on the rectangle
        // up to one shared offset.
        val (offsetX, offsetY) = transform.map(380f, 300f).let { (x, y) -> x - 370f to y - 300f }
        from.zip(to).forEach { (source, target) ->
            assertMaps(transform, source.first.toFloat() to source.second.toFloat(), target.first.toFloat() + offsetX to target.second.toFloat() + offsetY)
        }
    }

    @Test
    fun refusesPerspectiveThatFoldsThePhoto() {
        val from = listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val to = listOf(0.0 to 0.0, 100.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0)

        assertNull(PhotoTransform.perspective(from, to, 1000, 1000))
    }

    private fun assertMaps(transform: PhotoTransform, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        val (x, y) = transform.map(from.first, from.second)
        assertEquals(to.first, x, 0.5f)
        assertEquals(to.second, y, 0.5f)
    }
}
