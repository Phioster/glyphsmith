package org.phioster.glyphsmith.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Decodes a picked image down to a workable size and fixes its orientation.
 *
 * Everything downstream (grid size, cell size, preview and export alike) is measured
 * against this capped bitmap, so preview and export always describe the same grid — only
 * the glyph size differs.
 */
object ImageLoader {

    /** Longest source edge kept after decoding. Enough detail for a fine grid, cheap to hold. */
    const val MAX_SOURCE_SIDE = 2048

    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SOURCE_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scaled = scaleDown(decoded, maxSide)
        val rotation = context.contentResolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
        return rotate(scaled, rotation)
    }

    fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxSide * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun readRotation(stream: java.io.InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Pixels as ARGB ints — the engine's input. */
    fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }
}
