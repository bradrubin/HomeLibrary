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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import com.example.bookcatalog.data.covers.NormalizedCrop
import kotlin.math.max
import kotlin.math.min

private data class CropSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized() = NormalizedCrop(left, top, right, bottom)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CropPhotoScreen(
    bitmap: Bitmap,
    initialCrop: NormalizedCrop?,
    onUseCrop: (NormalizedCrop) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val handleColor = MaterialTheme.colorScheme.primary
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
                if (initialCrop == null) {
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
                            var imageLeft = 0f
                            var imageTop = 0f
                            var imageWidth = 0f
                            var imageHeight = 0f
                            var dragStartX = 0f
                            var dragStartY = 0f
                            var initial = selection
                            var dragMode = DragMode.NEW
                            var corner = -1

                            fun normalizedPoint(point: Offset): Offset = Offset(
                                ((point.x - imageLeft) / imageWidth).coerceIn(0f, 1f),
                                ((point.y - imageTop) / imageHeight).coerceIn(0f, 1f),
                            )

                            detectDragGestures(
                                onDragStart = { point ->
                                    val scale = min(
                                        size.width / bitmap.width.toFloat(),
                                        size.height / bitmap.height.toFloat(),
                                    )
                                    imageWidth = bitmap.width * scale
                                    imageHeight = bitmap.height * scale
                                    imageLeft = (size.width - imageWidth) / 2f
                                    imageTop = (size.height - imageHeight) / 2f
                                    val normalized = normalizedPoint(point)
                                    dragStartX = normalized.x
                                    dragStartY = normalized.y
                                    initial = selection
                                    val cornerCandidates = listOf(
                                        Offset(selection.left, selection.top),
                                        Offset(selection.right, selection.top),
                                        Offset(selection.right, selection.bottom),
                                        Offset(selection.left, selection.bottom),
                                    )
                                    corner = cornerCandidates.indexOfFirst {
                                        val dx = (it.x - normalized.x) * imageWidth
                                        val dy = (it.y - normalized.y) * imageHeight
                                        dx * dx + dy * dy <= HANDLE_RADIUS_PX * HANDLE_RADIUS_PX
                                    }
                                    dragMode = when {
                                        corner >= 0 -> DragMode.RESIZE
                                        normalized.x in selection.left..selection.right &&
                                            normalized.y in selection.top..selection.bottom ->
                                            DragMode.MOVE
                                        else -> DragMode.NEW
                                    }
                                    if (dragMode == DragMode.NEW) {
                                        selection = CropSelection(
                                            normalized.x,
                                            normalized.y,
                                            normalized.x,
                                            normalized.y,
                                        )
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val normalized = normalizedPoint(change.position)
                                    val minSide = MIN_CROP_SIDE
                                    selection = when (dragMode) {
                                        DragMode.NEW -> CropSelection(
                                            left = min(dragStartX, normalized.x)
                                                .coerceIn(0f, 1f - minSide),
                                            top = min(dragStartY, normalized.y)
                                                .coerceIn(0f, 1f - minSide),
                                            right = max(dragStartX, normalized.x)
                                                .coerceIn(minSide, 1f),
                                            bottom = max(dragStartY, normalized.y)
                                                .coerceIn(minSide, 1f),
                                        )
                                        DragMode.MOVE -> {
                                            val width = initial.right - initial.left
                                            val height = initial.bottom - initial.top
                                            val left = (initial.left + normalized.x - dragStartX)
                                                .coerceIn(0f, 1f - width)
                                            val top = (initial.top + normalized.y - dragStartY)
                                                .coerceIn(0f, 1f - height)
                                            CropSelection(left, top, left + width, top + height)
                                        }
                                        DragMode.RESIZE -> resizeSelection(
                                            initial,
                                            corner,
                                            normalized.x,
                                            normalized.y,
                                        )
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
                    listOf(
                        Offset(left, top),
                        Offset(right, top),
                        Offset(right, bottom),
                        Offset(left, bottom),
                    ).forEach { handle ->
                        drawCircle(Color.White, radius = HANDLE_RADIUS_PX, center = handle)
                        drawCircle(
                            color = handleColor,
                            radius = HANDLE_RADIUS_PX - 4.dp.toPx(),
                            center = handle,
                        )
                    }
                }
            }
        }
    }
}

private fun resizeSelection(
    initial: CropSelection,
    corner: Int,
    x: Float,
    y: Float,
): CropSelection {
    val minSide = MIN_CROP_SIDE
    return when (corner) {
        0 -> initial.copy(
            left = x.coerceIn(0f, initial.right - minSide),
            top = y.coerceIn(0f, initial.bottom - minSide),
        )
        1 -> initial.copy(
            right = x.coerceIn(initial.left + minSide, 1f),
            top = y.coerceIn(0f, initial.bottom - minSide),
        )
        2 -> initial.copy(
            right = x.coerceIn(initial.left + minSide, 1f),
            bottom = y.coerceIn(initial.top + minSide, 1f),
        )
        3 -> initial.copy(
            left = x.coerceIn(0f, initial.right - minSide),
            bottom = y.coerceIn(initial.top + minSide, 1f),
        )
        else -> initial
    }
}

private enum class DragMode {
    NEW,
    MOVE,
    RESIZE,
}

private const val MIN_CROP_SIDE = 0.05f
private const val HANDLE_RADIUS_PX = 28f
