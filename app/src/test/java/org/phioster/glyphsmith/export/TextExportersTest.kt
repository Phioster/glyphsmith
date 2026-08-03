package org.phioster.glyphsmith.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.glyph.GlyphGrid
import org.phioster.glyphsmith.render.RenderSettings

class TextExportersTest {

    private fun artOf(vararg rows: String, colors: IntArray? = null): GlyphGrid {
        val cols = rows[0].length
        val glyphs = CharArray(cols * rows.size)
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> glyphs[r * cols + c] = ch }
        }
        return GlyphGrid(cols, rows.size, glyphs, colors)
    }

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val esc = "\u001B"

    @Test
    fun `html keeps every glyph and every line`() {
        val html = TextExporters.html(artOf("ab", "cd"), RenderSettings())
        val body = html.substringAfter("<pre>").substringBefore("</pre>")

        assertEquals(2, body.lines().size)
        assertTrue(body.contains("ab"))
        assertTrue(body.contains("cd"))
    }

    /** The ramps are full of `&`, `<` and `>`; unescaped they would break the page. */
    @Test
    fun `html escapes the characters that would close a tag`() {
        val html = TextExporters.html(artOf("&<>"), RenderSettings())
        val body = html.substringAfter("<pre>").substringBefore("</pre>")

        assertTrue(body.contains("&amp;"))
        assertTrue(body.contains("&lt;"))
        assertTrue(body.contains("&gt;"))
        // The raw characters must not survive anywhere in the run's text.
        assertFalse(body.substringAfter("\">").substringBefore("</span>").contains('<'))
    }

    /**
     * A span per glyph would be a megabyte of markup for a mostly flat image, so equal
     * neighbours have to share one. If this ever produces one span per character the export
     * still looks right and the file is unusable.
     */
    @Test
    fun `html merges neighbouring glyphs of the same colour`() {
        val same = TextExporters.html(artOf("abc", colors = intArrayOf(red, red, red)), RenderSettings())
        assertEquals(1, same.split("<span").size - 1)

        val mixed = TextExporters.html(artOf("abc", colors = intArrayOf(red, blue, red)), RenderSettings())
        assertEquals(3, mixed.split("<span").size - 1)
    }

    @Test
    fun `html uses the background colour, or none when transparent`() {
        val opaque = TextExporters.html(artOf("ab"), RenderSettings(backgroundColor = 0xFF102030.toInt()))
        assertTrue(opaque.contains("background:#102030"))

        val clear = TextExporters.html(artOf("ab"), RenderSettings(transparentBackground = true))
        assertTrue(clear.contains("background:transparent"))
    }

    @Test
    fun `ansi writes real escape bytes, not the text of them`() {
        val ansi = TextExporters.ansi(artOf("ab"), RenderSettings())
        assertTrue("no escape character in the output", ansi.contains(esc))
        assertFalse("the escape was written as literal text", ansi.contains("\\u001B"))
    }

    /**
     * Without a reset at the end of a line the terminal keeps painting that colour over
     * everything after the file, the shell prompt included.
     */
    @Test
    fun `every ansi line ends with a reset`() {
        val ansi = TextExporters.ansi(artOf("ab", "cd"), RenderSettings())
        ansi.lines().forEach { line ->
            assertTrue("a line does not reset: $line", line.endsWith("$esc[0m"))
        }
    }

    @Test
    fun `ansi repeats a colour code only when the colour changes`() {
        val flat = TextExporters.ansi(artOf("abc", colors = intArrayOf(red, red, red)), RenderSettings())
        val mixed = TextExporters.ansi(artOf("abc", colors = intArrayOf(red, blue, red)), RenderSettings())

        assertEquals(1, flat.split("$esc[38;2;").size - 1)
        assertEquals(3, mixed.split("$esc[38;2;").size - 1)
    }

    @Test
    fun `ansi carries the glyphs through unchanged`() {
        val ansi = TextExporters.ansi(artOf("a&<b"), RenderSettings())
        // Nothing is escaped here — a terminal wants the characters themselves.
        assertTrue(ansi.contains("a&<b"))
    }

    @Test
    fun `both formats survive an all-space grid`() {
        val blank = artOf("   ", "   ")
        assertTrue(TextExporters.html(blank, RenderSettings()).contains("</pre>"))
        assertEquals(2, TextExporters.ansi(blank, RenderSettings()).lines().size)
    }
}
