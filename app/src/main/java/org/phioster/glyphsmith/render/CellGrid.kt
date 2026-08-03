package org.phioster.glyphsmith.render

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.phioster.glyphsmith.core.image.Adjustments

/**
 * A source image reduced to a grid of cells: one luminance per cell, and one colour per cell
 * when anything downstream needs it.
 *
 * This is the part of the render that both modes share entirely. A glyph cell and a dithered
 * pixel block are the same thing at this stage — a region of the source averaged down to one
 * value — and the only difference is how big the region is and what the value becomes later.
 *
 * [colors] is null when the output will be monochrome, so the pass does not pay for a grid
 * nothing reads.
 */
class CellGrid(
    val cols: Int,
    val rows: Int,
    val luma: FloatArray,
    val colors: IntArray?,
)

/**
 * Averages a source image down to a [CellGrid].
 *
 * Two passes, not one. Both dithering and edge detection need to see a cell's *neighbours* —
 * error diffusion writes into cells it has not reached yet, and Sobel reads all eight around
 * it — so the grid has to be complete before anything looks at it. Blur and denoise sit
 * between the two for the same reason: the sampler has not finished the grid yet, and by the
 * time the quantiser runs the values are already committed.
 *
 * Free of Android types, so the whole reduction is unit-testable on the JVM.
 */
object CellSampler {

    /** Upper bound on samples per cell per axis — big cells stay fast without visible loss. */
    private const val MAX_SAMPLES_PER_AXIS = 8

    fun sample(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: RenderSettings,
        cellWidth: Int,
        cellHeight: Int,
    ): CellGrid {
        require(width > 0 && height > 0) { "source must not be empty" }
        require(pixels.size >= width * height) { "pixel buffer smaller than $width×$height" }

        val cellW = cellWidth.coerceAtLeast(1)
        val cellH = cellHeight.coerceAtLeast(1)
        val cols = ceil(width.toFloat() / cellW).toInt().coerceAtLeast(1)
        val rows = ceil(height.toFloat() / cellH).toInt().coerceAtLeast(1)

        val needsColor = params.colorMode != ColorMode.SINGLE
        val luma = FloatArray(cols * rows)
        val colors = if (needsColor) IntArray(cols * rows) else null

        averagePass(pixels, width, height, cellW, cellH, cols, rows, params, luma, colors)

        return CellGrid(cols, rows, neighbourhoodPass(params, cols, rows, luma, colors), colors)
    }

    /**
     * Cell size for the glyph path: a monospace cell is taller than it is wide, and sampling
     * with the same aspect is what keeps the output from looking vertically stretched.
     */
    fun cellHeightFor(cellSize: Int, cellAspect: Float): Int =
        max(1, (cellSize * cellAspect).roundToInt())

    /** Average each cell's pixels and run the tone curve over the result. */
    @Suppress("LongParameterList")
    private fun averagePass(
        pixels: IntArray,
        width: Int,
        height: Int,
        cellW: Int,
        cellH: Int,
        cols: Int,
        rows: Int,
        params: RenderSettings,
        lumaGrid: FloatArray,
        colorGrid: IntArray?,
    ) {
        val stepX = max(1, cellW / MAX_SAMPLES_PER_AXIS)
        val stepY = max(1, cellH / MAX_SAMPLES_PER_AXIS)

        for (row in 0 until rows) {
            val yStart = row * cellH
            val yEnd = min(yStart + cellH, height)
            for (col in 0 until cols) {
                val xStart = col * cellW
                val xEnd = min(xStart + cellW, width)

                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var samples = 0
                var y = yStart
                while (y < yEnd) {
                    val rowOffset = y * width
                    var x = xStart
                    while (x < xEnd) {
                        val pixel = pixels[rowOffset + x]
                        sumR += (pixel shr 16) and 0xFF
                        sumG += (pixel shr 8) and 0xFF
                        sumB += pixel and 0xFF
                        samples++
                        x += stepX
                    }
                    y += stepY
                }
                if (samples == 0) samples = 1

                val avgR = (sumR / samples).toInt()
                val avgG = (sumG / samples).toInt()
                val avgB = (sumB / samples).toInt()

                val cell = row * cols + col
                // Hue and saturation land here, before the luminance is taken, because the
                // luminance *is* what the dither reads — adjusting colour afterwards would
                // change how the cell is painted without changing which level it gets.
                val adjusted = Adjustments.colorAdjust(avgR, avgG, avgB, params.hue, params.saturation)
                val r = (adjusted shr 16) and 0xFF
                val g = (adjusted shr 8) and 0xFF
                val b = adjusted and 0xFF
                lumaGrid[cell] = toneCurve(luminance(r, g, b), params)
                colorGrid?.set(cell, adjusted)
            }
        }
    }

    /**
     * The adjustments that need to see a cell's neighbours.
     *
     * Denoise runs before blur on purpose. A median removes outliers, and feeding a blur
     * outliers it then has to average is how a single hot pixel becomes a grey smudge the
     * width of the kernel.
     */
    private fun neighbourhoodPass(
        params: RenderSettings,
        cols: Int,
        rows: Int,
        lumaGrid: FloatArray,
        colorGrid: IntArray?,
    ): FloatArray {
        var luma = lumaGrid
        if (params.denoise > 0) {
            luma = Adjustments.denoise(luma, cols, rows, params.denoise)
            colorGrid?.let {
                Adjustments.denoiseColors(it, cols, rows, params.denoise).copyInto(it)
            }
        }
        if (params.preBlur > 0) {
            luma = Adjustments.blur(luma, cols, rows, params.preBlur)
            colorGrid?.let {
                Adjustments.blurColors(it, cols, rows, params.preBlur).copyInto(it)
            }
        }
        return luma
    }

    /** Rec. 709 luminance, normalised to 0..1. */
    fun luminance(r: Int, g: Int, b: Int): Float =
        (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

    /** Gamma, then contrast around mid grey, then brightness, then midtones and highlights. */
    fun toneCurve(value: Float, params: RenderSettings): Float {
        val gamma = params.gamma.coerceAtLeast(0.05f)
        val gammaed = value.coerceIn(0f, 1f).pow(1f / gamma)
        val contrasted = (gammaed - 0.5f) * params.contrast + 0.5f
        val levelled = (contrasted + params.brightness).coerceIn(0f, 1f)
        return Adjustments.tone(levelled, params.midtones, params.highlights)
    }
}
