package com.example.imagetool.ui.crop

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagetool.data.repository.ImageRepository
import com.example.imagetool.utils.BitmapUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CropViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ImageRepository(application.applicationContext)

    private val _bitmapToCrop = MutableStateFlow<Bitmap?>(null)
    val bitmapToCrop: StateFlow<Bitmap?> = _bitmapToCrop

    fun loadBitmapForCrop(uri: Uri) {
        viewModelScope.launch {
            _bitmapToCrop.value = repo.loadBitmapFull(uri)
        }
    }

    fun cropAndSave(fileName: String, cropRect: Rect) {
        viewModelScope.launch {
            val source = _bitmapToCrop.value ?: return@launch
            val cropped = BitmapUtils.cropBitmap(source, cropRect)

            if (cropped != null) {
                repo.saveBitmapToMediaStore(cropped, fileName)
            }
        }
    }
}
