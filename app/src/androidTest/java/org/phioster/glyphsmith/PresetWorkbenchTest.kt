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

    /**
     * Exactly what `orb lattice` ships as, which is the fixed point of this round.
     *
     * The first twelve candidates all came out near-black, including the one meant to be the
     * plain mechanism — so the fault was in the starting point, not in the variations. This
     * starts from a setting known to render bright and changes one ingredient per picture. A
     * bisection finds it in one run; guessing finds it in six.
     */
    private fun known() = RenderSettings(
        cellSize = 4,
        depth = 4,
        ditherMode = DitherMode.ORB_DIFFUSE,
        modScale = 22,
        patternDensity = 40,
        colorMode = ColorMode.PALETTE,
        paletteId = "palette.ember",
    )

    private fun glow() = EffectStack(
        glow = GlowParams(
            enabled = true,
            threshold = 42,
            thresholdSmoothing = 40,
            radius = 110,
            intensity = 700,
            falloff = 14,
        ),
    )

    private fun candidates(): List<Pair<String, RenderSettings>> = listOf(
        "01-known-bright" to known(),
        "02-ice" to known().copy(paletteId = "palette.ice"),
        "03-ice-black-ground" to known().copy(
            paletteId = "palette.ice",
            backgroundColor = 0xFF000206.toInt(),
        ),
        "04-ember-glow" to known().copy(effects = glow()),
        "05-ice-glow" to known().copy(paletteId = "palette.ice", effects = glow()),
        "06-ice-glow-weak" to known().copy(
            paletteId = "palette.ice",
            effects = EffectStack(
                glow = GlowParams(enabled = true, threshold = 20, radius = 60, intensity = 300),
            ),
        ),
        "07-ice-depth-6" to known().copy(paletteId = "palette.ice", depth = 6),
        "08-ice-cell-3" to known().copy(paletteId = "palette.ice", cellSize = 3),
        "09-ice-contrast-up" to known().copy(paletteId = "palette.ice", contrast = 1.6f),
        "10-ice-midtones-down" to known().copy(paletteId = "palette.ice", midtones = 30),
        "11-ice-midtones-up" to known().copy(paletteId = "palette.ice", midtones = 70),
        "12-ice-highlights-up" to known().copy(paletteId = "palette.ice", highlights = 78),
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
