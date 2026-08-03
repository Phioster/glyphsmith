package org.phioster.glyphsmith.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.glyph.GlyphGrid
import org.phioster.glyphsmith.render.RenderSettings
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Only the text mode is covered here: the outline mode needs a real Typeface to measure and
 * flatten glyphs, which a JVM unit test has no way to provide. What is testable is the
 * document these tests actually check — that it parses, that every glyph survives, and that
 * the characters an ASCII ramp is full of don't break the XML.
 */
class SvgExporterTest {

    private val geometry = SvgExporter.Geometry(
        width = 100,
        height = 60,
        originX = 0f,
        originY = 0f,
        cellWidth = 10,
        cellHeight = 20,
        baseline = 16f,
        fontSize = 20,
    )

    private fun artOf(vararg rows: String, colors: IntArray? = null): GlyphGrid {
        val cols = rows[0].length
        val glyphs = CharArray(cols * rows.size)
        rows.forEachIndexed { r, line ->
            require(line.length == cols) { "ragged grid" }
            line.forEachIndexed { c, ch -> glyphs[r * cols + c] = ch }
        }
        return GlyphGrid(cols, rows.size, glyphs, colors)
    }

    private fun parse(svg: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(svg.byteInputStream())

    @Test
    fun `the output is well-formed xml with the right canvas`() {
        val svg = SvgExporter.buildText(artOf("ab", "cd"), RenderSettings(), geometry)
        val root = parse(svg).documentElement

        assertEquals("svg", root.tagName)
        assertEquals("100", root.getAttribute("width"))
        assertEquals("60", root.getAttribute("height"))
        assertEquals("0 0 100 60", root.getAttribute("viewBox"))
    }

    @Test
    fun `every non-space glyph reaches the document exactly once`() {
        val svg = SvgExporter.buildText(artOf("ab c", "d ef"), RenderSettings(), geometry)
        val texts = parse(svg).getElementsByTagName("text")

        val written = buildString {
            for (i in 0 until texts.length) append(texts.item(i).textContent)
        }
        assertEquals("abcdef", written)
    }

    /**
     * A `<text>` positions its glyphs from an explicit x-list, so the count of coordinates
     * has to match the count of characters — one missing entry silently shifts the rest of
     * the run out of its cells.
     */
    @Test
    fun `each run carries one x coordinate per glyph`() {
        val svg = SvgExporter.buildText(artOf("abc", "d f"), RenderSettings(), geometry)
        val texts = parse(svg).getElementsByTagName("text")

        assertTrue("no text runs emitted", texts.length > 0)
        for (i in 0 until texts.length) {
            val node = texts.item(i)
            val xs = node.attributes.getNamedItem("x").nodeValue.trim().split(" ")
            assertEquals("run '${node.textContent}'", node.textContent.length, xs.size)
        }
    }

    @Test
    fun `spaces are skipped rather than emitted`() {
        val svg = SvgExporter.buildText(artOf("a b"), RenderSettings(), geometry)
        val texts = parse(svg).getElementsByTagName("text")
        // A space splits the row into two runs; it must not become a glyph of its own.
        assertEquals(2, texts.length)
        assertEquals("a", texts.item(0).textContent)
        assertEquals("b", texts.item(1).textContent)
    }

    /** `&`, `<` and `>` all appear in the standard ASCII ramps, so this is not hypothetical. */
    @Test
    fun `xml metacharacters from the ramp are escaped`() {
        val svg = SvgExporter.buildText(artOf("&<>"), RenderSettings(), geometry)

        assertTrue(svg.contains("&amp;"))
        assertTrue(svg.contains("&lt;"))
        assertTrue(svg.contains("&gt;"))
        // Parsing is the real proof: a raw '<' would make this throw.
        assertEquals("&<>", parse(svg).getElementsByTagName("text").item(0).textContent)
    }

    @Test
    fun `a transparent background emits no backing rect`() {
        val opaque = SvgExporter.buildText(artOf("ab"), RenderSettings(), geometry)
        val clear = SvgExporter.buildText(
            artOf("ab"),
            RenderSettings(transparentBackground = true),
            geometry,
        )

        assertEquals(1, parse(opaque).getElementsByTagName("rect").length)
        assertEquals(0, parse(clear).getElementsByTagName("rect").length)
    }

    @Test
    fun `neighbouring cells of different colours become separate runs`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val art = artOf("ab", colors = intArrayOf(red, blue))
        val texts = parse(SvgExporter.buildText(art, RenderSettings(), geometry))
            .getElementsByTagName("text")

        assertEquals(2, texts.length)
        assertEquals("#FF0000", texts.item(0).attributes.getNamedItem("fill").nodeValue)
        assertEquals("#0000FF", texts.item(1).attributes.getNamedItem("fill").nodeValue)
    }

    @Test
    fun `cells sharing a colour stay in one run`() {
        val red = 0xFFFF0000.toInt()
        val art = artOf("abc", colors = intArrayOf(red, red, red))
        val texts = parse(SvgExporter.buildText(art, RenderSettings(), geometry))
            .getElementsByTagName("text")

        assertEquals(1, texts.length)
        assertEquals("abc", texts.item(0).textContent)
    }

    @Test
    fun `an all-space grid still produces a valid empty document`() {
        val svg = SvgExporter.buildText(artOf("   ", "   "), RenderSettings(), geometry)
        assertEquals(0, parse(svg).getElementsByTagName("text").length)
        assertFalse(svg.contains("NaN"))
    }
}
