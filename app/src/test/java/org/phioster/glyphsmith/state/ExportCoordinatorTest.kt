package org.phioster.glyphsmith.state

import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.glyphsmith.export.Exports
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgMode
import org.robolectric.RobolectricTestRunner

/**
 * What the application says and does about an export.
 *
 * Not what the platform does with it: [org.phioster.glyphsmith.export.Exporter] writes through
 * MediaStore, which Robolectric does not back, so a test against the real sink could only ever
 * watch an insert return null. The question worth answering is the one above that — is there
 * anything to export, was the write refused, and is the bitmap released either way — and it
 * only became answerable once the sink could be stood in for.
 */
@RunWith(RobolectricTestRunner::class)
class ExportCoordinatorTest {

    /** Records what it was asked to do, and can be told to refuse. */
    private class FakeExports(private val succeed: Boolean = true) : Exports {
        val calls = mutableListOf<String>()
        var sharedText: String? = null
        var clipboard: String? = null

        private fun result(): Uri? = if (succeed) Uri.parse("content://test/1") else null

        override fun saveImage(bitmap: Bitmap, format: ImageFormat): Uri? {
            calls += "saveImage:${format.extension}"
            return result()
        }

        override fun saveText(text: String): Uri? {
            calls += "saveText"; return result()
        }

        override fun saveJson(text: String, name: String): Uri? {
            calls += "saveJson:$name"; return result()
        }

        override fun saveSvg(text: String): Uri? {
            calls += "saveSvg"; return result()
        }

        override fun saveHtml(text: String): Uri? {
            calls += "saveHtml"; return result()
        }

        override fun saveAnsi(text: String): Uri? {
            calls += "saveAnsi"; return result()
        }

        override fun copyToClipboard(text: String) {
            calls += "copy"; clipboard = text
        }

        override fun shareImage(bitmap: Bitmap, format: ImageFormat) {
            calls += "shareImage"
        }

        override fun shareText(text: String) {
            calls += "shareText"; sharedText = text
        }
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private fun coordinator(succeed: Boolean = true): Pair<ExportCoordinator, FakeExports> {
        val fake = FakeExports(succeed)
        return ExportCoordinator(fake) to fake
    }

    // --- nothing loaded -----------------------------------------------------------------

    /**
     * The export buttons stay live with no image loaded, so pressing one has to say why it did
     * nothing. Silence would read as a broken button.
     */
    @Test
    fun `with nothing to export it says so and writes nothing`() {
        val (exports, fake) = coordinator()

        assertEquals("nothing to export", exports.image(null, ImageFormat.PNG))
        assertEquals("nothing to export", exports.text(null))
        assertEquals("nothing to export", exports.html(null))
        assertEquals("nothing to export", exports.ansi(null))
        assertEquals("nothing to export", exports.svg(null, SvgMode.TEXT))
        assertEquals("nothing to copy", exports.copy(null))
        assertEquals("nothing to share", exports.shareImage(null, ImageFormat.PNG))
        assertEquals("nothing to share", exports.shareText(null))

        assertTrue("something was written with nothing to write: ${fake.calls}", fake.calls.isEmpty())
    }

    // --- a refused write ----------------------------------------------------------------

    /**
     * A refused write is the case a user actually meets — a revoked permission, a full disk —
     * and reporting it as success would leave them hunting for a file that was never created.
     */
    @Test
    fun `a refused write is reported as a failure rather than a success`() {
        val (exports, _) = coordinator(succeed = false)

        assertEquals("save failed", exports.image(bitmap(), ImageFormat.PNG))
        assertEquals("save failed", exports.text("art"))
        assertEquals("save failed", exports.html("<pre>"))
        assertEquals("save failed", exports.ansi("[0m"))
        assertEquals("save failed", exports.svg("<svg/>", SvgMode.TEXT))
        assertEquals("export failed", exports.presets("[]"))
    }

    // --- the ordinary case --------------------------------------------------------------

    @Test
    fun `a saved image names the format and where it went`() {
        val (exports, fake) = coordinator()

        val status = exports.image(bitmap(), ImageFormat.PNG)

        assertEquals("saved png to Pictures/Glyphsmith", status)
        assertEquals(listOf("saveImage:png"), fake.calls)
    }

    @Test
    fun `saved text, html and ansi all name the download folder`() {
        val (exports, _) = coordinator()

        assertEquals("saved to Download/Glyphsmith", exports.text("art"))
        assertEquals("saved to Download/Glyphsmith", exports.html("<pre>"))
        assertEquals("saved to Download/Glyphsmith", exports.ansi("esc"))
    }

    @Test
    fun `presets go out under a name that says what they are`() {
        val (exports, fake) = coordinator()

        assertEquals("presets saved to Download/Glyphsmith", exports.presets("{}"))
        assertEquals(listOf("saveJson:glyphsmith-presets.json"), fake.calls)
    }

    /** An outline svg of a dense grid is megabytes, and that surprises people. */
    @Test
    fun `an svg reports its size and its mode`() {
        val (exports, _) = coordinator()

        val status = exports.svg("x".repeat(4096), SvgMode.OUTLINES)

        assertTrue(status, status.contains("4 KB"))
        assertTrue(status, status.contains(SvgMode.OUTLINES.label))
        assertTrue(status, status.endsWith("Download/Glyphsmith"))
    }

    @Test
    fun `copying reports how much went to the clipboard`() {
        val (exports, fake) = coordinator()

        assertEquals("12 chars copied", exports.copy("hello world!"))
        assertEquals("hello world!", fake.clipboard)
    }

    @Test
    fun `sharing passes the text through untouched`() {
        val (exports, fake) = coordinator()

        assertEquals("shared", exports.shareText("the grid"))
        assertEquals("the grid", fake.sharedText)
    }

    // --- the bitmap ---------------------------------------------------------------------

    /**
     * A full-size render is the largest allocation the app makes, and both paths that produce
     * one used to be responsible for releasing it themselves. Recycling here is what makes the
     * responsibility one place rather than two.
     */
    @Test
    fun `an exported bitmap is released whether the write succeeded or not`() {
        val saved = bitmap()
        ExportCoordinator(FakeExports()).image(saved, ImageFormat.PNG)
        assertTrue("a saved bitmap was left allocated", saved.isRecycled)

        val refused = bitmap()
        ExportCoordinator(FakeExports(succeed = false)).image(refused, ImageFormat.PNG)
        assertTrue("a bitmap leaked when the write was refused", refused.isRecycled)

        val shared = bitmap()
        ExportCoordinator(FakeExports()).shareImage(shared, ImageFormat.PNG)
        assertTrue("a shared bitmap was left allocated", shared.isRecycled)
    }
}
