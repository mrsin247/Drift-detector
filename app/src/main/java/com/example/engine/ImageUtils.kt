package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageUtils {

    private const val MAX_DIMENSION = 1024

    /**
     * Loads and normalizes the default sample portrait from drawable resources.
     */
    fun loadSamplePortrait(context: Context): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sample_portrait, options)
        return scaleToMaxDimension(rawBitmap, MAX_DIMENSION)
    }

    /**
     * Loads a user-selected image from Uri, safely downscaled for optimal performance.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Read dimensions first
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream.close()

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Calculate sample size
            var sampleSize = 1
            val maxSide = max(origWidth, origHeight)
            while (maxSide / (sampleSize * 2) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            decoded?.let { scaleToMaxDimension(it, MAX_DIMENSION) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ensures image does not exceed max side dimension to prevent OOM and maintain 60fps mesh warping.
     */
    private fun scaleToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxSide = max(w, h)
        if (maxSide <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxSide
        val targetW = (w * scale).toInt()
        val targetH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    /**
     * Saves the drifted bitmap to the device's public Pictures directory via MediaStore.
     * Supports both JPEG and PNG format.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String,
        isJpeg: Boolean = true
    ): Pair<Uri?, String> {
        val ext = if (isJpeg) "jpg" else "png"
        val mime = if (isJpeg) "image/jpeg" else "image/png"
        val fileName = "LikenessDrift_${title}_${System.currentTimeMillis()}.$ext"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LikenessDriftLab")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Pair(null, fileName)

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (isJpeg) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Pair(uri, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
            Pair(null, fileName)
        }
    }

    /**
     * Prepares an image file in cache and returns a share intent for sharing to agents or other apps.
     */
    fun createShareIntent(
        context: Context,
        bitmap: Bitmap,
        genNumber: Int,
        likenessScore: Float,
        isJpeg: Boolean = true
    ): Intent? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val ext = if (isJpeg) "jpg" else "png"
            val file = File(cachePath, "drifted_frame_gen${genNumber}.$ext")
            FileOutputStream(file).use { out ->
                if (isJpeg) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mime = if (isJpeg) "image/jpeg" else "image/png"

            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Likeness Drift Lab Frame - Gen $genNumber (Likeness: $likenessScore%)\nSimulated for AI agent detection & likeness re-anchoring."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
