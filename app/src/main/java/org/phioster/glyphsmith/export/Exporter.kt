package org.phioster.glyphsmith.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saving and sharing. Two distinct outputs, both of which matter:
 * the rendered PNG, and the *true character grid* as .txt — the grid is the thing you can
 * paste into a terminal, a README or a text editor, and it can't be recovered from a PNG.
 */
/** Image formats offered next to the export button, mirroring the original's format picker. */
enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
    ;

    /** JPEG has no alpha, so a transparent background silently turns black without a warning. */
    val supportsTransparency: Boolean get() = this != JPG
}

object Exporter {

    private const val ALBUM = "Glyphsmith"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val QUALITY = 95

    fun timestampedName(extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "glyphsmith-$stamp.$extension"
    }

    private fun compressFormat(format: ImageFormat): Bitmap.CompressFormat = when (format) {
        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
        ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
        ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    /** Writes the image into the gallery. Returns null when the write failed. */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        format: ImageFormat = ImageFormat.PNG,
        name: String = timestampedName(format.extension),
    ): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageViaMediaStore(context, bitmap, format, name)
        } else {
            saveImageLegacy(context, bitmap, format, name)
        }

    private fun saveImageViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        format: ImageFormat,
        name: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(compressFormat(format), QUALITY, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun saveImageLegacy(
        context: Context,
        bitmap: Bitmap,
        format: ImageFormat,
        name: String,
    ): Uri? = runCatching {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        dir.mkdirs()
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(compressFormat(format), QUALITY, it) }
        shareUriFor(context, file)
    }.getOrNull()

    /** Writes the character grid as a .txt into Downloads. Returns null when the write failed. */
    fun saveText(context: Context, text: String, name: String = timestampedName("txt")): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ALBUM")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.also { target ->
                runCatching {
                    resolver.openOutputStream(target)?.use { it.write(text.toByteArray()) }
                }.onFailure { resolver.delete(target, null, null) }
            }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ALBUM)
                dir.mkdirs()
                val file = File(dir, name)
                file.writeText(text)
                shareUriFor(context, file)
            }.getOrNull()
        }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("ASCII", text))
    }

    fun shareImage(context: Context, bitmap: Bitmap, format: ImageFormat = ImageFormat.PNG) {
        val file = File(cacheDir(context), timestampedName(format.extension))
        file.outputStream().use { bitmap.compress(compressFormat(format), QUALITY, it) }
        startShare(context, shareUriFor(context, file), format.mimeType)
    }

    fun shareText(context: Context, text: String) {
        val file = File(cacheDir(context), timestampedName("txt"))
        file.writeText(text)
        startShare(context, shareUriFor(context, file), "text/plain")
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "share").apply { mkdirs() }

    private fun shareUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

    private fun startShare(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
