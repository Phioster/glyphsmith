package org.phioster.glyphsmith.ascii

import org.phioster.glyphsmith.render.CellSampler
import org.phioster.glyphsmith.render.IndexGrid
import org.phioster.glyphsmith.render.QuantisePass
import org.phioster.glyphsmith.core.color.Palettes

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
 * The sampling and the dither are not here any more — they are
 * [org.phioster.glyphsmith.render.CellSampler] and
 * [org.phioster.glyphsmith.render.QuantisePass], shared with the pixel path, because neither
 * of them ever needed to know about characters. What is left is the part that is genuinely
 * about glyphs: turning a quantised level into a character, and letting a strong edge override
 * the brightness-matched choice with a directional one.
 *
 * Deliberately free of Android types, so the whole mapping is unit-testable on the JVM.
 */
object AsciiEngine {

    /**
     * Monospace glyph cells are taller than they are wide; sampling with the same aspect is
     * what keeps the output from looking vertically stretched.
     */
    const val DEFAULT_CELL_ASPECT = 2.0f

    fun convert(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: AsciiParams,
        cellAspect: Float = DEFAULT_CELL_ASPECT,
    ): AsciiArt {
        val ramp = params.effectiveRamp().ifEmpty { " " }
        val grid = CellSampler.sample(
            pixels = pixels,
            width = width,
            height = height,
            params = params,
            cellWidth = params.cellSize.coerceAtLeast(1),
            cellHeight = CellSampler.cellHeightFor(params.cellSize, cellAspect),
        )
        // The ramp length is what the dither quantises to. That is the only thing the glyph
        // mode contributes to the algorithm — the pixel mode passes its palette size instead.
        return mapToGlyphs(QuantisePass.run(params, grid, ramp.length), params, ramp)
    }

    /**
     * A quantised grid read as characters: index into the ramp, then let an edge override it.
     */
    fun mapToGlyphs(grid: IndexGrid, params: AsciiParams, ramp: String): AsciiArt {
        val levels = grid.levels
        val cols = grid.cols
        val rows = grid.rows
        val glyphs = CharArray(cols * rows)
        val colors = if (grid.colors != null) IntArray(cols * rows) else null
        val palette = params.renderPalette()

        val edges = grid.edges
        val edgeSet = EdgeDetect.setById(params.edgeSetId)
        val edgeThreshold = params.edgeThreshold / 100f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cell = row * cols + col
                var glyph = ramp[applyOffset(grid.indices[cell], levels, params.offset)]
                if (edges != null) {
                    val magnitude = edges.magnitudeAt(col, row)
                    if (magnitude >= edgeThreshold) {
                        glyph = EdgeDetect.glyphFor(edges.angleAt(col, row), edgeSet)
                    } else if (params.edgeOnly) {
                        glyph = ' '
                    }
                }
                glyphs[cell] = glyph

                if (colors != null && grid.colors != null) {
                    colors[cell] = when (params.colorMode) {
                        ColorMode.SOURCE -> grid.colors[cell]
                        // Sampled from the *undithered* luminance on purpose: colour that
                        // flickers in step with the glyphs reads as twice the noise.
                        ColorMode.PALETTE -> Palettes.sample(palette, grid.base[cell])
                        ColorMode.SINGLE -> params.inkColor
                    }
                }
            }
        }
        return AsciiArt(cols, rows, glyphs, colors)
    }

    /** Rec. 709 luminance, normalised to 0..1. */
    fun luminance(r: Int, g: Int, b: Int): Float = CellSampler.luminance(r, g, b)

    /** Gamma, then contrast around mid grey, then brightness, then midtones and highlights. */
    fun toneCurve(value: Float, params: AsciiParams): Float = CellSampler.toneCurve(value, params)

    /**
     * Luminance to a ramp index, *before* any offset. Dithering needs this separately: the
     * error is the difference between the requested tone and the tone actually reproduced,
     * which only means anything on the un-rotated ramp.
     */
    fun quantize(luma: Float, rampLength: Int): Int = QuantisePass.quantize(luma, rampLength)

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
