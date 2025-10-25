package com.example.imagetool.data.model

import android.net.Uri

data class ImageItem(
    val uri: Uri,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 0
)
