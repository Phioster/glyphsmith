package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.Regions
import org.phioster.glyphsmith.core.dither.PatternOptions
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.Dither

class RegionsTest {

    private val cols = 48
    private val rows = 24
    private val levels = 10

    /** A left-to-right ramp: every column a slightly different brightness. */
    private fun ramp() = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }

    private fun quantise(mode: DitherMode, options: PatternOptions = PatternOptions(period = 6)) =
        Regions.quantise(mode, ramp(), cols, rows, levels, options)

    private val regionModes = DitherMode.entries.filter { Dither.isRegion(it) }

    @Test
    fun `every region style fills the grid and stays in range`() {
        regionModes.forEach { mode ->
            val out = quantise(mode)
            assertEquals("$mode returned the wrong size", cols * rows, out.size)
            assertTrue("$mode left the glyph range", out.all { it in 0 until levels })
        }
    }

    /**
     * The property that makes these region styles at all: cells sharing a tile share a glyph.
     *
     * Tested on a horizontal ramp through plain Mosaic, where the tiles are known — if the
     * averaging were skipped, each column inside a tile would differ and this would fail.
     */
    @Test
    fun `a mosaic tile is flat`() {
        val out = quantise(DitherMode.MOSAIC, PatternOptions(period = 6))
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                assertEquals(
                    "tile is not flat at ($x,$y)",
                    out[0],
                    out[y * cols + x],
                )
            }
        }
    }

    /** And that it is genuinely coarser: neighbouring tiles must still differ on a ramp. */
    @Test
    fun `neighbouring tiles differ on a gradient`() {
        val out = quantise(DitherMode.MOSAIC, PatternOptions(period = 6))
        assertTrue("the whole row came out one value", out[0] != out[cols - 1])
    }

    /**
     * Gaps are the point of the grid styles — circles on a grid leave the corners bare, and a
     * cell in the corner must come back as the emptiest glyph rather than joining a tile.
     */
    @Test
    fun `a circle grid leaves its corners empty`() {
        val out = quantise(DitherMode.CIRCLE_GRID, PatternOptions(period = 8, density = 0))
        assertTrue("no cell was left as a gap", out.any { it == 0 })
    }

    @Test
    fun `square mosaic grout widens with its slider`() {
        val tight = quantise(DitherMode.SQUARE_MOSAIC, PatternOptions(period = 8, density = 0))
        val loose = quantise(DitherMode.SQUARE_MOSAIC, PatternOptions(period = 8, density = 100))
        assertTrue(
            "more grout did not leave more cells bare",
            loose.count { it == 0 } > tight.count { it == 0 },
        )
    }

    /** A triangle cut has to actually cut: the two halves of a tile must be separable. */
    @Test
    fun `tri-poly splits a tile in two`() {
        val options = PatternOptions(period = 8)
        val out = quantise(DitherMode.TRI_POLY, options)
        // Opposite corners of the same tile sit on opposite sides of the diagonal.
        assertTrue("the diagonal did not separate the corners", out[0] != out[7 * cols + 7])
    }

    @Test
    fun `low-poly triangle type changes the tessellation`() {
        val shapes = (0..100 step 20).map {
            quantise(DitherMode.LOW_POLY, PatternOptions(period = 8, density = it)).toList()
        }
        assertTrue("every triangle type produced the same picture", shapes.toSet().size > 1)
    }

    @Test
    fun `penta-poly splits the other way when asked`() {
        val horizontal = quantise(DitherMode.PENTA_POLY, PatternOptions(period = 8, density = 0))
        val vertical = quantise(DitherMode.PENTA_POLY, PatternOptions(period = 8, density = 100))
        assertTrue("the split direction did nothing", !horizontal.contentEquals(vertical))
    }

    /** Camo is a Voronoi, so its blobs must not line up into the grid that seeded them. */
    @Test
    fun `camo does not come out as a plain grid`() {
        val camo = quantise(DitherMode.CAMO, PatternOptions(period = 6))
        val mosaic = quantise(DitherMode.MOSAIC, PatternOptions(period = 6))
        assertTrue("camo collapsed into its lattice", !camo.contentEquals(mosaic))
    }

    @Test
    fun `region styles are deterministic`() {
        regionModes.forEach { mode ->
            assertTrue("$mode is not deterministic", quantise(mode).contentEquals(quantise(mode)))
        }
    }

    /** A rotated pattern makes tile coordinates go negative; the keys must survive that. */
    @Test
    fun `rotation does not merge distinct regions`() {
        val straight = quantise(DitherMode.MOSAIC, PatternOptions(period = 6, angle = 0))
        val turned = quantise(DitherMode.MOSAIC, PatternOptions(period = 6, angle = 30))
        assertTrue("rotation did nothing", !straight.contentEquals(turned))
        // If packing collided, whole swathes would share one average and flatten out.
        assertTrue("the turned grid collapsed", turned.toSet().size > 2)
    }
}
