package com.example.imagetool.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val images by viewModel.selectedImages.collectAsState()
    val preview by viewModel.previewBitmap.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val stitchMode by viewModel.stitchMode.collectAsState()

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                viewModel.addUris(uris)
            }
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Image Tool - Stitch & Crop") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(arrayOf("image/*")) }) {
                Text("+")
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                        Text("Chọn ảnh")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { viewModel.clearAll() }) {
                        Text("Xóa tất cả")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stitch options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chế độ ghép:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    // Horizontal button
                    val isHorizontal = stitchMode == StitchMode.HORIZONTAL
                    OutlinedButton(
                        onClick = { viewModel.setStitchMode(StitchMode.HORIZONTAL) },
                        colors = if (isHorizontal) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Ngang")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Vertical button
                    val isVertical = stitchMode == StitchMode.VERTICAL
                    OutlinedButton(
                        onClick = { viewModel.setStitchMode(StitchMode.VERTICAL) },
                        colors = if (isVertical) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Dọc")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.stitchAndSave("stitched_${System.currentTimeMillis()}.png") },
                        enabled = images.isNotEmpty()
                    ) {
                        Text("Ghép & Lưu")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (images.isEmpty()) {
                    Text("Chưa chọn ảnh nào", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Đã chọn ${images.size} ảnh:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        items(items = images, key = { it.uri.toString() }) { item ->
                            ImageRow(item)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Đang xử lý... vui lòng chờ", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                preview?.let { bmp ->
                    Text("Preview:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 600.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageRow(item: com.example.imagetool.data.model.ImageItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // placeholder thumbnail (we didn't include Coil). Show URI last path segment
        Text(
            text = item.uri.lastPathSegment ?: item.uri.toString(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${item.width}x${item.height}", style = MaterialTheme.typography.bodySmall)
    }
}
