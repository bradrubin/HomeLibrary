package com.example.bookcatalog.data.covers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Straightens a photo: a 3×3 projective matrix, row-major as android.graphics.Matrix stores it,
 * taking photo pixels to pixels of a [width] by [height] result that holds the whole photo.
 * A rotation is the simple case; a perspective matrix also squares up a keystoned cover.
 */
@Parcelize
class PhotoTransform(val values: FloatArray, val width: Int, val height: Int) : Parcelable {
    val isIdentity: Boolean
        get() = values.contentEquals(IDENTITY)

    fun map(x: Float, y: Float): Pair<Float, Float> {
        val w = values[6] * x + values[7] * y + values[8]
        return (values[0] * x + values[1] * y + values[2]) / w to (values[3] * x + values[4] * y + values[5]) / w
    }

    /** This transform followed by a quarter turn clockwise. */
    fun thenRotateClockwise(): PhotoTransform =
        PhotoTransform(multiply(floatArrayOf(0f, -1f, height.toFloat(), 1f, 0f, 0f, 0f, 0f, 1f), values), height, width)

    companion object {
        private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        fun identity(width: Int, height: Int) = PhotoTransform(IDENTITY.copyOf(), width, height)

        /** Rotates clockwise about the photo's center, into bounds that fit the whole photo. */
        fun rotation(degrees: Float, width: Int, height: Int): PhotoTransform {
            if (degrees == 0f) return identity(width, height)
            val radians = Math.toRadians(degrees.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            val newWidth = (width * abs(cos) + height * abs(sin)).roundToInt()
            val newHeight = (width * abs(sin) + height * abs(cos)).roundToInt()
            // Turn about the old center, then move it to the new center.
            val cx = width / 2f
            val cy = height / 2f
            return PhotoTransform(
                floatArrayOf(
                    cos, -sin, newWidth / 2f - cos * cx + sin * cy,
                    sin, cos, newHeight / 2f - sin * cx - cos * cy,
                    0f, 0f, 1f,
                ),
                newWidth,
                newHeight,
            )
        }

        /**
         * Maps the four [from] points onto the four [to] points, moved so the whole photo fits.
         * Returns null when the photo would fold over itself or grow past [maxGrowth] times its
         * size, which only happens when the points are far from a gentle keystone.
         */
        fun perspective(
            from: List<Pair<Double, Double>>,
            to: List<Pair<Double, Double>>,
            width: Int,
            height: Int,
            maxGrowth: Double = 2.0,
        ): PhotoTransform? {
            val h = solveHomography(from, to) ?: return null
            val corners = listOf(0.0 to 0.0, width.toDouble() to 0.0, width.toDouble() to height.toDouble(), 0.0 to height.toDouble())
            val mapped = corners.map { (x, y) ->
                val w = h[6] * x + h[7] * y + h[8]
                if (w <= 0) return null
                (h[0] * x + h[1] * y + h[2]) / w to (h[3] * x + h[4] * y + h[5]) / w
            }
            val minX = mapped.minOf { it.first }
            val minY = mapped.minOf { it.second }
            val newWidth = ceil(mapped.maxOf { it.first } - minX)
            val newHeight = ceil(mapped.maxOf { it.second } - minY)
            if (newWidth > width * maxGrowth || newHeight > height * maxGrowth) return null
            val shifted = multiply(
                floatArrayOf(1f, 0f, -minX.toFloat(), 0f, 1f, -minY.toFloat(), 0f, 0f, 1f),
                FloatArray(9) { h[it].toFloat() },
            )
            return PhotoTransform(shifted, newWidth.toInt(), newHeight.toInt())
        }

        /** Solves for the matrix taking each [from] point to its [to] point, with the last entry 1. */
        private fun solveHomography(from: List<Pair<Double, Double>>, to: List<Pair<Double, Double>>): DoubleArray? {
            val rows = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = from[i]
                val (u, v) = to[i]
                rows[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                rows[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            // Gaussian elimination with partial pivoting on the 8×8 system.
            for (column in 0 until 8) {
                val pivot = (column until 8).maxBy { abs(rows[it][column]) }
                if (abs(rows[pivot][column]) < 1e-9) return null
                rows[column] = rows[pivot].also { rows[pivot] = rows[column] }
                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = rows[row][column] / rows[column][column]
                    for (k in column..8) rows[row][k] -= factor * rows[column][k]
                }
            }
            return DoubleArray(9) { if (it == 8) 1.0 else rows[it][8] / rows[it][it] }
        }

        private fun multiply(a: FloatArray, b: FloatArray) = FloatArray(9) { index ->
            val row = index / 3
            val column = index % 3
            (0 until 3).sumOf { (a[row * 3 + it] * b[it * 3 + column]).toDouble() }.toFloat()
        }
    }
}
