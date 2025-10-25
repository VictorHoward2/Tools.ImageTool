package com.example.imagetool.ui.crop

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import kotlin.math.abs

private const val CORNER_THRESHOLD_DP = 500f

private enum class DragMode {
    NONE, MOVE, SCALE_TL, SCALE_TR, SCALE_BL, SCALE_BR
}

data class AspectRatio(val name: String, val value: Float?)

private val aspectRatios = listOf(
    AspectRatio("Free", null),
    AspectRatio("1:1", 1f / 1f),
    AspectRatio("2:3", 2f / 3f),
    AspectRatio("3:4", 3f / 4f),
    AspectRatio("4:5", 4f / 5f),
    AspectRatio("5:6", 5f / 6f),
    AspectRatio("3:5", 3f / 5f),
    AspectRatio("5:7", 5f / 7f),
    AspectRatio("Original", -1f), // Special value for original image ratio
    AspectRatio("Full", -2f)      // Special value for screen ratio
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(viewModel: CropViewModel, onBack: () -> Unit) {
    val sourceBitmap by viewModel.bitmapToCrop.collectAsState()
    var cropRect by remember { mutableStateOf<ComposeRect?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var activeRatio by remember { mutableStateOf(aspectRatios.first()) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceBitmap, viewSize, activeRatio) {
        val bmp = sourceBitmap
        if (bmp != null && viewSize != IntSize.Zero) {
            // Calculate the scaling factor and offset to fit the bitmap in the view
            scale = minOf(viewSize.width.toFloat() / bmp.width, viewSize.height.toFloat() / bmp.height)
            offset = Offset((viewSize.width - bmp.width * scale) / 2f, (viewSize.height - bmp.height * scale) / 2f)

            val originalRatio = bmp.width.toFloat() / bmp.height.toFloat()
            val screenRatio = viewSize.width.toFloat() / viewSize.height.toFloat()

            val currentRatio = when (activeRatio.value) {
                -1f -> originalRatio
                -2f -> screenRatio
                else -> activeRatio.value
            }

            // Always reset the crop rect for a new ratio to the largest possible size
            var newWidth = bmp.width.toFloat()
            var newHeight = bmp.height.toFloat()

            if (currentRatio != null) {
                if (newWidth / newHeight > currentRatio) { // Image is wider than ratio, height is the limit
                    newWidth = newHeight * currentRatio
                } else { // Image is taller than ratio, width is the limit
                    newHeight = newWidth / currentRatio
                }
            }

            val center = Offset(bmp.width / 2f, bmp.height / 2f)
            cropRect = ComposeRect(
                left = center.x - newWidth / 2f,
                top = center.y - newHeight / 2f,
                right = center.x + newWidth / 2f,
                bottom = center.y + newHeight / 2f
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cắt ảnh") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { Button(onClick = { if (sourceBitmap != null && cropRect != null) { val r = cropRect!!; viewModel.cropAndSave("cropped_${System.currentTimeMillis()}.png", Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())); onBack() } }, enabled = cropRect != null) { Text("Lưu") } }) },
        bottomBar = { AspectRatioSelection(activeRatio) { newRatio -> activeRatio = newRatio } }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewSize = it }) {
                val bmp = sourceBitmap
                if (bmp != null && cropRect != null) {
                    val originalBmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
                    val screenRatio = viewSize.width.toFloat() / viewSize.height.toFloat()
                    val ratioValue = when (activeRatio.value) {
                        -1f -> originalBmpRatio
                        -2f -> screenRatio
                        else -> activeRatio.value
                    }
                    androidx.compose.foundation.Image(bmp.asImageBitmap(), "Image to crop", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    CropOverlay(IntSize(bmp.width, bmp.height), scale, offset, cropRect!!, ratioValue) { transform -> cropRect = transform(cropRect!!) }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioSelection(activeRatio: AspectRatio, onRatioChange: (AspectRatio) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        LazyRow(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(aspectRatios) { ratio ->
                Button(onClick = { onRatioChange(ratio) }, colors = if (ratio.name == activeRatio.name) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()) {
                    Text(ratio.name)
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(bitmapSize: IntSize, scale: Float, offset: Offset, cropRect: ComposeRect, aspectRatio: Float?, onCropRectChange: ((ComposeRect) -> ComposeRect) -> Unit) {
    val cornerThresholdPx = with(LocalDensity.current) { CORNER_THRESHOLD_DP.dp.toPx() }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).pointerInput(Unit) { detectDragGestures(
        onDragStart = { startOffset -> val touchInBitmap = (startOffset - offset) / scale; dragMode = getDragMode(touchInBitmap, cropRect, cornerThresholdPx) },
        onDragEnd = { dragMode = DragMode.NONE },
        onDrag = { _, dragAmount ->
            if (dragMode == DragMode.NONE) return@detectDragGestures
            val dragInBitmap = dragAmount / scale
            onCropRectChange { currentRect ->
                val newRect = when (dragMode) {
                    DragMode.MOVE -> {
                        val translated = currentRect.translate(dragInBitmap.x, dragInBitmap.y)
                        val dx = when {
                            translated.left < 0 -> -translated.left
                            translated.right > bitmapSize.width -> bitmapSize.width - translated.right
                            else -> 0f
                        }
                        val dy = when {
                            translated.top < 0 -> -translated.top
                            translated.bottom > bitmapSize.height -> bitmapSize.height - translated.bottom
                            else -> 0f
                        }
                        translated.translate(dx, dy)
                    }
                    else -> { // Handle all SCALE modes
                        if (aspectRatio == null) { // Free scaling
                            when (dragMode) {
                                DragMode.SCALE_TL -> currentRect.copy(left = currentRect.left + dragInBitmap.x, top = currentRect.top + dragInBitmap.y)
                                DragMode.SCALE_TR -> currentRect.copy(right = currentRect.right + dragInBitmap.x, top = currentRect.top + dragInBitmap.y)
                                DragMode.SCALE_BL -> currentRect.copy(left = currentRect.left + dragInBitmap.x, bottom = currentRect.bottom + dragInBitmap.y)
                                DragMode.SCALE_BR -> currentRect.copy(right = currentRect.right + dragInBitmap.x, bottom = currentRect.bottom + dragInBitmap.y)
                                else -> currentRect
                            }
                        } else { // Aspect-ratio locked scaling
                            when (dragMode) {
                                DragMode.SCALE_BR -> {
                                    val fixed = currentRect.topLeft
                                    val newWidth = (currentRect.right + dragInBitmap.x) - fixed.x
                                    ComposeRect(fixed.x, fixed.y, fixed.x + newWidth, fixed.y + newWidth / aspectRatio)
                                }
                                DragMode.SCALE_TL -> {
                                    val fixed = currentRect.bottomRight
                                    val newWidth = fixed.x - (currentRect.left + dragInBitmap.x)
                                    ComposeRect(fixed.x - newWidth, fixed.y - newWidth / aspectRatio, fixed.x, fixed.y)
                                }
                                DragMode.SCALE_TR -> {
                                    val fixed = currentRect.bottomLeft
                                    val newWidth = (currentRect.right + dragInBitmap.x) - fixed.x
                                    ComposeRect(fixed.x, fixed.y - newWidth / aspectRatio, fixed.x + newWidth, fixed.y)
                                }
                                DragMode.SCALE_BL -> {
                                    val fixed = currentRect.topRight
                                    val newWidth = fixed.x - (currentRect.left + dragInBitmap.x)
                                    ComposeRect(fixed.x - newWidth, fixed.y, fixed.x, fixed.y + newWidth / aspectRatio)
                                }
                                else -> currentRect
                            }
                        }
                    }
                }
                val boundedRect = newRect.copy(left = newRect.left.coerceIn(0f, bitmapSize.width.toFloat()), top = newRect.top.coerceIn(0f, bitmapSize.height.toFloat()), right = newRect.right.coerceIn(0f, bitmapSize.width.toFloat()), bottom = newRect.bottom.coerceIn(0f, bitmapSize.height.toFloat()))
                ComposeRect(minOf(boundedRect.left, boundedRect.right), minOf(boundedRect.top, boundedRect.bottom), maxOf(boundedRect.left, boundedRect.right), maxOf(boundedRect.top, boundedRect.bottom))
            }
        }
    ) }) { val s = cropRect.left * scale + offset.x; val t = cropRect.top * scale + offset.y; val r = cropRect.right * scale + offset.x; val b = cropRect.bottom * scale + offset.y; val rect = ComposeRect(s,t,r,b); drawRect(Color(0x99000000)); drawRect(Color.Transparent, rect.topLeft, rect.size, blendMode = androidx.compose.ui.graphics.BlendMode.Clear); drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(1.dp.toPx())); val w = rect.width/3; val h = rect.height/3; val gridColor = Color.White.copy(0.7f); drawLine(gridColor, Offset(s+w,t), Offset(s+w,b), 1.dp.toPx()); drawLine(gridColor, Offset(s+2*w,t), Offset(s+2*w,b), 1.dp.toPx()); drawLine(gridColor, Offset(s,t+h), Offset(r,t+h), 1.dp.toPx()); drawLine(gridColor, Offset(s,t+2*h), Offset(r,t+2*h), 1.dp.toPx()) }
}

private fun getDragMode(touch: Offset, rect: ComposeRect, threshold: Float): DragMode {
    val scaledThresholdSq = threshold * threshold
    return when {
        (touch - rect.topLeft).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_TL
        (touch - rect.topRight).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_TR
        (touch - rect.bottomLeft).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_BL
        (touch - rect.bottomRight).getDistanceSquared() < scaledThresholdSq -> DragMode.SCALE_BR
        rect.contains(touch) -> DragMode.MOVE
        else -> DragMode.NONE
    }
}
