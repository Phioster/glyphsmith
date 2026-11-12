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

    init {
        LayerImages.frameLoader = { id, index ->
            runCatching { BitmapFactory.decodeFile(frameFile(id, index).path) }.getOrNull()?.let { bitmap ->
                LayerImages.Picture(ImageLoader.pixelsOf(bitmap), bitmap.width, bitmap.height).also { bitmap.recycle() }
            }
        }
    }

    private fun frameFile(id: String, index: Int) = File(dir, "$id.f$index.png")

    /** How many frames of the loop [id] are on file, counted from the first without a gap. */
    private fun framesOnFile(id: String): Int {
        var count = 0
        while (frameFile(id, count).exists()) count++
        return count
    }

    /**
     * Keeps [frames] as the loop of the picture [id], and returns how many were kept. They are
     * copied as they come, not decoded and written again: there may be dozens. Only handed
     * light comes as a loop and there is one of it at a time, so older loops are cleared out;
     * a layer that still names one falls back to its still.
     */
    fun adoptLoop(id: String, frames: List<Uri>): Int {
        dir.listFiles { file -> LOOP_FILE.matches(file.name) && !file.name.startsWith("$id.") }?.forEach { it.delete() }
        var kept = 0
        for (uri in frames.take(MAX_FRAMES)) {
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { from ->
                    frameFile(id, kept).outputStream().use { from.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (!copied) break
            kept++
        }
        LayerImages.setLoop(id, kept)
        return kept
    }

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
            LayerImages.setLoop(id, framesOnFile(id))
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
        const val MAX_FRAMES = 96
        val LOOP_FILE = Regex("""p[0-9a-z]+\.f\d+\.png""")
    }
}
