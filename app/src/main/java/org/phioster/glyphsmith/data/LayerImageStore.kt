package org.phioster.glyphsmith.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import org.phioster.glyphsmith.pipeline.LayerImages

/**
 * Keeps the pictures layers use as files of the app's own, so a layer still has its picture
 * after a restart. Stored as PNG because the transparency is the point of most of them.
 */
class LayerImageStore(private val context: Context) {

    private val dir = File(context.filesDir, "layer-images")

    /** Copies the picture at [uri] in and returns the id a layer names it by, or null. */
    fun adopt(uri: Uri): String? {
        val bitmap = ImageLoader.load(context, uri, MAX_SIDE) ?: return null
        val id = "p" + java.lang.Long.toString(System.currentTimeMillis(), ID_RADIX)
        val saved = runCatching {
            dir.mkdirs()
            File(dir, "$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        }.getOrDefault(false)
        register(id, bitmap)
        bitmap.recycle()
        return if (saved) id else null
    }

    /** Loads every picture in [ids] that is on file and not yet held. */
    fun ensure(ids: Collection<String>) {
        ids.filter { it.isNotEmpty() && !LayerImages.has(it) }.forEach { id ->
            val file = File(dir, "$id.png")
            if (!file.exists()) return@forEach
            val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return@forEach
            register(id, bitmap)
            bitmap.recycle()
        }
    }

    private fun register(id: String, bitmap: Bitmap) {
        LayerImages.put(id, LayerImages.Picture(ImageLoader.pixelsOf(bitmap), bitmap.width, bitmap.height))
    }

    private companion object {
        /** Smaller than a main picture: a layer is usually one part of the frame. */
        const val MAX_SIDE = 1536
        const val PNG_QUALITY = 100
        const val ID_RADIX = 36
    }
}
