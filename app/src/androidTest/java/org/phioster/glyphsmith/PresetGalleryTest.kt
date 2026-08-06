package org.phioster.glyphsmith

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.glyphsmith.data.PresetLibrary
import org.phioster.glyphsmith.pipeline.RenderPipeline

/**
 * Every shipped preset, run over one photograph, written out to be looked at.
 *
 * Not an assertion about how a preset should look — there is no such assertion to write. This is
 * a *tool*: the library is ninety pictures and the only way to judge whether it is any good is to
 * see them side by side, over the same source, with nothing else varying. The question that
 * prompted it was "why do these not land the way the reference app's do", and that question
 * cannot be answered from source code.
 *
 * On a device rather than on the JVM because the pipeline draws into a real `Bitmap`, so the JVM
 * harness stubs exactly the part being measured. It rides in the same emulator run as the
 * walkthrough and lands in the same output directory, so `tools/screens.py` brings it back with
 * everything else.
 *
 * The one thing it does assert is that a preset renders at all — a preset in the library that
 * throws is a bug no unit test would catch, since none of them run the whole pipeline.
 */
@RunWith(AndroidJUnit4::class)
class PresetGalleryTest {

    private val gallery: File by lazy {
        val given = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val base = given?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(base, "gallery").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /**
     * The same photograph the `examples/` folder uses, so a picture here and a picture there are
     * comparable, and so the source is one a dither has something to bite on: a lit figure with
     * real midtones. A silhouette makes every algorithm look alike, which is a lesson this
     * project has already paid for once.
     */
    private fun source(): Triple<IntArray, Int, Int> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("source.jpg").use { BitmapFactory.decodeStream(it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val size = Triple(pixels, bitmap.width, bitmap.height)
        bitmap.recycle()
        return size
    }

    @Test
    fun everyPresetRendersAndIsWrittenOut() {
        val (pixels, width, height) = source()
        val modules = AppRenderModules
        val failed = mutableListOf<String>()
        var written = 0

        PresetLibrary.builtIns.forEachIndexed { index, preset ->
            val result = runCatching {
                RenderPipeline.run(pixels, width, height, preset.params, MAX_SIDE, modules)
            }
            val bitmap = result.getOrNull()?.bitmap
            if (bitmap == null) {
                failed += "${preset.name}: ${result.exceptionOrNull()?.message ?: "no bitmap"}"
                return@forEachIndexed
            }

            // Named by index as well as by preset, so the sheet comes out in library order and a
            // name with a slash or a colon in it cannot make an unwritable file.
            val safe = preset.name.replace(Regex("[^a-z0-9]+"), "-").trim('-')
            File(gallery, "%02d-%s.png".format(index + 1, safe)).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            written++
        }

        File(gallery, "presets.txt").writeText(
            PresetLibrary.builtIns.joinToString("\n") { "${it.category} · ${it.name}" },
        )

        assertTrue("presets that would not render: $failed", failed.isEmpty())
        assertTrue("nothing was rendered", written > 0)
    }

    private companion object {
        /** Small enough that ninety renders fit in an emulator run, large enough to judge. */
        const val MAX_SIDE = 420
    }
}
