package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowVectorTest {

    private fun marks(glyphs: String, colours: List<Int>) = FlowMarks(
        cols = 100, rows = 50, count = glyphs.length,
        x = FloatArray(glyphs.length) { 10f + it * 10f },
        y = FloatArray(glyphs.length) { 20f },
        angle = FloatArray(glyphs.length) { if (it == 1) 0.5f else 0f },
        glyph = glyphs.toCharArray(),
        half = FloatArray(glyphs.length) { 3f },
        color = colours.toIntArray(),
        size = 7f,
    )

    @Test
    fun `characters become turned text, grouped by colour, and markup is escaped`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val (svg, count) = FlowVector.svg(marks("#o<&", listOf(red, red, blue, blue)), RenderSettings())

        assertEquals(4, count)
        assertEquals(4, Regex("<text ").findAll(svg).count())
        assertEquals(1, Regex("<g fill=\"#ff0000\">").findAll(svg).count())
        assertEquals(1, Regex("<g fill=\"#0000ff\">").findAll(svg).count())
        // Only the second mark is turned: 0.5 rad is 28.65 degrees, about its own centre.
        assertEquals(1, Regex("rotate\\(").findAll(svg).count())
        assertTrue(svg.contains("rotate(28.65 20.0 20.0)"))
        assertTrue(svg.contains(">&lt;</text>") && svg.contains(">&amp;</text>"))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `hairs of one colour share a single path`() {
        val grey = 0xFF808080.toInt()
        val hairs = String(CharArray(3) { FlowMarks.HAIR })
        val (svg, count) = FlowVector.svg(marks(hairs, listOf(grey, grey, grey)), RenderSettings())

        assertEquals(3, count)
        assertEquals(1, Regex("<path ").findAll(svg).count())
        assertEquals(3, Regex("M[0-9.]+ [0-9.]+L").findAll(svg).count())
        assertEquals(0, Regex("<text ").findAll(svg).count())
    }
}
