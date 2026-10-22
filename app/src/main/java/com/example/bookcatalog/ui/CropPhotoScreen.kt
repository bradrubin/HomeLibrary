package com.example.bookcatalog.ui

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import com.example.bookcatalog.data.covers.NormalizedCrop
import kotlin.math.abs
import kotlin.math.min

private data class CropSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized() = NormalizedCrop(left, top, right, bottom)

    /** The same area once the photo is turned a quarter turn clockwise. */
    fun rotatedClockwise() = NormalizedCrop(1f - bottom, left, 1f - top, right)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CropPhotoScreen(
    bitmap: Bitmap,
    initialCrop: NormalizedCrop?,
    onRotate: (NormalizedCrop) -> Unit,
    onUseCrop: (NormalizedCrop) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val handleColor = MaterialTheme.colorScheme.primary
    // Rotating hands back a crop, so remember whether the first one came from detection.
    val edgesFound = remember { initialCrop != null }
    var selection by remember(bitmap) {
        mutableStateOf(
            initialCrop?.let { CropSelection(it.left, it.top, it.right, it.bottom) }
                ?: CropSelection(0.05f, 0.05f, 0.95f, 0.95f),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop book cover") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel crop",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onRotate(selection.rotatedClockwise()) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate photo",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onUseCrop(selection.normalized()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use this cover")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (!edgesFound) {
                    "Couldn’t detect the cover edges confidently. Drag the corners to fit the cover."
                } else {
                    "We found the cover edges. Adjust the frame if needed."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            var imageWidth = 0f
                            var imageHeight = 0f
                            var dragStart = Offset.Zero
                            var initial = selection
                            var horizontalEdge = Edge.NONE
                            var verticalEdge = Edge.NONE
                            var moving = false

                            detectDragGestures(
                                onDragStart = { point ->
                                    val scale = min(
                                        size.width / bitmap.width.toFloat(),
                                        size.height / bitmap.height.toFloat(),
                                    )
                                    imageWidth = bitmap.width * scale
                                    imageHeight = bitmap.height * scale
                                    val imageLeft = (size.width - imageWidth) / 2f
                                    val imageTop = (size.height - imageHeight) / 2f
                                    dragStart = point
                                    initial = selection
                                    val touch = EDGE_TOUCH_RADIUS.toPx()
                                    val left = imageLeft + selection.left * imageWidth
                                    val top = imageTop + selection.top * imageHeight
                                    val right = imageLeft + selection.right * imageWidth
                                    val bottom = imageTop + selection.bottom * imageHeight
                                    val alongVertical = point.y in (top - touch)..(bottom + touch)
                                    val alongHorizontal = point.x in (left - touch)..(right + touch)
                                    // Picks the nearer side, so a narrow box can still be widened.
                                    horizontalEdge = when {
                                        !alongVertical -> Edge.NONE
                                        abs(point.x - left) <= min(touch, abs(point.x - right)) -> Edge.LEFT
                                        abs(point.x - right) <= touch -> Edge.RIGHT
                                        else -> Edge.NONE
                                    }
                                    verticalEdge = when {
                                        !alongHorizontal -> Edge.NONE
                                        abs(point.y - top) <= min(touch, abs(point.y - bottom)) -> Edge.TOP
                                        abs(point.y - bottom) <= touch -> Edge.BOTTOM
                                        else -> Edge.NONE
                                    }
                                    moving = horizontalEdge == Edge.NONE && verticalEdge == Edge.NONE &&
                                        point.x in left..right && point.y in top..bottom
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val dx = (change.position.x - dragStart.x) / imageWidth
                                    val dy = (change.position.y - dragStart.y) / imageHeight
                                    selection = if (moving) {
                                        val width = initial.right - initial.left
                                        val height = initial.bottom - initial.top
                                        val left = (initial.left + dx).coerceIn(0f, 1f - width)
                                        val top = (initial.top + dy).coerceIn(0f, 1f - height)
                                        CropSelection(left, top, left + width, top + height)
                                    } else {
                                        moveEdges(initial, horizontalEdge, verticalEdge, dx, dy)
                                    }
                                },
                            )
                        },
                ) {
                    drawRect(Color.Black)
                    val scale = min(
                        size.width / bitmap.width.toFloat(),
                        size.height / bitmap.height.toFloat(),
                    )
                    val imageWidth = bitmap.width * scale
                    val imageHeight = bitmap.height * scale
                    val imageLeft = (size.width - imageWidth) / 2f
                    val imageTop = (size.height - imageHeight) / 2f
                    val imageRight = imageLeft + imageWidth
                    val imageBottom = imageTop + imageHeight
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(imageLeft.toInt(), imageTop.toInt()),
                        dstSize = IntSize(imageWidth.toInt(), imageHeight.toInt()),
                    )
                    val left = imageLeft + selection.left * imageWidth
                    val top = imageTop + selection.top * imageHeight
                    val right = imageLeft + selection.right * imageWidth
                    val bottom = imageTop + selection.bottom * imageHeight
                    val shade = Color.Black.copy(alpha = 0.58f)
                    drawRect(shade, Offset(imageLeft, imageTop), androidx.compose.ui.geometry.Size(imageWidth, top - imageTop))
                    drawRect(shade, Offset(imageLeft, bottom), androidx.compose.ui.geometry.Size(imageWidth, imageBottom - bottom))
                    drawRect(shade, Offset(imageLeft, top), androidx.compose.ui.geometry.Size(left - imageLeft, bottom - top))
                    drawRect(shade, Offset(right, top), androidx.compose.ui.geometry.Size(imageRight - right, bottom - top))
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    // A bar across the middle of each side marks it as draggable.
                    val bar = EDGE_HANDLE_LENGTH.toPx() / 2
                    val centerX = (left + right) / 2
                    val centerY = (top + bottom) / 2
                    listOf(
                        Offset(centerX - bar, top) to Offset(centerX + bar, top),
                        Offset(centerX - bar, bottom) to Offset(centerX + bar, bottom),
                        Offset(left, centerY - bar) to Offset(left, centerY + bar),
                        Offset(right, centerY - bar) to Offset(right, centerY + bar),
                    ).forEach { (start, end) ->
                        drawLine(Color.White, start, end, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(handleColor, start, end, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun moveEdges(
    initial: CropSelection,
    horizontalEdge: Edge,
    verticalEdge: Edge,
    dx: Float,
    dy: Float,
): CropSelection {
    val minSide = MIN_CROP_SIDE
    var result = initial
    when (horizontalEdge) {
        Edge.LEFT -> result = result.copy(left = (initial.left + dx).coerceIn(0f, initial.right - minSide))
        Edge.RIGHT -> result = result.copy(right = (initial.right + dx).coerceIn(initial.left + minSide, 1f))
        else -> Unit
    }
    when (verticalEdge) {
        Edge.TOP -> result = result.copy(top = (initial.top + dy).coerceIn(0f, initial.bottom - minSide))
        Edge.BOTTOM -> result = result.copy(bottom = (initial.bottom + dy).coerceIn(initial.top + minSide, 1f))
        else -> Unit
    }
    return result
}

private enum class Edge {
    NONE,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
}

private const val MIN_CROP_SIDE = 0.05f
private val EDGE_TOUCH_RADIUS = 24.dp
private val EDGE_HANDLE_LENGTH = 32.dp
