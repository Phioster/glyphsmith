package org.phioster.glyphsmith.render

import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.core.color.PaletteQuantizer
import org.phioster.glyphsmith.core.image.Pixels

/**
 * The pixel path: a quantised grid painted as colours instead of characters.
 *
 * Cells are square here, unlike the glyph path — there is no font metric to compensate for, so
 * a cell of one source pixel dithers at full resolution and anything larger is a visible pixel
 * block. That single difference is why this needs its own entry point rather than a flag on the
 * glyph one.
 *
 * How a level becomes a colour depends on the colour mode:
 *
 * - [ColorMode.PALETTE] indexes the palette directly. Because the palette is luminance-ordered,
 *   a dither over `palette.size` levels *is* a palette-reduction dither, and every one of the 78
 *   algorithms works on it unchanged.
 * - [ColorMode.SINGLE] interpolates between the background and the ink colour, which at two
 *   levels is classic 1-bit dithering.
 * - [ColorMode.SOURCE] keeps each cell's own colour and reduces it to the palette through
 *   [ColorDiffusionPass], which carries the error per channel. This is the mode the colour metric
 *   actually decides: the same image reduced through [ColorDistance.EUCLIDEAN] and
 *   [ColorDistance.OKLAB] picks visibly different entries.
 */
object PixelDitherRenderer {

    /** Fewest levels a dither can say anything with. */
    private const val MIN_LEVELS = 2

    /**
     * How many levels the dither should quantise to. This is the pixel mode's answer to the
     * ramp length — the number the glyph mode passes instead.
     */
    fun levelsFor(params: RenderSettings): Int = when (params.colorMode) {
        ColorMode.SINGLE -> params.depth.coerceAtLeast(MIN_LEVELS)
        ColorMode.PALETTE, ColorMode.SOURCE ->
            params.renderPalette().colors.size.coerceAtLeast(MIN_LEVELS)
    }

    fun render(grid: IndexGrid, params: RenderSettings, block: Int): Pixels {
        val blockSize = block.coerceAtLeast(1)
        val width = grid.cols * blockSize
        val height = grid.rows * blockSize
        val out = IntArray(width * height)

        val colors = colorsFor(grid, params)

        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                val colour = colors[row * grid.cols + col]
                val yStart = row * blockSize
                val xStart = col * blockSize
                for (y in yStart until yStart + blockSize) {
                    val rowOffset = y * width
                    for (x in xStart until xStart + blockSize) {
                        out[rowOffset + x] = colour
                    }
                }
            }
        }
        return Pixels(out, width, height)
    }

    /** One colour per cell, before it is expanded into blocks. */
    private fun colorsFor(grid: IndexGrid, params: RenderSettings): IntArray {
        val palette = params.renderPalette().colors
        val out = IntArray(grid.cols * grid.rows)
        val levels = grid.levels

        when (params.colorMode) {
            ColorMode.SINGLE -> {
                val background = params.backgroundColor
                val ink = params.inkColor
                for (i in out.indices) {
                    val t = if (levels <= 1) 1f else grid.indices[i].toFloat() / (levels - 1)
                    out[i] = lerpColor(background, ink, t)
                }
            }

            ColorMode.PALETTE -> {
                if (palette.isEmpty()) {
                    out.fill(params.inkColor)
                } else {
                    for (i in out.indices) {
                        out[i] = palette[grid.indices[i].coerceIn(0, palette.lastIndex)]
                    }
                }
            }

            ColorMode.SOURCE -> {
                if (grid.colors == null || palette.isEmpty()) {
                    out.fill(params.inkColor)
                } else {
                    // A real colour dither: the error is carried per channel, which is the only
                    // way a coloured palette can hold a tone it has no exact entry for.
                    val quantizer = PaletteQuantizer(palette.toIntArray(), params.colorDistance)
                    return ColorDiffusionPass.run(params, grid, quantizer)
                }
            }
        }
        return out
    }

    /** Straight channel interpolation, opaque. Alpha is decided by the caller, not the ramp. */
    private fun lerpColor(from: Int, to: Int, frac: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * frac).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
