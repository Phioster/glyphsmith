package org.phioster.glyphsmith.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * Where a finished render is sent.
 *
 * A seam rather than an abstraction for its own sake. ARCHITECTURE.md names *Export Providers*
 * as one of the plugin categories, and this is the shape of one, minus the stable ids and the
 * registry — those change the stored format and belong to their own step.
 *
 * What it buys today is that the decision layer above it becomes testable. [Exporter] writes
 * through MediaStore, which Robolectric does not back: an insert there returns null, so a test
 * against the real thing could only ever watch it fail. The interesting question is not whether
 * `insert` works — the platform decides that — but what the application *says and does* when it
 * works and when it does not, and that only becomes answerable once the sink can be stood in for.
 */
interface Exports {

    fun saveImage(bitmap: Bitmap, format: ImageFormat): Uri?

    fun saveText(text: String): Uri?

    fun saveJson(text: String, name: String): Uri?

    fun saveSvg(text: String): Uri?

    fun saveHtml(text: String): Uri?

    fun saveAnsi(text: String): Uri?

    fun copyToClipboard(text: String)

    fun shareImage(bitmap: Bitmap, format: ImageFormat)

    fun shareText(text: String)
}

/**
 * The real one: [Exporter], unchanged, with the `Context` bound once instead of threaded
 * through every call.
 */
class AndroidExports(private val context: Context) : Exports {

    override fun saveImage(bitmap: Bitmap, format: ImageFormat): Uri? =
        Exporter.saveImage(context, bitmap, format)

    override fun saveText(text: String): Uri? = Exporter.saveText(context, text)

    override fun saveJson(text: String, name: String): Uri? = Exporter.saveJson(context, text, name)

    override fun saveSvg(text: String): Uri? = Exporter.saveSvg(context, text)

    override fun saveHtml(text: String): Uri? = Exporter.saveHtml(context, text)

    override fun saveAnsi(text: String): Uri? = Exporter.saveAnsi(context, text)

    override fun copyToClipboard(text: String) = Exporter.copyToClipboard(context, text)

    override fun shareImage(bitmap: Bitmap, format: ImageFormat) =
        Exporter.shareImage(context, bitmap, format)

    override fun shareText(text: String) = Exporter.shareText(context, text)
}
