package com.example.imagetool.utils

import android.graphics.*
import androidx.exifinterface.media.ExifInterface

object BitmapUtils {

    /**
     * Rotate/flip a bitmap according to Exif orientation constant.
     * Returns new bitmap (may be same reference if no change).
     */
    fun applyExifOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                // optionally recycle original
            }
            rotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Merge bitmaps vertically (stack top-to-bottom).
     * NOTE: All bitmaps are used as-is (no scaling). Be careful with memory.
     */
    fun mergeVertically(bitmaps: List<Bitmap>): Bitmap? {
        if (bitmaps.isEmpty()) return null
        val width = bitmaps.maxOf { it.width }
        val height = bitmaps.sumOf { it.height }

        // Basic safeguard: if width/height invalid
        if (width <= 0 || height <= 0) return null

        return try {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var top = 0f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (bmp in bitmaps) {
                // draw at left=0, top=top
                canvas.drawBitmap(bmp, 0f, top, paint)
                top += bmp.height
            }
            result
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Merge bitmaps horizontally (stack left-to-right).
     * NOTE: All bitmaps are used as-is (no scaling). Be careful with memory.
     */
    fun mergeHorizontally(bitmaps: List<Bitmap>): Bitmap? {
        if (bitmaps.isEmpty()) return null
        val width = bitmaps.sumOf { it.width }
        val height = bitmaps.maxOf { it.height }

        // Basic safeguard: if width/height invalid
        if (width <= 0 || height <= 0) return null

        return try {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var left = 0f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (bmp in bitmaps) {
                // draw at left=left, top=0
                canvas.drawBitmap(bmp, left, 0f, paint)
                left += bmp.width
            }
            result
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Create a downsampled bitmap for preview to avoid OOM on Compose Image.
     * Keeps aspect ratio and fits into maxWidth x maxHeight.
     */
    fun createPreviewBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth.toFloat() / source.width, maxHeight.toFloat() / source.height)
        if (ratio >= 1f) return source
        val w = (source.width * ratio).toInt()
        val h = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
