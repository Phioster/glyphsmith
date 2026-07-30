package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.dither.DitherMatrices
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.pipeline.RowParallel

/** Controls for the post-render blue-noise dither. */
@Serializable
data class BlueNoiseDitherParams(
    val enabled: Boolean = false,
    /** Mask magnification, 1..8. Above 1 the grain coarsens without becoming a grid. */
    val noiseScale: Int = 1,
    /** 0..100 — shifts the whole mask, biasing the result light or dark. 50 is neutral. */
    val threshold: Int = 50,
    /** Output levels per channel, 2..16. Two is a hard black-and-white per channel. */
    val levels: Int = 2,
    /** Quantise luminance instead of each channel, keeping hue and dithering only tone. */
    val monochrome: Boolean = false,
)

/**
 * Dithers the rendered image against a blue-noise mask.
 *
 * Blue noise has no periodic structure, so the pattern it leaves reads as grain rather than as the
 * cross-hatch a Bayer matrix leaves — which is the entire reason to prefer it when the dither is
 * meant to disappear into the image instead of decorating it.
 *
 * This is the *post-render* dither and it is not the same feature as `DitherMode.BLUE_NOISE_16/32`.
 * Those quantise the source into glyph or palette levels before anything is drawn; this one works
 * on the finished pixels, so it stacks with them and with everything else in the chain.
 *
 * The mask comes from [DitherMatrices.blueNoise], the void-and-cluster generator the dither core
 * already ships, and this is its first use outside that package. Note that generator caches on size
 * alone and ignores its seed argument, so this node asks for one fixed size and offers no seed
 * rather than exposing a control that would silently do nothing.
 *
 * Works in place and parallel over rows: the mask is a pure function of `(x, y)`, so a pixel's
 * result depends on nothing but itself.
 */
object BlueNoiseDither {

    /** 32 is large enough that the tiling is invisible and small enough to stay cache-warm. */
    private const val MASK_SIZE = 32

    fun apply(source: Pixels, params: BlueNoiseDitherParams, ctx: RenderContext): Pixels {
        if (!params.enabled) return source
        val levels = params.levels.coerceIn(2, 16)
        if (levels >= 256) return source

        val mask = DitherMatrices.blueNoise(MASK_SIZE)
        val span = (MASK_SIZE * MASK_SIZE).toFloat()
        val scale = params.noiseScale.coerceIn(1, 8)
        val bias = (params.threshold.coerceIn(0, 100) - 50) / 100f
        val width = source.width
        val step = 255f / (levels - 1)

        RowParallel.rows(source.height) { band ->
            for (y in band) {
                val rowStart = y * width
                for (x in 0 until width) {
                    val index = rowStart + x
                    val pixel = source.data[index]
                    // Rank in 0 until n*n, so dividing gives an evenly distributed 0..1 threshold.
                    val rank = mask[(y / scale) % MASK_SIZE][(x / scale) % MASK_SIZE]
                    val noise = rank / span - 0.5f + bias

                    source.data[index] = if (params.monochrome) {
                        val luma = PixelOps.luminance(pixel) * 255f
                        val level = quantise(luma, noise, step)
                        // Tone is replaced, hue kept: scale each channel by the change in luminance.
                        val ratio = if (luma <= 0.5f) 0f else level / luma
                        PixelOps.argb(
                            PixelOps.alphaOf(pixel),
                            (PixelOps.redOf(pixel) * ratio).toInt().coerceIn(0, 255),
                            (PixelOps.greenOf(pixel) * ratio).toInt().coerceIn(0, 255),
                            (PixelOps.blueOf(pixel) * ratio).toInt().coerceIn(0, 255),
                        )
                    } else {
                        PixelOps.argb(
                            PixelOps.alphaOf(pixel),
                            quantise(PixelOps.redOf(pixel).toFloat(), noise, step).toInt(),
                            quantise(PixelOps.greenOf(pixel).toFloat(), noise, step).toInt(),
                            quantise(PixelOps.blueOf(pixel).toFloat(), noise, step).toInt(),
                        )
                    }
                }
            }
        }
        return source
    }

    /**
     * Rounds to the nearest level after nudging by the mask.
     *
     * The nudge is one whole step wide, which is what makes the dither work: a value halfway
     * between two levels lands on the lower one for half the mask's ranks and on the upper one for
     * the other half, so the average over an area is the value that was asked for.
     */
    private fun quantise(value: Float, noise: Float, step: Float): Float {
        val nudged = value + noise * step
        val level = (nudged / step + 0.5f).toInt().coerceAtLeast(0)
        return (level * step).coerceIn(0f, 255f)
    }
}
