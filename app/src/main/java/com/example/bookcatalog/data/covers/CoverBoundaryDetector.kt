package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import kotlin.math.abs

internal object CoverBoundaryDetector {
    fun detect(bitmap: Bitmap): NormalizedCrop? =
        detect(bitmap.width, bitmap.height, bitmap::getPixel)

    internal fun detect(
        imageWidth: Int,
        imageHeight: Int,
        pixelAt: (x: Int, y: Int) -> Int,
    ): NormalizedCrop? {
        if (imageWidth < MIN_IMAGE_EDGE || imageHeight < MIN_IMAGE_EDGE) return null
        val verticalEdges = findCandidates(
            length = imageWidth,
            sampleLength = imageHeight,
        ) { coordinate, sample ->
            contrast(pixelAt(coordinate - 1, sample), pixelAt(coordinate, sample))
        }
        val horizontalEdges = findCandidates(
            length = imageHeight,
            sampleLength = imageWidth,
        ) { coordinate, sample ->
            contrast(pixelAt(sample, coordinate - 1), pixelAt(sample, coordinate))
        }

        var best: Detection? = null
        for (left in verticalEdges) {
            for (right in verticalEdges) {
                val cropWidth = right.position - left.position
                if (cropWidth < imageWidth * MIN_WIDTH_RATIO) continue
                for (top in horizontalEdges) {
                    for (bottom in horizontalEdges) {
                        val cropHeight = bottom.position - top.position
                        if (cropHeight < imageHeight * MIN_HEIGHT_RATIO) continue
                        val aspectRatio = cropWidth.toFloat() / cropHeight
                        if (aspectRatio !in MIN_COVER_ASPECT..MAX_COVER_ASPECT) continue

                        val edges = listOf(
                            lineStrength(pixelAt, left.position, top.position, bottom.position, true),
                            lineStrength(pixelAt, right.position, top.position, bottom.position, true),
                            lineStrength(pixelAt, top.position, left.position, right.position, false),
                            lineStrength(pixelAt, bottom.position, left.position, right.position, false),
                        )
                        val weakestEdge = edges.min()
                        val averageEdge = edges.average().toFloat()
                        if (weakestEdge < MIN_EDGE_STRENGTH || averageEdge < MIN_AVERAGE_EDGE_STRENGTH) {
                            continue
                        }
                        val score = weakestEdge * 2f + averageEdge
                        if (best == null || score > best.score) {
                            best = Detection(left.position, top.position, right.position, bottom.position, score)
                        }
                    }
                }
            }
        }

        return best?.let {
            NormalizedCrop(
                left = it.left.toFloat() / imageWidth,
                top = it.top.toFloat() / imageHeight,
                right = it.right.toFloat() / imageWidth,
                bottom = it.bottom.toFloat() / imageHeight,
            )
        }
    }

    private fun findCandidates(
        length: Int,
        sampleLength: Int,
        contrastAt: (coordinate: Int, sample: Int) -> Int,
    ): List<EdgeCandidate> {
        val profile = IntArray(length)
        val sampleStart = (sampleLength * PROFILE_INSET).toInt()
        val sampleEnd = (sampleLength * (1f - PROFILE_INSET)).toInt()
        val sampleStep = (sampleLength / PROFILE_SAMPLE_COUNT).coerceAtLeast(1)
        for (coordinate in 1 until length) {
            if (coordinate < length * MIN_EDGE_POSITION || coordinate > length * MAX_EDGE_POSITION) {
                continue
            }
            var sum = 0
            var count = 0
            var sample = sampleStart
            while (sample < sampleEnd) {
                sum += contrastAt(coordinate, sample)
                count++
                sample += sampleStep
            }
            if (count > 0) profile[coordinate] = sum / count
        }

        return (1 until length - 1)
            .asSequence()
            .filter { position ->
                position >= length * MIN_EDGE_POSITION &&
                    position <= length * MAX_EDGE_POSITION &&
                    profile[position] >= profile[position - 1] &&
                    profile[position] >= profile[position + 1]
            }
            .map { EdgeCandidate(it, profile[it]) }
            .sortedByDescending(EdgeCandidate::strength)
            .take(MAX_EDGE_CANDIDATES)
            .sortedBy(EdgeCandidate::position)
            .toList()
    }

    private fun lineStrength(
        pixelAt: (x: Int, y: Int) -> Int,
        position: Int,
        start: Int,
        end: Int,
        isVertical: Boolean,
    ): Float {
        val sampleStep = ((end - start) / RECTANGLE_SAMPLE_COUNT).coerceAtLeast(1)
        var sum = 0
        var count = 0
        var sample = start
        while (sample < end) {
            val before = if (isVertical) {
                pixelAt(position - 1, sample)
            } else {
                pixelAt(sample, position - 1)
            }
            val after = if (isVertical) {
                pixelAt(position, sample)
            } else {
                pixelAt(sample, position)
            }
            sum += contrast(before, after)
            count++
            sample += sampleStep
        }
        return if (count == 0) 0f else sum.toFloat() / count
    }

    private fun contrast(first: Int, second: Int): Int = maxOf(
        abs((first ushr 16 and 0xff) - (second ushr 16 and 0xff)),
        abs((first ushr 8 and 0xff) - (second ushr 8 and 0xff)),
        abs((first and 0xff) - (second and 0xff)),
    )

    private data class EdgeCandidate(val position: Int, val strength: Int)

    private data class Detection(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float,
    )

    private const val MIN_IMAGE_EDGE = 80
    private const val PROFILE_INSET = 0.15f
    private const val PROFILE_SAMPLE_COUNT = 100
    private const val RECTANGLE_SAMPLE_COUNT = 100
    private const val MIN_EDGE_POSITION = 0.03f
    private const val MAX_EDGE_POSITION = 0.97f
    private const val MIN_WIDTH_RATIO = 0.2f
    private const val MIN_HEIGHT_RATIO = 0.3f
    private const val MIN_COVER_ASPECT = 0.4f
    private const val MAX_COVER_ASPECT = 0.9f
    private const val MIN_EDGE_STRENGTH = 18f
    private const val MIN_AVERAGE_EDGE_STRENGTH = 28f
    private const val MAX_EDGE_CANDIDATES = 12
}
