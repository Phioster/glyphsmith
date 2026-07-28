package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsciiEngineTest {

    private val params = AsciiParams(charSetId = "ascii-standard-10", depth = 10, cellSize = 8)
    private val ramp = CharacterSets.byId("ascii-standard-10").glyphs

    private fun solid(color: Int, width: Int, height: Int) = IntArray(width * height) { color }

    @Test
    fun `grid size follows cell size and the glyph aspect`() {
        val art = AsciiEngine.convert(solid(BLACK, 32, 32), 32, 32, params, cellAspect = 2f)
        // 8px cells horizontally, 16px vertically.
        assertEquals(4, art.cols)
        assertEquals(2, art.rows)
    }

    @Test
    fun `black maps to the emptiest glyph and white to the densest`() {
        val dark = AsciiEngine.convert(solid(BLACK, 16, 32), 16, 32, params)
        val light = AsciiEngine.convert(solid(WHITE, 16, 32), 16, 32, params)
        assertEquals(ramp.first(), dark.glyphAt(0, 0))
        assertEquals(ramp.last(), light.glyphAt(0, 0))
    }

    @Test
    fun `invert swaps which end of the ramp black lands on`() {
        val art = AsciiEngine.convert(solid(BLACK, 16, 32), 16, 32, params.copy(invert = true))
        assertEquals(ramp.last(), art.glyphAt(0, 0))
    }

    @Test
    fun `text export has one line per row`() {
        val art = AsciiEngine.convert(solid(WHITE, 64, 64), 64, 64, params)
        val lines = art.toText().lines()
        assertEquals(art.rows, lines.size)
        lines.forEach { assertEquals(art.cols, it.length) }
    }

    @Test
    fun `single colour mode carries no per-cell colours`() {
        val art = AsciiEngine.convert(solid(WHITE, 16, 32), 16, 32, params.copy(colorMode = ColorMode.SINGLE))
        assertNull(art.colors)
    }

    @Test
    fun `source colour mode averages the cell`() {
        val art = AsciiEngine.convert(solid(RED, 16, 32), 16, 32, params.copy(colorMode = ColorMode.SOURCE))
        assertNotNull(art.colors)
        assertTrue(RED == art.colorAt(0, 0))
    }

    @Test
    fun `offset wraps instead of clamping`() {
        assertEquals(0, AsciiEngine.mapToRamp(0f, 10, 0))
        assertEquals(1, AsciiEngine.mapToRamp(0f, 10, 1))
        // Past the end it folds back to the start — the point of the control.
        assertEquals(0, AsciiEngine.mapToRamp(1f, 10, 1))
        assertEquals(9, AsciiEngine.mapToRamp(0f, 10, -1))
    }

    @Test
    fun `tone curve stays inside 0 and 1`() {
        val hot = AsciiEngine.toneCurve(1f, params.copy(brightness = 1f, contrast = 3f))
        val cold = AsciiEngine.toneCurve(0f, params.copy(brightness = -1f, contrast = 3f))
        assertEquals(1f, hot, 1e-4f)
        assertEquals(0f, cold, 1e-4f)
    }

    @Test
    fun `contrast pushes mid grey outwards but leaves the pivot alone`() {
        val pivot = AsciiEngine.toneCurve(0.5f, params.copy(contrast = 2.5f))
        assertEquals(0.5f, pivot, 1e-4f)
        assertTrue(AsciiEngine.toneCurve(0.6f, params.copy(contrast = 2f)) > 0.6f)
    }

    @Test
    fun `luminance is weighted for green`() {
        assertTrue(AsciiEngine.luminance(0, 255, 0) > AsciiEngine.luminance(255, 0, 0))
        assertEquals(1f, AsciiEngine.luminance(255, 255, 255), 1e-4f)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
    }
}
