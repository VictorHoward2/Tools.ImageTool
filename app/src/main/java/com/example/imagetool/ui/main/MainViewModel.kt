package com.example.imagetool.ui.main

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagetool.data.model.ImageItem
import com.example.imagetool.data.repository.ImageRepository
import com.example.imagetool.utils.BitmapUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class StitchMode {
    VERTICAL,
    HORIZONTAL
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ImageRepository(application.applicationContext)

    private val _selectedImages = MutableStateFlow<List<ImageItem>>(emptyList())
    val selectedImages: StateFlow<List<ImageItem>> = _selectedImages

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _stitchMode = MutableStateFlow(StitchMode.VERTICAL)
    val stitchMode: StateFlow<StitchMode> = _stitchMode

    private val MAX_TOTAL_PIXELS = 120_000_000 // safeguard: 120 million pixels ~ (e.g., 12k x 10k)

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch {
            // For each URI, get size & orientation (non-blocking)
            val items = uris.map { uri ->
                val size = repo.getImageSize(uri)
                val orientation = repo.getExifOrientation(uri)
                val thumbnail = repo.loadThumbnail(uri, 128, 128)
                ImageItem(uri, size.first, size.second, orientation, thumbnail)
            }
            _selectedImages.value = _selectedImages.value + items
        }
    }

    fun setStitchMode(mode: StitchMode) {
        _stitchMode.value = mode
    }

    fun removeItem(item: ImageItem) {
        _selectedImages.value = _selectedImages.value - item
    }

    fun clearAll() {
        _selectedImages.value = emptyList()
        _previewBitmap.value = null
    }

    /**
     * Create an in-memory stitched bitmap and keep a downsampled preview.
     * Returns saved Uri if successful (save to MediaStore) or null.
     */
    fun stitchAndSave(fileName: String = "") {
        viewModelScope.launch {
            val items = _selectedImages.value
            if (items.isEmpty()) return@launch

            _isProcessing.value = true

            val totalPixels = items.sumOf { it.width.toLong() * it.height.toLong() }
            if (totalPixels > MAX_TOTAL_PIXELS) {
                // too big to handle in-memory safely
                _isProcessing.value = false
                // set preview null
                _previewBitmap.value = null
                // Could send error state - for simplicity we just stop
                return@launch
            }

            // Load bitmaps full-resolution and apply orientation
            val bitmaps = mutableListOf<Bitmap>()
            withContext(Dispatchers.IO) {
                for (it in items) {
                    val bmp = repo.loadBitmapFull(it.uri)
                    if (bmp != null) {
                        val oriented = BitmapUtils.applyExifOrientation(bmp, it.orientation)
                        // if a new bitmap created and different ref, optionally recycle original bmp
                        if (oriented !== bmp) {
                            try { bmp.recycle() } catch (_: Exception) {}
                        }
                        bitmaps.add(oriented)
                    }
                }
            }

            if (bitmaps.isEmpty()) {
                _isProcessing.value = false
                return@launch
            }

            // Merge
            val merged = when (_stitchMode.value) {
                StitchMode.VERTICAL -> BitmapUtils.mergeVertically(bitmaps)
                StitchMode.HORIZONTAL -> BitmapUtils.mergeHorizontally(bitmaps)
            }
            
            // create preview (scaled) for UI
            merged?.let {
                val preview = BitmapUtils.createPreviewBitmap(it, 1200, 1600)
                _previewBitmap.value = preview
            }

            // Save merged bitmap to MediaStore
            val savedUri = merged?.let { repo.saveBitmapToMediaStore(it, fileName) }

            // recycle large bitmaps to free memory
            try {
                for (b in bitmaps) {
                    if (!b.isRecycled) b.recycle()
                }
                if (merged != null && !merged.isRecycled) {
                    // keep merged if you want; but we freed source bitmaps
                }
            } catch (e: Exception) { e.printStackTrace() }

            _isProcessing.value = false
            // Could expose savedUri via StateFlow / event; for simplicity user checks MediaStore
        }
    }
}
