package org.phioster.glyphsmith.state

import android.graphics.Bitmap
import org.phioster.glyphsmith.export.AnimationFormat
import org.phioster.glyphsmith.export.Exporter
import org.phioster.glyphsmith.export.Exports
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.glyph.SvgMode

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
 * The bitmap is recycled here rather than by the caller, because every path that produces one is
 * a path that was once responsible for releasing it itself.
 *
 * ## Why it grew
 *
 * Four exports used to go around this class and call [Exporter] with a `Context` directly — the
 * palette, a batch's images, the GIF and the MP4 — not out of neglect but because [Exports] had
 * no way to say what they needed: raw bytes, or a file named something other than the ordinary
 * timestamp. So the naming and the status lines for those four sat inline in the view model,
 * which is exactly the arrangement this class exists to end, and the only untested export paths
 * left in the app. The seam now covers them, and naming lives here rather than at the call site:
 * a `.gif` and a batch image are files with rules about what they are called, and a rule spelled
 * out where it is used is a rule that gets spelled differently the second time.
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

    /**
     * The palette in use, as its own file.
     *
     * Named for what it is rather than timestamped like everything else: a folder of
     * `glyphsmith-20260804-*.json` files gives no way to tell a palette from a preset export.
     */
    fun palette(json: String): String =
        if (exports.saveJson(json, named("json", PALETTE)) != null) {
            "palette saved to $DOWNLOAD"
        } else {
            SAVE_FAILED
        }

    /** A rendered animation, as whatever bytes its encoder produced. */
    fun animation(bytes: ByteArray, format: AnimationFormat): String {
        val name = Exporter.timestampedName(format.extension)
        return if (exports.saveBytes(bytes, name, format.mimeType) != null) {
            "${format.extension} saved to $DOWNLOAD"
        } else {
            SAVE_FAILED
        }
    }

    /**
     * One image of a batch run, numbered so the results stay in the order they were picked.
     *
     * Returns whether it was written, because a batch reports its own totals rather than a line
     * per file — see [batchStatus]. The bitmap is released either way, for the same reason as in
     * [image]: this is the third path in the app that produces a full-size render, and the first
     * two both used to forget.
     */
    fun batchImage(bitmap: Bitmap, format: ImageFormat, number: Int): Boolean {
        val uri = exports.saveImage(bitmap, format, named(format.extension, "batch-$number"))
        bitmap.recycle()
        return uri != null
    }

    /** What a finished batch says. Failures are counted rather than named — they are files. */
    fun batchStatus(saved: Int, failed: Int): String = if (failed == 0) {
        "batch done — $saved saved to $PICTURES"
    } else {
        "batch done — $saved saved, $failed failed"
    }

    /** The ordinary timestamped name with [what] worked into it, e.g. `glyphsmith-batch-2-…`. */
    private fun named(extension: String, what: String): String =
        Exporter.timestampedName(extension).replace(PREFIX, "$PREFIX$what-")

    fun presets(json: String): String =
        if (exports.saveJson(json, PRESETS_FILE) != null) {
            "presets saved to $DOWNLOAD"
        } else {
            "export failed"
        }

    /** The size is worth saying: an outline SVG of a dense grid is megabytes, and that surprises. */
    /**
     * The pixel path's own vector export.
     *
     * Reports the block count as well as the size, because that is the number a plotter cares
     * about — a file is openable or not, but ten thousand shapes is ten thousand cuts.
     */
    fun pixelSvg(svg: String?, blocks: Int): String {
        if (svg == null) return NOTHING_TO_EXPORT
        val uri = exports.saveSvg(svg)
        return if (uri != null) {
            "saved svg · $blocks shapes (${svg.length / BYTES_PER_KB} KB) to $DOWNLOAD"
        } else {
            SAVE_FAILED
        }
    }

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

        /** What every exported file is called before anything else is said about it. */
        const val PREFIX = "glyphsmith-"
        const val PALETTE = "palette"
    }
}
