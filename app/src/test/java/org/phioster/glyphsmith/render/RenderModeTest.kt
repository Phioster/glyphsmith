package org.phioster.glyphsmith.render

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.core.color.ColorDistance

class RenderModeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Every preset on disk was written before this field existed. If the default were anything
     * but the glyph mode, opening the app would silently turn all of them into pixel dithers —
     * the single worst thing this refactor could do.
     */
    @Test
    fun `params without a render mode decode to the glyph mode`() {
        val decoded = json.decodeFromString<AsciiParams>("""{"cellSize":6}""")

        assertEquals(RenderMode.GlyphMatrix, decoded.renderMode)
        assertTrue(decoded.renderMode.isGlyph)
        assertEquals("the rest of the preset must survive too", 6, decoded.cellSize)
    }

    @Test
    fun `params without a colour metric decode to oklab`() {
        val decoded = json.decodeFromString<AsciiParams>("""{"cellSize":6}""")
        assertEquals(ColorDistance.OKLAB, decoded.colorDistance)
    }

    @Test
    fun `the mode survives a round trip`() {
        val params = AsciiParams(renderMode = RenderMode.PurePixel)
        val decoded = json.decodeFromString<AsciiParams>(json.encodeToString(params))

        assertEquals(RenderMode.PurePixel, decoded.renderMode)
        assertTrue("pixel mode is not a glyph mode", !decoded.renderMode.isGlyph)
    }
}
