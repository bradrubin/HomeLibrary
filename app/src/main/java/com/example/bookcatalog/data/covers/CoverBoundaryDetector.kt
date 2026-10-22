package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A detected cover: rotate the photo clockwise by [rotationDegrees], then crop it to [crop]. */
internal data class DetectedCover(
    val crop: NormalizedCrop,
    val rotationDegrees: Float,
)

/**
 * Finds a book cover photographed against a plainer background.
 *
 * The photo is shrunk so blur and sensor noise average out, the background is grown inward from
 * the photo's border through gradual color changes, and the largest remaining region is accepted
 * when it is shaped like a cover. The tilt of its edges gives the rotation that straightens the
 * book, and the crop is the book's box in the straightened photo.
 */
internal object CoverBoundaryDetector {
    fun detect(bitmap: Bitmap): DetectedCover? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return detect(bitmap.width, bitmap.height) { x, y -> pixels[y * bitmap.width + x] }
    }

    internal fun detect(
        imageWidth: Int,
        imageHeight: Int,
        pixelAt: (x: Int, y: Int) -> Int,
    ): DetectedCover? {
        if (imageWidth < MIN_IMAGE_EDGE || imageHeight < MIN_IMAGE_EDGE) return null
        val image = medianFilter(downscale(imageWidth, imageHeight, pixelAt))
        val background = findBackground(image) ?: return null
        val found = largestForegroundRegion(image, openForeground(image, background)) ?: return null
        val placement = refineEdges(
            image,
            found,
            centerX = imageWidth / 2f / image.block,
            centerY = imageHeight / 2f / image.block,
        )

        val regionWidth = placement.right - placement.left
        val regionHeight = placement.bottom - placement.top
        if (regionWidth <= 0 || regionHeight <= 0) return null
        val aspectRatio = regionWidth / regionHeight
        // Measured against the straightened box, so tilted books still count as solid rectangles.
        val fill = found.pixelCount / (regionWidth * regionHeight)
        if (regionWidth < image.width * MIN_WIDTH_RATIO ||
            regionHeight < image.height * MIN_HEIGHT_RATIO ||
            aspectRatio !in MIN_COVER_ASPECT..MAX_COVER_ASPECT ||
            fill < MIN_FILL_RATIO
        ) {
            return null
        }
        // Size of the photo once rotated, matching the bounds Bitmap.createBitmap gives it.
        val cos = abs(cos(placement.tilt))
        val sin = abs(sin(placement.tilt))
        val rotatedWidth = imageWidth * cos + imageHeight * sin
        val rotatedHeight = imageWidth * sin + imageHeight * cos
        fun normalizedX(x: Float) = ((rotatedWidth / 2 + x * image.block) / rotatedWidth).coerceIn(0f, 1f)
        fun normalizedY(y: Float) = ((rotatedHeight / 2 + y * image.block) / rotatedHeight).coerceIn(0f, 1f)
        return DetectedCover(
            crop = NormalizedCrop(
                left = normalizedX(placement.left),
                top = normalizedY(placement.top),
                right = normalizedX(placement.right),
                bottom = normalizedY(placement.bottom),
            ),
            rotationDegrees = if (placement.tilt == 0f) 0f else -Math.toDegrees(placement.tilt.toDouble()).toFloat(),
        )
    }

    /** Averages blocks of pixels so the longest side is at most [WORKING_EDGE]. */
    private fun downscale(
        imageWidth: Int,
        imageHeight: Int,
        pixelAt: (x: Int, y: Int) -> Int,
    ): SmallImage {
        val block = max(1, ceil(max(imageWidth, imageHeight).toFloat() / WORKING_EDGE).toInt())
        val width = imageWidth / block
        val height = imageHeight / block
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                for (dy in 0 until block) {
                    for (dx in 0 until block) {
                        val color = pixelAt(x * block + dx, y * block + dy)
                        red += color ushr 16 and 0xff
                        green += color ushr 8 and 0xff
                        blue += color and 0xff
                    }
                }
                val count = block * block
                pixels[y * width + x] = (red / count shl 16) or (green / count shl 8) or (blue / count)
            }
        }
        return SmallImage(width, height, block, pixels)
    }

    /** Erases thin lines such as marble veins or wood grain while keeping straight edges sharp. */
    private fun medianFilter(image: SmallImage): SmallImage {
        val window = IntArray((MEDIAN_RADIUS * 2 + 1) * (MEDIAN_RADIUS * 2 + 1))
        val pixels = IntArray(image.pixels.size)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                var result = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    var count = 0
                    for (dy in -MEDIAN_RADIUS..MEDIAN_RADIUS) {
                        for (dx in -MEDIAN_RADIUS..MEDIAN_RADIUS) {
                            val nx = (x + dx).coerceIn(0, image.width - 1)
                            val ny = (y + dy).coerceIn(0, image.height - 1)
                            window[count++] = image.pixels[ny * image.width + nx] ushr shift and 0xff
                        }
                    }
                    window.sort(0, count)
                    result = result or (window[count / 2] shl shift)
                }
                pixels[y * image.width + x] = result
            }
        }
        return SmallImage(image.width, image.height, image.block, pixels)
    }

    /**
     * Marks background pixels by flooding inward from border pixels that match the border's
     * typical color, crossing only small color steps. Returns null when there is no background.
     */
    private fun findBackground(image: SmallImage): BooleanArray? {
        val borderIndices = buildList {
            for (x in 0 until image.width) {
                add(x)
                add((image.height - 1) * image.width + x)
            }
            for (y in 1 until image.height - 1) {
                add(y * image.width)
                add(y * image.width + image.width - 1)
            }
        }
        val borderColor = medianColor(borderIndices.map { image.pixels[it] })
        val background = BooleanArray(image.pixels.size)
        val queue = ArrayDeque<Int>()
        for (index in borderIndices) {
            if (!background[index] && contrast(image.pixels[index], borderColor) <= MAX_SEED_DIFFERENCE) {
                background[index] = true
                queue.addLast(index)
            }
        }
        if (queue.size < borderIndices.size * MIN_BACKGROUND_BORDER_RATIO) return null
        // Allow the background to drift only as far as its border pixels already vary.
        val borderSpread = queue.map { contrast(image.pixels[it], borderColor) }.sorted()[queue.size * 9 / 10]
        val maxDifference = (borderSpread * 2 + BACKGROUND_DIFFERENCE_MARGIN)
            .coerceIn(MIN_BACKGROUND_DIFFERENCE, MAX_BACKGROUND_DIFFERENCE)

        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val color = image.pixels[index]
            image.forEachNeighbor(index) { neighbor ->
                // Comparing across two pixels stops the flood creeping over soft, blurred edges.
                val previous = index - (neighbor - index)
                val spanColor = if (previous in background.indices && background[previous] &&
                    image.isNeighbor(index, previous)
                ) {
                    image.pixels[previous]
                } else {
                    color
                }
                if (!background[neighbor] &&
                    contrast(color, image.pixels[neighbor]) <= MAX_BACKGROUND_STEP &&
                    contrast(spanColor, image.pixels[neighbor]) <= MAX_BACKGROUND_STEP &&
                    contrast(borderColor, image.pixels[neighbor]) <= maxDifference
                ) {
                    background[neighbor] = true
                    queue.addLast(neighbor)
                }
            }
        }
        return background
    }

    /**
     * Removes foreground thinner than the opening, such as marble veins or wood grain touching
     * the book, by shrinking the foreground and growing what survives back to its original size.
     * Returns a mask where true means excluded from the foreground.
     */
    private fun openForeground(image: SmallImage, background: BooleanArray): BooleanArray {
        fun spread(mask: BooleanArray, value: Boolean): BooleanArray {
            val result = mask.copyOf()
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (mask[y * image.width + x] != value) continue
                    for (dy in -OPENING_RADIUS..OPENING_RADIUS) {
                        for (dx in -OPENING_RADIUS..OPENING_RADIUS) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until image.width && ny in 0 until image.height) {
                                result[ny * image.width + nx] = value
                            }
                        }
                    }
                }
            }
            return result
        }
        val eroded = spread(background, true)
        return spread(eroded, false)
    }

    private fun largestForegroundRegion(image: SmallImage, excluded: BooleanArray): Region? {
        val visited = excluded.copyOf()
        var best: Region? = null
        val queue = ArrayDeque<Int>()
        for (start in image.pixels.indices) {
            if (visited[start]) continue
            visited[start] = true
            queue.addLast(start)
            var left = image.width
            var top = image.height
            var right = 0
            var bottom = 0
            var count = 0
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % image.width
                val y = index / image.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
                count++
                image.forEachNeighbor(index) { neighbor ->
                    if (!visited[neighbor]) {
                        visited[neighbor] = true
                        queue.addLast(neighbor)
                    }
                }
            }
            if (best == null || count > best.pixelCount) {
                best = Region(left, top, right, bottom, count)
            }
        }
        return best
    }

    /**
     * Pulls each side of the region in to the outermost sharp color step, so soft cast shadows
     * beside the book are left out, and measures the book's tilt from how those steps drift.
     * Sides are measured square to the tilt from the photo's center and use the outermost tenth
     * of the per-line positions, which keeps the book fully enclosed when it is slightly warped
     * or the tilt is too small to correct.
     */
    private fun refineEdges(image: SmallImage, region: Region, centerX: Float, centerY: Float): Placement {
        fun at(x: Int, y: Int) = image.pixels[y * image.width + x]

        // On each line, walks from `outer` toward `inner` and returns where the first sharp step
        // lands, or nothing when the line has none. Steps span two pixels so blurred edges still
        // register, while gradual shadows stay well below the threshold.
        fun sidePositions(
            lines: IntRange,
            outer: Int,
            inner: Int,
            limit: Int,
            colorAt: (line: Int, position: Int) -> Int,
        ): List<EdgePoint> {
            val direction = if (inner >= outer) 1 else -1
            // Points sit on the boundary between pixels, so the far side lands one pixel later.
            val boundaryShift = if (direction > 0) 0 else 1
            return lines.mapNotNull { line ->
                generateSequence(outer) { it + direction }
                    .takeWhile { it != inner && it + 2 * direction in 0 until limit }
                    .firstOrNull { position ->
                        contrast(colorAt(line, position), colorAt(line, position + 2 * direction)) >= MIN_EDGE_STEP
                    }
                    ?.let { EdgePoint(along = line + 0.5f, across = (it + direction + boundaryShift).toFloat()) }
            }
        }

        // The median slope between well-separated points, so stray lines don't skew it.
        fun slope(points: List<EdgePoint>): Float? {
            val minGap = (points.last().along - points.first().along) / 4
            val slopes = buildList {
                for (first in points.indices) {
                    for (second in first + 1 until points.size) {
                        val gap = points[second].along - points[first].along
                        if (gap >= minGap) add((points[second].across - points[first].across) / gap)
                    }
                }
            }
            return slopes.sorted().getOrNull(slopes.size / 2)
        }

        val width = region.right - region.left
        val height = region.bottom - region.top
        val bandX = max(MIN_EDGE_BAND, (width * EDGE_BAND_RATIO).toInt())
        val bandY = max(MIN_EDGE_BAND, (height * EDGE_BAND_RATIO).toInt())
        val rows = (region.top + height / 10) until (region.bottom - height / 10)
        val columns = (region.left + width / 10) until (region.right - width / 10)
        val maxX = image.width - 1
        val maxY = image.height - 1

        val left = sidePositions(rows, (region.left - 2).coerceAtLeast(0), (region.left + bandX).coerceAtMost(maxX), image.width) { y, x -> at(x, y) }
            .takeIf { it.size >= rows.count() * MIN_EDGE_LINE_SHARE }
        val right = sidePositions(rows, (region.right + 1).coerceAtMost(maxX), (region.right - 1 - bandX).coerceAtLeast(0), image.width) { y, x -> at(x, y) }
            .takeIf { it.size >= rows.count() * MIN_EDGE_LINE_SHARE }
        val top = sidePositions(columns, (region.top - 2).coerceAtLeast(0), (region.top + bandY).coerceAtMost(maxY), image.height) { x, y -> at(x, y) }
            .takeIf { it.size >= columns.count() * MIN_EDGE_LINE_SHARE }
        val bottom = sidePositions(columns, (region.bottom + 1).coerceAtMost(maxY), (region.bottom - 1 - bandY).coerceAtLeast(0), image.height) { x, y -> at(x, y) }
            .takeIf { it.size >= columns.count() * MIN_EDGE_LINE_SHARE }

        // A clockwise tilt moves the left and right sides left going down, and the top and
        // bottom sides down going right.
        val sideTilts = listOfNotNull(
            left?.let(::slope)?.let { atan(-it) },
            right?.let(::slope)?.let { atan(-it) },
            top?.let(::slope)?.let { atan(it) },
            bottom?.let(::slope)?.let { atan(it) },
        ).sorted()
        val measuredTilt = when {
            sideTilts.isEmpty() -> 0f
            sideTilts.size % 2 == 1 -> sideTilts[sideTilts.size / 2]
            else -> (sideTilts[sideTilts.size / 2 - 1] + sideTilts[sideTilts.size / 2]) / 2
        }
        val tilt = if (abs(measuredTilt) in MIN_TILT..MAX_TILT) measuredTilt else 0f

        // Positions across and down the straightened photo, relative to its center.
        val cos = cos(tilt)
        val sin = sin(tilt)
        fun straightX(x: Float, y: Float) = (x - centerX) * cos + (y - centerY) * sin
        fun straightY(x: Float, y: Float) = -(x - centerX) * sin + (y - centerY) * cos

        fun refined(positions: List<Float>?, fallback: Float, outward: Boolean): Float {
            if (positions == null) return fallback
            val sorted = positions.sorted()
            val index = (sorted.size * EDGE_PERCENTILE).toInt()
            return if (outward) sorted[sorted.size - 1 - index] else sorted[index]
        }

        val corners = listOf(
            region.left to region.top,
            region.right to region.top,
            region.right to region.bottom,
            region.left to region.bottom,
        ).map { (x, y) -> x.toFloat() to y.toFloat() }
        return Placement(
            left = refined(
                left?.map { straightX(it.across, it.along) },
                corners.minOf { (x, y) -> straightX(x, y) },
                outward = false,
            ),
            top = refined(
                top?.map { straightY(it.along, it.across) },
                corners.minOf { (x, y) -> straightY(x, y) },
                outward = false,
            ),
            right = refined(
                right?.map { straightX(it.across, it.along) },
                corners.maxOf { (x, y) -> straightX(x, y) },
                outward = true,
            ),
            bottom = refined(
                bottom?.map { straightY(it.along, it.across) },
                corners.maxOf { (x, y) -> straightY(x, y) },
                outward = true,
            ),
            tilt = tilt,
        )
    }

    private fun medianColor(colors: List<Int>): Int {
        fun channelMedian(shift: Int) = colors.map { it ushr shift and 0xff }.sorted()[colors.size / 2]
        return (channelMedian(16) shl 16) or (channelMedian(8) shl 8) or channelMedian(0)
    }

    private fun contrast(first: Int, second: Int): Int = maxOf(
        abs((first ushr 16 and 0xff) - (second ushr 16 and 0xff)),
        abs((first ushr 8 and 0xff) - (second ushr 8 and 0xff)),
        abs((first and 0xff) - (second and 0xff)),
    )

    private class SmallImage(val width: Int, val height: Int, val block: Int, val pixels: IntArray) {
        fun isNeighbor(first: Int, second: Int): Boolean =
            abs(first % width - second % width) + abs(first / width - second / width) == 1

        inline fun forEachNeighbor(index: Int, action: (Int) -> Unit) {
            val x = index % width
            val y = index / width
            if (x > 0) action(index - 1)
            if (x < width - 1) action(index + 1)
            if (y > 0) action(index - width)
            if (y < height - 1) action(index + width)
        }
    }

    private data class Region(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val pixelCount: Int,
    )

    /** Where a side's sharp step was found on one line, in shrunken-photo pixels. */
    private class EdgePoint(val along: Float, val across: Float)

    /**
     * The book's sides in the straightened photo, in shrunken-photo pixels from its center, and
     * its clockwise tilt in radians.
     */
    private class Placement(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val tilt: Float,
    )

    private const val MIN_IMAGE_EDGE = 80
    private const val WORKING_EDGE = 256
    private const val OPENING_RADIUS = 2
    private const val MEDIAN_RADIUS = 2
    private const val MIN_EDGE_STEP = 30
    private const val EDGE_BAND_RATIO = 0.25f
    private const val MIN_EDGE_BAND = 3
    private const val MIN_EDGE_LINE_SHARE = 0.3f
    private const val EDGE_PERCENTILE = 0.1f
    private const val MAX_SEED_DIFFERENCE = 40
    private const val MAX_BACKGROUND_STEP = 14
    private const val BACKGROUND_DIFFERENCE_MARGIN = 12
    private const val MIN_BACKGROUND_DIFFERENCE = 24
    private const val MAX_BACKGROUND_DIFFERENCE = 90
    private const val MIN_BACKGROUND_BORDER_RATIO = 0.5f
    private const val MIN_WIDTH_RATIO = 0.2f
    private const val MIN_HEIGHT_RATIO = 0.3f
    private const val MIN_COVER_ASPECT = 0.4f
    private const val MAX_COVER_ASPECT = 0.95f
    private const val MIN_FILL_RATIO = 0.8f

    // Tilts below about half a degree aren't visible; beyond 15 degrees the edge search is unreliable.
    private const val MIN_TILT = 0.008f
    private const val MAX_TILT = 0.26f
}
