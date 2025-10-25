package com.example.imagetool.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageRepository(private val context: Context) {

    /**
     * Load a full-resolution bitmap from the given URI.
     * WARNING: may OOM if image is extremely large.
     */
    suspend fun loadBitmapFull(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                    // inSampleSize = 1 -> full resolution
                }
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get basic info (width, height) without decoding full bitmap.
     */
    suspend fun getImageSize(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var width = 0
        var height = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                width = opts.outWidth
                height = opts.outHeight
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(width, height)
    }

    /**
     * Read EXIF orientation (returns ExifInterface.ORIENTATION_*)
     */
    suspend fun getExifOrientation(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            } ?: ExifInterface.ORIENTATION_UNDEFINED
        } catch (e: Exception) {
            e.printStackTrace()
            ExifInterface.ORIENTATION_UNDEFINED
        }
    }

    /**
     * Save a bitmap to MediaStore (PNG). Returns URI of saved image or null.
     */
    suspend fun saveBitmapToMediaStore(bitmap: Bitmap, displayName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val filename = displayName.ifBlank { "stitch_${System.currentTimeMillis()}.png" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values) ?: return@withContext null

            resolver.openOutputStream(itemUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            } ?: run {
                // failed to open stream
                resolver.delete(itemUri, null, null)
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            itemUri
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
