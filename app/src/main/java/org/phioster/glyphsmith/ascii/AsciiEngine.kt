package org.phioster.glyphsmith.ascii

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A finished character grid. [colors] is null when the art is monochrome — the renderer
 * then paints every glyph in the ink colour.
 */
class AsciiArt(
    val cols: Int,
    val rows: Int,
    val glyphs: CharArray,
    val colors: IntArray?,
) {
    fun glyphAt(col: Int, row: Int): Char = glyphs[row * cols + col]

    fun colorAt(col: Int, row: Int): Int? = colors?.get(row * cols + col)

    /** The grid as text — this is what the .txt export and the clipboard get. */
    fun toText(): String = buildString(rows * (cols + 1)) {
        for (row in 0 until rows) {
            append(glyphs, row * cols, cols)
            if (row < rows - 1) append('\n')
        }
    }
}

/**
 * Turns a bitmap's pixels into a character grid.
 *
 * Deliberately free of Android types so the whole mapping — cell averaging, tone curve,
 * ramp selection, offset wrapping — is unit-testable on the JVM.
 */
object AsciiEngine {

    /**
     * Monospace glyph cells are taller than they are wide; sampling with the same aspect is
     * what keeps the output from looking vertically stretched.
     */
    const val DEFAULT_CELL_ASPECT = 2.0f

    /** Upper bound on samples per cell per axis — big cells stay fast without visible loss. */
    private const val MAX_SAMPLES_PER_AXIS = 8

    fun convert(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: AsciiParams,
        cellAspect: Float = DEFAULT_CELL_ASPECT,
    ): AsciiArt {
        require(width > 0 && height > 0) { "source must not be empty" }
        require(pixels.size >= width * height) { "pixel buffer smaller than $width×$height" }

        val ramp = params.effectiveRamp().ifEmpty { " " }
        val cellW = params.cellSize.coerceAtLeast(1)
        val cellH = max(1, (params.cellSize * cellAspect).roundToInt())
        val cols = ceil(width.toFloat() / cellW).toInt().coerceAtLeast(1)
        val rows = ceil(height.toFloat() / cellH).toInt().coerceAtLeast(1)

        val glyphs = CharArray(cols * rows)
        val needsColor = params.colorMode != ColorMode.SINGLE
        val colors = if (needsColor) IntArray(cols * rows) else null
        val palette = params.activePalette()

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
                val luma = toneCurve(luminance(avgR, avgG, avgB), params)

                val index = mapToRamp(luma, ramp.length, params.offset)
                val cell = row * cols + col
                glyphs[cell] = ramp[index]
                if (colors != null) {
                    colors[cell] = when (params.colorMode) {
                        ColorMode.SOURCE -> (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                        ColorMode.PALETTE -> Palettes.sample(palette, luma)
                        ColorMode.SINGLE -> params.inkColor
                    }
                }
            }
        }
        return AsciiArt(cols, rows, glyphs, colors)
    }

    /** Rec. 709 luminance, normalised to 0..1. */
    fun luminance(r: Int, g: Int, b: Int): Float =
        (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

    /** Gamma, then contrast around mid grey, then brightness — clamped to 0..1. */
    fun toneCurve(value: Float, params: AsciiParams): Float {
        val gamma = params.gamma.coerceAtLeast(0.05f)
        val gammaed = value.coerceIn(0f, 1f).pow(1f / gamma)
        val contrasted = (gammaed - 0.5f) * params.contrast + 0.5f
        return (contrasted + params.brightness).coerceIn(0f, 1f)
    }

    /**
     * Luminance to ramp index. The offset wraps rather than clamps: wrapping is what makes
     * a large offset fold the tonal range back on itself, which is the whole point of the
     * control — clamping would just flatten the image to one glyph.
     */
    fun mapToRamp(luma: Float, rampLength: Int, offset: Int): Int {
        if (rampLength <= 1) return 0
        val base = (luma.coerceIn(0f, 1f) * (rampLength - 1)).roundToInt()
        val shifted = (base + offset) % rampLength
        return if (shifted < 0) shifted + rampLength else shifted
    }
}
