package org.phioster.glyphsmith.state

import android.graphics.Bitmap
import org.phioster.glyphsmith.export.Exports
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgMode

/**
 * What happens to a finished render, and what is said about it.
 *
 * Third slice of splitting the ViewModel. The saving itself is [Exports]; this is the layer
 * above it — the part that decides there is nothing to export, that a refused write is a
 * failure worth reporting rather than a silence, and what each of those reads as.
 *
 * That layer used to be nine copies of the same three lines spread through the ViewModel, each
 * spelling out its own message, and none of them reachable from a test.
 *
 * The messages are gathered here rather than left inline for a reason worth stating: `saved to
 * Download/Glyphsmith` appears on four different paths and had been written out four times, so
 * moving the folder meant finding all four.
 *
 * The bitmap is recycled here rather than by the caller, because the two paths that produce one
 * are the two that were most likely to forget.
 */
class ExportCoordinator(private val exports: Exports) {

    /**
     * Saves the rendered image and recycles it.
     *
     * [bitmap] is null when there is nothing loaded, which is a state to report rather than an
     * error — the export buttons stay live so that pressing one says why it did nothing.
     */
    fun image(bitmap: Bitmap?, format: ImageFormat): String {
        if (bitmap == null) return NOTHING_TO_EXPORT
        val uri = exports.saveImage(bitmap, format)
        bitmap.recycle()
        return if (uri != null) "saved ${format.extension} to $PICTURES" else SAVE_FAILED
    }

    fun text(text: String?): String = save(text) { exports.saveText(it) }

    fun html(text: String?): String = save(text) { exports.saveHtml(it) }

    fun ansi(text: String?): String = save(text) { exports.saveAnsi(it) }

    fun presets(json: String): String =
        if (exports.saveJson(json, PRESETS_FILE) != null) {
            "presets saved to $DOWNLOAD"
        } else {
            "export failed"
        }

    /** The size is worth saying: an outline SVG of a dense grid is megabytes, and that surprises. */
    fun svg(svg: String?, mode: SvgMode): String {
        if (svg == null) return NOTHING_TO_EXPORT
        val uri = exports.saveSvg(svg)
        return if (uri != null) {
            "saved ${mode.label} svg (${svg.length / BYTES_PER_KB} KB) to $DOWNLOAD"
        } else {
            SAVE_FAILED
        }
    }

    fun copy(text: String?): String {
        if (text == null) return "nothing to copy"
        exports.copyToClipboard(text)
        return "${text.length} chars copied"
    }

    fun shareImage(bitmap: Bitmap?, format: ImageFormat): String {
        if (bitmap == null) return "nothing to share"
        exports.shareImage(bitmap, format)
        bitmap.recycle()
        return "shared"
    }

    fun shareText(text: String?): String {
        if (text == null) return "nothing to share"
        exports.shareText(text)
        return "shared"
    }

    private fun save(text: String?, write: (String) -> Any?): String {
        if (text == null) return NOTHING_TO_EXPORT
        return if (write(text) != null) "saved to $DOWNLOAD" else SAVE_FAILED
    }

    private companion object {
        const val NOTHING_TO_EXPORT = "nothing to export"

        /**
         * Where the files land. Named once: four paths say the same folder, and it had been
         * spelled out on each of them.
         */
        const val PICTURES = "Pictures/Glyphsmith"
        const val DOWNLOAD = "Download/Glyphsmith"
        const val SAVE_FAILED = "save failed"
        const val PRESETS_FILE = "glyphsmith-presets.json"
        const val BYTES_PER_KB = 1024
    }
}
