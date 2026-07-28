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
 * Turns a bitmap's pixels into a character grid, in two passes.
 *
 * Pass one averages each cell and applies the tone curve, producing a luminance grid. Pass
 * two turns those luminances into glyphs. They are separate because both dithering and edge
 * detection need to see a cell's *neighbours* — error diffusion writes into cells it hasn't
 * reached yet, and Sobel reads all eight around it. A single fused pass can do neither.
 *
 * Deliberately free of Android types, so the whole mapping is unit-testable on the JVM.
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

        val needsColor = params.colorMode != ColorMode.SINGLE
        val lumaGrid = FloatArray(cols * rows)
        val colorGrid = if (needsColor) IntArray(cols * rows) else null

        samplePass(pixels, width, height, cellW, cellH, cols, rows, params, lumaGrid, colorGrid)

        return glyphPass(params, ramp, cols, rows, lumaGrid, colorGrid)
    }

    /** Pass one: average each cell's pixels and run the tone curve over the result. */
    @Suppress("LongParameterList")
    private fun samplePass(
        pixels: IntArray,
        width: Int,
        height: Int,
        cellW: Int,
        cellH: Int,
        cols: Int,
        rows: Int,
        params: AsciiParams,
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
                lumaGrid[cell] = toneCurve(luminance(avgR, avgG, avgB), params)
                colorGrid?.set(cell, (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB)
            }
        }
    }

    /** Pass two: dither, quantise, offset, and let strong edges override the result. */
    private fun glyphPass(
        params: AsciiParams,
        ramp: String,
        cols: Int,
        rows: Int,
        lumaGrid: FloatArray,
        colorGrid: IntArray?,
    ): AsciiArt {
        val levels = ramp.length
        val glyphs = CharArray(cols * rows)
        val colors = if (colorGrid != null) IntArray(cols * rows) else null
        val palette = params.activePalette()

        val mode = params.ditherMode
        val strength = (params.ditherStrength / 100f).coerceIn(0f, 1f)
        val ordered = Dither.isOrdered(mode)
        val kernel = Dither.diffusionKernel(mode)
        val depth = Dither.kernelDepth(mode)
        // Only the rows a kernel can still reach are kept, not a full-grid error buffer.
        val errorRows = Array(depth) { FloatArray(cols) }

        val edges = if (params.edgeEnabled) EdgeDetect.sobel(lumaGrid, cols, rows) else null
        val edgeSet = EdgeDetect.setById(params.edgeSetId)
        val edgeThreshold = params.edgeThreshold / 100f

        for (row in 0 until rows) {
            val currentError = errorRows[row % depth]
            // Serpentine scanning alternates direction so diffusion errors don't all drift
            // the same way and form the classic diagonal worm pattern.
            val leftToRight = !params.serpentine || row % 2 == 0
            var step = 0
            while (step < cols) {
                val col = if (leftToRight) step else cols - 1 - step
                val cell = row * cols + col
                val base = lumaGrid[cell]

                val target = when {
                    ordered -> base + (Dither.orderedThreshold(mode, col, row) - 0.5f) *
                        strength / max(1, levels - 1)

                    kernel.isNotEmpty() -> base + currentError[col]
                    else -> base
                }

                val index = quantize(target, levels)

                if (!ordered && kernel.isNotEmpty()) {
                    val reproduced = if (levels <= 1) 0f else index.toFloat() / (levels - 1)
                    val error = (target - reproduced) * strength
                    for (tap in kernel) {
                        val dx = if (leftToRight) tap.dx else -tap.dx
                        val tx = col + dx
                        val ty = row + tap.dy
                        if (tx < 0 || tx >= cols || ty >= rows) continue
                        errorRows[ty % depth][tx] += error * tap.weight
                    }
                }

                var glyph = ramp[applyOffset(index, levels, params.offset)]
                if (edges != null) {
                    val magnitude = edges.magnitudeAt(col, row)
                    if (magnitude >= edgeThreshold) {
                        glyph = EdgeDetect.glyphFor(edges.angleAt(col, row), edgeSet)
                    } else if (params.edgeOnly) {
                        glyph = ' '
                    }
                }
                glyphs[cell] = glyph

                if (colors != null && colorGrid != null) {
                    colors[cell] = when (params.colorMode) {
                        ColorMode.SOURCE -> colorGrid[cell]
                        // Sampled from the *undithered* luminance on purpose: colour that
                        // flickers in step with the glyphs reads as twice the noise.
                        ColorMode.PALETTE -> Palettes.sample(palette, base)
                        ColorMode.SINGLE -> params.inkColor
                    }
                }
                step++
            }
            // The row just consumed becomes the buffer for the row `depth` further down.
            currentError.fill(0f)
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
     * Luminance to a ramp index, *before* any offset. Dithering needs this separately: the
     * error is the difference between the requested tone and the tone actually reproduced,
     * which only means anything on the un-rotated ramp.
     */
    fun quantize(luma: Float, rampLength: Int): Int {
        if (rampLength <= 1) return 0
        return (luma.coerceIn(0f, 1f) * (rampLength - 1)).roundToInt()
    }

    /**
     * The offset wraps rather than clamps: wrapping is what makes a large offset fold the
     * tonal range back on itself, which is the whole point of the control — clamping would
     * just flatten the image to one glyph.
     */
    fun applyOffset(index: Int, rampLength: Int, offset: Int): Int {
        if (rampLength <= 1) return 0
        val shifted = (index + offset) % rampLength
        return if (shifted < 0) shifted + rampLength else shifted
    }

    fun mapToRamp(luma: Float, rampLength: Int, offset: Int): Int =
        applyOffset(quantize(luma, rampLength), rampLength, offset)
}
