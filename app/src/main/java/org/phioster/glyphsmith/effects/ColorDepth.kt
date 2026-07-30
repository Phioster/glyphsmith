package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.core.dither.DitherMatrices
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.pipeline.RowParallel

/** Controls for colour-depth reduction. */
@Serializable
data class ColorDepthParams(
    val enabled: Boolean = false,
    /** Levels per axis, 2..256. 256 is a no-op; 2 is one bit per channel. */
    val colorLevels: Int = 8,
    /**
     * Which space the levels are spaced in.
     *
     * [ColorDistance.EUCLIDEAN] steps the stored channels, which is what a bit-crush does and what
     * old hardware did. The two perceptual spaces put their steps where the eye can see them, so
     * the same number of levels loses visibly less in the shadows.
     */
    val colorSpace: ColorDistance = ColorDistance.EUCLIDEAN,
    /** Break the banding up against a blue-noise mask instead of rounding hard. */
    val dithered: Boolean = false,
)

/**
 * Reduces how many values each colour axis may take — bit-crushing, and posterisation.
 *
 * In [ColorDistance.EUCLIDEAN] this is the familiar operation: round each stored channel to the
 * nearest of N evenly spaced values. Cheap, and it is what the hardware being imitated actually
 * did, banding included.
 *
 * In the perceptual spaces the levels are spaced where the eye reads them as even, which is a
 * different and usually better-looking result for the same level count — sRGB spends most of its
 * range on brightnesses the eye barely separates, so evenly spaced sRGB levels waste most of them
 * in the highlights and band badly in the shadows. That path needs the round trip through
 * [ColorDistance.rgbOf], and colours it produces can fall outside sRGB, where they are clamped.
 *
 * Runs in place, in parallel over rows. Every pixel is a pure function of itself and its
 * coordinates, so there is nothing for the bands to coordinate.
 */
object ColorDepth {

    private const val MASK_SIZE = 32

    fun apply(source: Pixels, params: ColorDepthParams, ctx: RenderContext): Pixels {
        if (!params.enabled) return source
        val levels = params.colorLevels.coerceIn(2, 256)
        if (levels >= 256 && !params.dithered) return source

        val mask = if (params.dithered) DitherMatrices.blueNoise(MASK_SIZE) else null
        val span = (MASK_SIZE * MASK_SIZE).toFloat()
        val axes = axesFor(params.colorSpace)
        val width = source.width

        RowParallel.rows(source.height) { band ->
            // Per-band scratch: two workers must never share a coordinate array.
            val coords = FloatArray(3)
            for (y in band) {
                val rowStart = y * width
                for (x in 0 until width) {
                    val index = rowStart + x
                    val pixel = source.data[index]
                    val noise = if (mask == null) {
                        0f
                    } else {
                        mask[y % MASK_SIZE][x % MASK_SIZE] / span - 0.5f
                    }

                    val source3 = params.colorSpace.coordsOf(pixel)
                    for (axis in 0 until 3) {
                        coords[axis] = quantise(source3[axis], axes[axis], levels, noise)
                    }
                    source.data[index] = (PixelOps.alphaOf(pixel) shl 24) or
                        (params.colorSpace.rgbOf(coords) and 0x00FFFFFF)
                }
            }
        }
        return source
    }

    /**
     * The span each axis is quantised over.
     *
     * They have to be stated per space because the axes are not comparable: L\*a\*b\* lightness runs
     * 0..100 while OKLab's runs 0..1, and both chroma axes are signed. Quantising all three over
     * one nominal range would put nearly every level of the chroma axes outside anything sRGB can
     * hold.
     */
    private fun axesFor(space: ColorDistance): Array<ClosedFloatingPointRange<Float>> = when (space) {
        ColorDistance.EUCLIDEAN -> arrayOf(0f..255f, 0f..255f, 0f..255f)
        ColorDistance.CIELAB -> arrayOf(0f..100f, -128f..127f, -128f..127f)
        ColorDistance.OKLAB -> arrayOf(0f..1f, -0.4f..0.4f, -0.4f..0.4f)
    }

    /**
     * Snaps a value to one of [levels] stops across [range], nudged by the dither mask.
     *
     * The nudge is one whole step wide, so a value sitting between two stops resolves to each of
     * them for a proportional share of the mask — which is what makes the average over an area come
     * out as the value that went in, instead of as a band.
     */
    private fun quantise(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        levels: Int,
        noise: Float,
    ): Float {
        val low = range.start
        val step = (range.endInclusive - low) / (levels - 1)
        if (step <= 0f) return value
        val nudged = value + noise * step
        val level = ((nudged - low) / step + 0.5f).toInt().coerceIn(0, levels - 1)
        return low + level * step
    }
}
