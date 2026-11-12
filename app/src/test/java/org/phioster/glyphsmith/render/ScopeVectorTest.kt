package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeVectorTest {

    private fun grid(cols: Int, rows: Int, tone: (Int, Int) -> Float) =
        CellGrid(cols, rows, FloatArray(cols * rows) { tone(it % cols, it / cols) }, null)

    private fun settings(scope: OscilloscopeParams = OscilloscopeParams()) =
        RenderSettings(renderMode = RenderMode.Oscilloscope, oscilloscope = scope)

    @Test
    fun `a flat picture is one unbroken line per trace`() {
        val flat = grid(768, 200) { _, _ -> 0.5f }
        val runs = Oscilloscope.runs(flat, settings())

        // Spacing 9 over 200 rows, the topmost left out as on screen.
        assertEquals(22, runs.size)
        runs.forEach { run -> assertEquals("a trace runs the whole width", 768 * 2, run.size) }
    }

    @Test
    fun `a trace behind a nearer one is cut where it is hidden`() {
        // A white hill in the middle of the lower half: the traces behind it are interrupted.
        val hill = grid(768, 300) { x, y -> if (x in 300..460 && y in 190..250) 1f else 0f }
        val tall = OscilloscopeParams(height = 70)

        val hidden = Oscilloscope.runs(hill, settings(tall))
        val all = Oscilloscope.runs(hill, settings(tall.copy(hidden = false)))
        assertTrue("${hidden.size} stretches with hiding, ${all.size} without", hidden.size > all.size)
        assertTrue(hidden.sumOf { it.size } < all.sumOf { it.size })
    }

    @Test
    fun `the file is well-formed, in the tube's colour, and drops what a straight line does not need`() {
        val flat = grid(768, 200) { _, _ -> 0.5f }
        val (svg, count) = ScopeVector.svg(flat, settings(OscilloscopeParams(phosphor = Phosphor.AMBER)))

        assertEquals(22, count)
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        assertTrue("amber", svg.contains("stroke=\"#ffb03c\""))
        assertEquals(count, Regex("<path ").findAll(svg).count())
        // A straight trace needs its two ends, not 768 points.
        assertTrue("${svg.length} characters for 22 straight lines", svg.length < 4000)
    }
}
