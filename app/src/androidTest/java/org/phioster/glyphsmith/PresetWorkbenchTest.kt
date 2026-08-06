package org.phioster.glyphsmith

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.pipeline.RenderPipeline
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.RenderSettings

/**
 * A bench for trying preset settings, rather than a test of anything.
 *
 * Presets are the one part of this project that cannot be reasoned into being right. A dither is
 * correct or it is not and a test can say so; a *look* only exists once it has been rendered and
 * seen, and the settings that produce one are found by trying rather than by deriving. Doing that
 * a variant at a time through the app is an evening's work for one preset.
 *
 * So: a list of candidates, rendered in one pass, written out side by side. The list is meant to
 * be rewritten — what is in it now is whatever question was last being asked, kept in the file so
 * the next person can see how an answer was arrived at rather than only what it was.
 *
 * The current question is a glowing orb-diffused figure on near-black: the mechanism is
 * [DitherMode.ORB_DIFFUSE] — an orb modulation surface with a vertical diffusion kernel — and
 * what is being hunted is the *tone*, because the mechanism alone renders a busy mid-blue veil
 * where the reference has isolated bright points in the dark.
 */
@RunWith(AndroidJUnit4::class)
class PresetWorkbenchTest {

    private val bench: File by lazy {
        val given = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val base = given?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(base, "bench").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** Everything the candidates share, so what differs between two pictures is what was varied. */
    private fun base() = RenderSettings(
        cellSize = 3,
        depth = 5,
        ditherMode = DitherMode.ORB_DIFFUSE,
        modScale = 22,
        patternDensity = 40,
        colorMode = ColorMode.PALETTE,
        paletteId = "palette.ice",
        backgroundColor = 0xFF000206.toInt(),
        effects = EffectStack(
            glow = GlowParams(
                enabled = true,
                threshold = 42,
                thresholdSmoothing = 40,
                radius = 110,
                intensity = 700,
                falloff = 14,
            ),
        ),
    )

    /**
     * Tone pushed the way the reference's Adjustments panel is pushed in its own tutorials:
     * contrast up, midtones down so the ground falls away, highlights up so the subject clips.
     */
    private fun RenderSettings.toned() = copy(
        contrast = 1.8f,
        midtones = 30,
        highlights = 75,
    )

    private fun candidates(): List<Pair<String, RenderSettings>> = listOf(
        "01-as-shipped-mechanism" to base(),
        "02-toned" to base().toned(),
        "03-toned-fine-orbs" to base().toned().copy(modScale = 14),
        "04-toned-coarse-orbs" to base().toned().copy(modScale = 34),
        "05-toned-sparse" to base().toned().copy(patternDensity = 22),
        "06-toned-dense" to base().toned().copy(patternDensity = 62),
        "07-toned-cell-2" to base().toned().copy(cellSize = 2),
        "08-toned-cell-6" to base().toned().copy(cellSize = 6),
        "09-toned-big-glow" to base().toned().copy(
            effects = EffectStack(
                glow = GlowParams(
                    enabled = true,
                    threshold = 30,
                    thresholdSmoothing = 45,
                    radius = 170,
                    intensity = 950,
                    falloff = 12,
                ),
            ),
        ),
        "10-toned-few-levels" to base().toned().copy(depth = 3),
        "11-toned-many-levels" to base().toned().copy(depth = 8),
        "12-toned-harder" to base().copy(contrast = 2.4f, midtones = 18, highlights = 88),
    )

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
    fun renderTheCandidates() {
        val (pixels, width, height) = source()
        var written = 0

        candidates().forEach { (name, params) ->
            val bitmap = runCatching {
                RenderPipeline.run(pixels, width, height, params, MAX_SIDE, AppRenderModules).bitmap
            }.getOrNull() ?: return@forEach

            File(bench, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            written++
        }

        assertTrue("no candidate rendered", written == candidates().size)
    }

    private companion object {
        const val MAX_SIDE = 1200
    }
}
