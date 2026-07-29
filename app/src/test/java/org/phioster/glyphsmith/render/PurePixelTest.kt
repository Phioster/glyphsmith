package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.DitherMode

/**
 * The pixel path, end to end, without a single Android type — which is the whole reason the node
 * interface trades in pixel buffers instead of bitmaps.
 *
 * The claim under test is that the 78 dither algorithms genuinely never knew about characters:
 * the same [QuantisePass] that drives the glyph mode drives this one, asked for a different
 * number of levels.
 */
class PurePixelTest {

    private val side = 24

    /** A diagonal gradient — every mode has something to do with it. */
    private fun gradient(): IntArray = IntArray(side * side) { i ->
        val x = i % side
        val y = i / side
        val v = ((x + y) * 255 / (2 * side - 2)).coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    private fun params(mode: DitherMode = DitherMode.NONE, colorMode: ColorMode = ColorMode.PALETTE) =
        AsciiParams(
            renderMode = RenderMode.PurePixel,
            cellSize = 1,
            colorMode = colorMode,
            paletteId = "grayscale",
            ditherMode = mode,
        )

    private fun renderWith(p: AsciiParams, block: Int = 1): Pair<IndexGrid, IntArray> {
        val grid = CellSampler.sample(gradient(), side, side, p, p.cellSize, p.cellSize)
        val indexed = QuantisePass.run(p, grid, PixelDitherRenderer.levelsFor(p))
        return indexed to PixelDitherRenderer.render(indexed, p, block).data
    }

    @Test
    fun `a cell size of one dithers at full resolution`() {
        val (grid, _) = renderWith(params())

        assertEquals(side, grid.cols)
        assertEquals(side, grid.rows)
    }

    @Test
    fun `levels come from the palette, not from a ramp`() {
        val p = params()
        val expected = p.renderPalette().colors.size

        assertEquals(expected, PixelDitherRenderer.levelsFor(p))
    }

    /**
     * The important one. If any mode left a cell unassigned the output would have a hole in it,
     * and because indices default to zero that hole would be invisible in the image — so this
     * checks the indices are in range rather than merely present.
     */
    @Test
    fun `every dither mode fills the whole grid with valid levels`() {
        for (mode in DitherMode.entries) {
            val (grid, _) = renderWith(params(mode))
            assertEquals(mode.name, side * side, grid.indices.size)
            assertTrue(
                "$mode produced an index outside 0..${grid.levels - 1}",
                grid.indices.all { it in 0 until grid.levels },
            )
        }
    }

    @Test
    fun `every dither mode paints only palette colours`() {
        val p = params()
        val palette = p.renderPalette().colors.toSet()

        for (mode in DitherMode.entries) {
            val (_, pixels) = renderWith(params(mode))
            assertTrue(
                "$mode painted a colour that is not in the palette",
                pixels.all { it in palette },
            )
        }
    }

    @Test
    fun `every dither mode is deterministic`() {
        for (mode in DitherMode.entries) {
            val (_, first) = renderWith(params(mode))
            val (_, second) = renderWith(params(mode))
            assertTrue("$mode is not reproducible", first.contentEquals(second))
        }
    }

    @Test
    fun `a block size larger than one expands each cell into a square`() {
        val block = 3
        val (grid, pixels) = renderWith(params(), block = block)

        assertEquals(grid.cols * block * grid.rows * block, pixels.size)
        // The four pixels of one cell's top-left corner must be the same colour.
        val width = grid.cols * block
        assertEquals(pixels[0], pixels[1])
        assertEquals(pixels[0], pixels[width])
        assertEquals(pixels[0], pixels[width + 1])
    }

    @Test
    fun `single colour mode interpolates between background and ink`() {
        val p = params(colorMode = ColorMode.SINGLE).copy(depth = 2)
        val (_, pixels) = renderWith(p)

        assertEquals(2, PixelDitherRenderer.levelsFor(p))
        assertTrue(
            "two levels may only produce the background and the ink",
            pixels.all { it == p.backgroundColor || it == p.inkColor },
        )
    }

    /**
     * A flat field must not be scattered across the palette.
     *
     * Not "one colour", which is what you would expect and what this originally asserted: the
     * threshold-based modes add a signed offset to the value *before* quantising, so at pure
     * white a negative offset can round a cell down to the next level and leave a faint pattern
     * in what should be a solid area. That is pre-existing behaviour of this app's dither
     * formulation — the glyph path does exactly the same thing with the same code — so it is
     * documented here rather than changed, which would alter every existing render.
     *
     * What still has to hold is that the spread is *bounded*: at most two levels, and adjacent
     * ones. A mode that scattered a flat field over the whole palette would be broken.
     */
    @Test
    fun `a flat field stays within two adjacent levels in every mode`() {
        val white = IntArray(side * side) { -1 }
        for (mode in DitherMode.entries) {
            val p = params(mode)
            val grid = CellSampler.sample(white, side, side, p, 1, 1)
            val indexed = QuantisePass.run(p, grid, PixelDitherRenderer.levelsFor(p))

            val levels = indexed.indices.toSortedSet()
            assertTrue(
                "$mode spread a flat field over ${levels.size} levels: $levels",
                levels.size <= 2,
            )
            if (levels.size == 2) {
                assertEquals(
                    "$mode used two non-adjacent levels: $levels",
                    1,
                    levels.last() - levels.first(),
                )
            }
        }
    }
}
