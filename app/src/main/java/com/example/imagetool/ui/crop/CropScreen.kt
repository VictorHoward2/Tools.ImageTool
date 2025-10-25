package com.example.imagetool.ui.crop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.CompositingStrategy

private const val CORNER_THRESHOLD_DP = 500f

private enum class DragMode {
    NONE,
    MOVE,
    SCALE_TL,
    SCALE_TR,
    SCALE_BL,
    SCALE_BR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(viewModel: CropViewModel, onBack: () -> Unit) {
    val sourceBitmap by viewModel.bitmapToCrop.collectAsState()
    var cropRect by remember { mutableStateOf<ComposeRect?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceBitmap, viewSize) {
        val bmp = sourceBitmap
        if (bmp != null && viewSize != IntSize.Zero) {
            val canvasWidth = viewSize.width
            val canvasHeight = viewSize.height
            scale = minOf(canvasWidth.toFloat() / bmp.width, canvasHeight.toFloat() / bmp.height)
            offset = Offset((canvasWidth - bmp.width * scale) / 2f, (canvasHeight - bmp.height * scale) / 2f)

            if (cropRect == null) { // Initialize only once
                val w = bmp.width
                val h = bmp.height
                val marginX = w * 0.1f
                val marginY = h * 0.1f
                cropRect = ComposeRect(marginX, marginY, w - marginX, h - marginY)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cắt ảnh") },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                } },
                actions = {
                    Button(onClick = {
                        if (sourceBitmap != null && cropRect != null) {
                            val finalRect = Rect(
                                cropRect!!.left.roundToInt(),
                                cropRect!!.top.roundToInt(),
                                cropRect!!.right.roundToInt(),
                                cropRect!!.bottom.roundToInt()
                            )
                            viewModel.cropAndSave("cropped_${System.currentTimeMillis()}.png", finalRect)
                            onBack()
                        }
                    }, enabled = cropRect != null) {
                        Text("Lưu")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { viewSize = it }
        ) {
            val bmp = sourceBitmap
            if (bmp != null && cropRect != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Image to crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                CropOverlay(
                    bitmapSize = IntSize(bmp.width, bmp.height),
                    scale = scale,
                    offset = offset,
                    cropRect = cropRect!!,
                    onCropRectChange = { transform -> cropRect = transform(cropRect!!) }
                )
            }
        }
    }
}

@Composable
private fun CropOverlay(
    bitmapSize: IntSize,
    scale: Float,
    offset: Offset,
    cropRect: ComposeRect,
    onCropRectChange: ((ComposeRect) -> ComposeRect) -> Unit
) {
    val cornerThresholdPx = with(LocalDensity.current) { CORNER_THRESHOLD_DP.dp.toPx() }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    val touchInBitmap = (startOffset - offset) / scale
                    dragMode = getDragMode(touchInBitmap, cropRect, cornerThresholdPx)
                },
                onDragEnd = { dragMode = DragMode.NONE },
                onDrag = { _, dragAmount ->
                    if (dragMode == DragMode.NONE) return@detectDragGestures

                    val dragInBitmap = dragAmount / scale

                    onCropRectChange { currentRect ->
                        when (dragMode) {
                            DragMode.MOVE -> {
                                val translatedRect = currentRect.translate(dragInBitmap.x, dragInBitmap.y)
                                var dx = 0f
                                var dy = 0f

                                if (translatedRect.left < 0) {
                                    dx = -translatedRect.left
                                } else if (translatedRect.right > bitmapSize.width) {
                                    dx = bitmapSize.width - translatedRect.right
                                }

                                if (translatedRect.top < 0) {
                                    dy = -translatedRect.top
                                } else if (translatedRect.bottom > bitmapSize.height) {
                                    dy = bitmapSize.height - translatedRect.bottom
                                }
                                translatedRect.translate(dx, dy)
                            }
                            else -> { // Handle all SCALE modes
                                val newRect = when (dragMode) {
                                    DragMode.SCALE_TL -> currentRect.copy(left = currentRect.left + dragInBitmap.x, top = currentRect.top + dragInBitmap.y)
                                    DragMode.SCALE_TR -> currentRect.copy(right = currentRect.right + dragInBitmap.x, top = currentRect.top + dragInBitmap.y)
                                    DragMode.SCALE_BL -> currentRect.copy(left = currentRect.left + dragInBitmap.x, bottom = currentRect.bottom + dragInBitmap.y)
                                    DragMode.SCALE_BR -> currentRect.copy(right = currentRect.right + dragInBitmap.x, bottom = currentRect.bottom + dragInBitmap.y)
                                    else -> currentRect
                                }

                                val boundedRect = newRect.copy(
                                    left = newRect.left.coerceIn(0f, bitmapSize.width.toFloat()),
                                    top = newRect.top.coerceIn(0f, bitmapSize.height.toFloat()),
                                    right = newRect.right.coerceIn(0f, bitmapSize.width.toFloat()),
                                    bottom = newRect.bottom.coerceIn(0f, bitmapSize.height.toFloat())
                                )

                                ComposeRect(
                                    minOf(boundedRect.left, boundedRect.right),
                                    minOf(boundedRect.top, boundedRect.bottom),
                                    maxOf(boundedRect.left, boundedRect.right),
                                    maxOf(boundedRect.top, boundedRect.bottom)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) {
        val scaledCropRect = ComposeRect(
            left = cropRect.left * scale + offset.x,
            top = cropRect.top * scale + offset.y,
            right = cropRect.right * scale + offset.x,
            bottom = cropRect.bottom * scale + offset.y
        )

        // Draw semi-transparent overlay outside the crop rect
        drawRect(Color(0x99000000))
        drawRect(
            Color.Transparent,
            topLeft = scaledCropRect.topLeft,
            size = scaledCropRect.size,
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Draw the white border of the crop rect
        drawRect(
            Color.White,
            topLeft = scaledCropRect.topLeft,
            size = scaledCropRect.size,
            style = Stroke(width = 1.dp.toPx())
        )

        // Draw 3x3 grid inside the crop rect
        val thirdWidth = scaledCropRect.width / 3
        val thirdHeight = scaledCropRect.height / 3
        val gridColor = Color.White.copy(alpha = 0.7f)

        // Vertical lines
        drawLine(
            color = gridColor,
            start = Offset(scaledCropRect.left + thirdWidth, scaledCropRect.top),
            end = Offset(scaledCropRect.left + thirdWidth, scaledCropRect.bottom),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(scaledCropRect.left + 2 * thirdWidth, scaledCropRect.top),
            end = Offset(scaledCropRect.left + 2 * thirdWidth, scaledCropRect.bottom),
            strokeWidth = 1.dp.toPx()
        )

        // Horizontal lines
        drawLine(
            color = gridColor,
            start = Offset(scaledCropRect.left, scaledCropRect.top + thirdHeight),
            end = Offset(scaledCropRect.right, scaledCropRect.top + thirdHeight),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(scaledCropRect.left, scaledCropRect.top + 2 * thirdHeight),
            end = Offset(scaledCropRect.right, scaledCropRect.top + 2 * thirdHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun getDragMode(touchInBitmap: Offset, rect: ComposeRect, threshold: Float): DragMode {
    val scaledThresholdSq = threshold * threshold // Use a squared threshold for distance checking
    return when {
        (touchInBitmap - rect.topLeft).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_TL
        (touchInBitmap - rect.topRight).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_TR
        (touchInBitmap - rect.bottomLeft).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_BL
        (touchInBitmap - rect.bottomRight).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_BR
        rect.contains(touchInBitmap) -> DragMode.MOVE
        else -> DragMode.NONE
    }
}
