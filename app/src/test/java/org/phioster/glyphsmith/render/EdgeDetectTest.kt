package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeDetectTest {

    private val ascii = EdgeDetect.setById("ascii") // "-/|\"

    private fun field(cols: Int, rows: Int, value: (Int, Int) -> Float) =
        EdgeDetect.sobel(FloatArray(cols * rows) { value(it % cols, it / cols) }, cols, rows)

    @Test
    fun `a flat field has no edges`() {
        val edges = field(8, 8) { _, _ -> 0.5f }
        edges.magnitude.forEach { assertEquals(0f, it, 1e-5f) }
    }

    @Test
    fun `a vertical edge is drawn with a vertical glyph`() {
        // Left half black, right half white: the gradient points sideways, so the *edge*
        // runs top to bottom.
        val edges = field(8, 8) { x, _ -> if (x < 4) 0f else 1f }
        val col = 3
        val row = 4
        assertTrue("edge not detected", edges.magnitudeAt(col, row) > 0.5f)
        assertEquals('|', EdgeDetect.glyphFor(edges.angleAt(col, row), ascii))
    }

    @Test
    fun `a horizontal edge is drawn with a horizontal glyph`() {
        val edges = field(8, 8) { _, y -> if (y < 4) 0f else 1f }
        val col = 4
        val row = 3
        assertTrue("edge not detected", edges.magnitudeAt(col, row) > 0.5f)
        assertEquals('-', EdgeDetect.glyphFor(edges.angleAt(col, row), ascii))
    }

    @Test
    fun `magnitude is normalised so a full step reaches one`() {
        val edges = field(8, 8) { x, _ -> if (x < 4) 0f else 1f }
        edges.magnitude.forEach { assertTrue("magnitude $it above 1", it <= 1f) }
        assertEquals(1f, edges.magnitude.max(), 1e-4f)
    }

    @Test
    fun `opposite gradients pick the same glyph`() {
        // A line has no direction, only an orientation: 10° and 190° must not differ.
        val forward = EdgeDetect.glyphFor(0.3f, ascii)
        val backward = EdgeDetect.glyphFor(0.3f + Math.PI.toFloat(), ascii)
        assertEquals(forward, backward)
    }

    @Test
    fun `every edge set offers one glyph per direction`() {
        EdgeDetect.sets.forEach { set ->
            assertEquals("${set.id} needs four directions", 4, set.glyphs.length)
        }
    }
}
