package org.phioster.glyphsmith.glyph

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import org.phioster.glyphsmith.render.EdgeDetect

/**
 * Which glyph a detected direction is drawn with.
 *
 * Still fed by the shared [EdgeDetect] — a real gradient field rather than a hand-written
 * angle — because the thing worth asserting is that the two halves agree about what an angle
 * means, and that is exactly what the package boundary between them could break.
 */
class EdgeGlyphsTest {

    private val ascii = EdgeGlyphs.setById("ascii") // "-/|\"

    private fun field(cols: Int, rows: Int, value: (Int, Int) -> Float) =
        EdgeDetect.sobel(FloatArray(cols * rows) { value(it % cols, it / cols) }, cols, rows)

    @Test
    fun `a vertical edge is drawn with a vertical glyph`() {
        // Left half black, right half white: the gradient points sideways, so the *edge*
        // runs top to bottom.
        val edges = field(8, 8) { x, _ -> if (x < 4) 0f else 1f }
        val col = 3
        val row = 4
        assertTrue("edge not detected", edges.magnitudeAt(col, row) > 0.5f)
        assertEquals('|', EdgeGlyphs.glyphFor(edges.angleAt(col, row), ascii))
    }

    @Test
    fun `a horizontal edge is drawn with a horizontal glyph`() {
        val edges = field(8, 8) { _, y -> if (y < 4) 0f else 1f }
        val col = 4
        val row = 3
        assertTrue("edge not detected", edges.magnitudeAt(col, row) > 0.5f)
        assertEquals('-', EdgeGlyphs.glyphFor(edges.angleAt(col, row), ascii))
    }

    @Test
    fun `opposite gradients pick the same glyph`() {
        // A line has no direction, only an orientation: 10° and 190° must not differ.
        val forward = EdgeGlyphs.glyphFor(0.3f, ascii)
        val backward = EdgeGlyphs.glyphFor(0.3f + Math.PI.toFloat(), ascii)
        assertEquals(forward, backward)
    }

    @Test
    fun `every edge set offers one glyph per direction`() {
        EdgeGlyphs.sets.forEach { set ->
            assertEquals("${set.id} needs four directions", 4, set.glyphs.length)
        }
    }

    /** The id a preset stores is looked up here, and an unknown one must still render. */
    @Test
    fun `an unknown set falls back to the first`() {
        assertEquals(EdgeGlyphs.sets.first(), EdgeGlyphs.setById("no-such-set"))
    }
}
