package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.pipeline.RowParallel
import kotlin.math.cos
import kotlin.math.sin

/** Controls for the spot-colour press. */
@Serializable
data class SpotColorPrintParams(
    val enabled: Boolean = false,
    /** 0..100 — registration error between plates, up to about eight pixels at full. */
    val misalignment: Int = 30,
    /** 0..100 — how much light each ink removes where it is laid down. */
    val inkOpacity: Int = 85,
    /** 0..100 — how much of the paper's grain shows through the ink. */
    val paperTextureBlend: Int = 25,
    /** 0..100 — how far ink creeps into the fibre around where it was laid. */
    val inkBleed: Int = 20,
    /** Plates on the press, 2..4. Fewer plates is the look; four is full process colour. */
    val inkCount: Int = 3,
    /** 0..100 — how much lighter the paper is than pure white. */
    val paperTone: Int = 4,
    val seed: Int = 1,
)

/**
 * Multi-plate spot printing: each ink is laid down slightly out of register, and bleeds.
 *
 * The separation is [CmykHalftone.separate] — the same grey-component replacement the halftone
 * screen uses, shared rather than reimplemented so neutrals stay neutral in both. What is new here
 * is what happens *after* separation, and it is the part that makes a print look printed:
 *
 * - **Misregistration.** Each plate gets its own sub-pixel rotation and offset, derived from the
 *   seed. Perfect registration is what makes a digital simulation of print look digital; the
 *   coloured fringe along every edge is the tell that a human loaded the paper.
 * - **Bleed.** Ink spreads into the fibre it lands on, so coverage is averaged over a small
 *   neighbourhood. This softens edges the way absorption does, rather than blurring the image.
 * - **Paper.** Stock is never pure white and never flat, so the substrate is a toned base carrying
 *   the same positional grain hash [Subtexture] uses for its paper.
 *
 * Randomness is positional and per-plate — `hash(x, y, seed)` and a per-plate offset — never a
 * sequential `Random`, because this node runs its rows in parallel and a shared generator would
 * make the output depend on thread scheduling.
 */
object SpotColorPrint {

    private const val MAX_OFFSET_PIXELS = 8f

    fun apply(source: Pixels, params: SpotColorPrintParams, ctx: RenderContext): Pixels {
        if (!params.enabled) return source

        val width = source.width
        val height = source.height
        val inks = params.inkCount.coerceIn(2, 4)
        val opacity = params.inkOpacity.coerceIn(0, 100) / 100f
        val paperBlend = params.paperTextureBlend.coerceIn(0, 100) / 100f
        val bleed = params.inkBleed.coerceIn(0, 100) / 100f
        val paperTone = 1f - params.paperTone.coerceIn(0, 100) / 100f * 0.15f
        val spread = params.misalignment.coerceIn(0, 100) / 100f * MAX_OFFSET_PIXELS

        // Plate offsets, computed once on the calling thread. Plate 0 is the key and stays put —
        // a press registers everything else against one plate, it does not shift them all.
        val offsetX = FloatArray(inks)
        val offsetY = FloatArray(inks)
        for (ink in 1 until inks) {
            val angle = hash(ink, 0, params.seed) * 2f * Math.PI.toFloat()
            val distance = hash(0, ink, params.seed) * spread
            offsetX[ink] = cos(angle) * distance
            offsetY[ink] = sin(angle) * distance
        }

        val out = source.buffer()

        RowParallel.rows(height) { band ->
            // Per-band scratch, so no two workers share it. Allocated here rather than pooled
            // because the pool is single-threaded and this is a handful of floats.
            val coverage = FloatArray(4)
            for (y in band) {
                val rowStart = y * width
                for (x in 0 until width) {
                    val grain = hash(x, y, params.seed)
                    // The substrate: toned, and grainy in proportion to how much shows through.
                    val paper = paperTone - paperBlend * 0.12f * (grain - 0.5f) * 2f
                    var r = paper
                    var g = paper
                    var b = paper

                    for (ink in 0 until inks) {
                        val amount = inkAt(
                            source, params, coverage, x, y, offsetX[ink], offsetY[ink], ink, bleed,
                        )
                        if (amount <= 0f) continue
                        val laid = (amount * opacity).coerceIn(0f, 1f)
                        // Subtractive, exactly as the halftone screen composites: each ink removes
                        // light from the channels it absorbs.
                        when (ink) {
                            CmykHalftone.CYAN -> r *= 1f - laid
                            CmykHalftone.MAGENTA -> g *= 1f - laid
                            CmykHalftone.YELLOW -> b *= 1f - laid
                            else -> {
                                r *= 1f - laid
                                g *= 1f - laid
                                b *= 1f - laid
                            }
                        }
                    }

                    out[rowStart + x] = PixelOps.argb(
                        PixelOps.alphaOf(source.data[rowStart + x]),
                        (r * 255f).toInt().coerceIn(0, 255),
                        (g * 255f).toInt().coerceIn(0, 255),
                        (b * 255f).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source.derive(out)
    }

    /**
     * One plate's coverage at a point, shifted by its registration error and spread by bleed.
     *
     * Bleed is a five-tap average rather than a full kernel: ink spreads about as far as it is
     * thick, and a wider kernel starts blurring the picture instead of softening the ink.
     */
    @Suppress("LongParameterList")
    private fun inkAt(
        source: Pixels,
        params: SpotColorPrintParams,
        coverage: FloatArray,
        x: Int,
        y: Int,
        dx: Float,
        dy: Float,
        ink: Int,
        bleed: Float,
    ): Float {
        val centre = coverageAt(source, params, coverage, x - dx, y - dy, ink)
        if (bleed <= 0f) return centre
        val reach = 1f + bleed * 2f
        val neighbours =
            coverageAt(source, params, coverage, x - dx - reach, y - dy, ink) +
                coverageAt(source, params, coverage, x - dx + reach, y - dy, ink) +
                coverageAt(source, params, coverage, x - dx, y - dy - reach, ink) +
                coverageAt(source, params, coverage, x - dx, y - dy + reach, ink)
        // Bleed only ever adds ink — absorption spreads a mark outwards, it does not thin it.
        return maxOf(centre, centre * (1f - bleed) + neighbours / 4f * bleed)
    }

    private fun coverageAt(
        source: Pixels,
        params: SpotColorPrintParams,
        coverage: FloatArray,
        x: Float,
        y: Float,
        ink: Int,
    ): Float {
        val sx = x.toInt().coerceIn(0, source.width - 1)
        val sy = y.toInt().coerceIn(0, source.height - 1)
        CmykHalftone.separate(source.data[sy * source.width + sx], GCR, GAIN, coverage)
        return coverage[ink]
    }

    /**
     * Fixed separation constants. The halftone node exposes these as sliders because a screen's
     * whole look is in its ink balance; here the look is in the registration and the bleed, and two
     * more sliders that shift everything slightly would only make those two harder to judge.
     */
    private const val GCR = 0.8f
    private const val GAIN = 1f

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
