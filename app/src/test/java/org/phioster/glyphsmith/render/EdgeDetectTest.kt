package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detector alone. What a direction is *drawn* as is Glyph Art's half and moved with it —
 * see `glyph.EdgeGlyphsTest`, which still measures the same fields this one does.
 */
class EdgeDetectTest {

    private fun field(cols: Int, rows: Int, value: (Int, Int) -> Float) =
        EdgeDetect.sobel(FloatArray(cols * rows) { value(it % cols, it / cols) }, cols, rows)

    @Test
    fun `a flat field has no edges`() {
        val edges = field(8, 8) { _, _ -> 0.5f }
        edges.magnitude.forEach { assertEquals(0f, it, 1e-5f) }
    }

    @Test
    fun `magnitude is normalised so a full step reaches one`() {
        val edges = field(8, 8) { x, _ -> if (x < 4) 0f else 1f }
        edges.magnitude.forEach { assertTrue("magnitude $it above 1", it <= 1f) }
        assertEquals(1f, edges.magnitude.max(), 1e-4f)
    }
}
