package org.phioster.glyphsmith.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.RenderSettings

class GlyphEngineTest {

    private val params = RenderSettings(charSetId = "ascii-standard-10", depth = 10, cellSize = 8)
    private val ramp = CharacterSets.byId("ascii-standard-10").glyphs

    private fun solid(color: Int, width: Int, height: Int) = IntArray(width * height) { color }

    @Test
    fun `grid size follows cell size and the glyph aspect`() {
        val art = GlyphEngine.convert(solid(BLACK, 32, 32), 32, 32, params, cellAspect = 2f)
        // 8px cells horizontally, 16px vertically.
        assertEquals(4, art.cols)
        assertEquals(2, art.rows)
    }

    @Test
    fun `black maps to the emptiest glyph and white to the densest`() {
        val dark = GlyphEngine.convert(solid(BLACK, 16, 32), 16, 32, params)
        val light = GlyphEngine.convert(solid(WHITE, 16, 32), 16, 32, params)
        assertEquals(ramp.first(), dark.glyphAt(0, 0))
        assertEquals(ramp.last(), light.glyphAt(0, 0))
    }

    @Test
    fun `invert swaps which end of the ramp black lands on`() {
        val art = GlyphEngine.convert(solid(BLACK, 16, 32), 16, 32, params.copy(invert = true))
        assertEquals(ramp.last(), art.glyphAt(0, 0))
    }

    @Test
    fun `text export has one line per row`() {
        val art = GlyphEngine.convert(solid(WHITE, 64, 64), 64, 64, params)
        val lines = art.toText().lines()
        assertEquals(art.rows, lines.size)
        lines.forEach { assertEquals(art.cols, it.length) }
    }

    @Test
    fun `single colour mode carries no per-cell colours`() {
        val art = GlyphEngine.convert(solid(WHITE, 16, 32), 16, 32, params.copy(colorMode = ColorMode.SINGLE))
        assertNull(art.colors)
    }

    @Test
    fun `source colour mode averages the cell`() {
        val art = GlyphEngine.convert(solid(RED, 16, 32), 16, 32, params.copy(colorMode = ColorMode.SOURCE))
        assertNotNull(art.colors)
        assertTrue(RED == art.colorAt(0, 0))
    }

    @Test
    fun `offset wraps instead of clamping`() {
        assertEquals(0, GlyphEngine.mapToRamp(0f, 10, 0))
        assertEquals(1, GlyphEngine.mapToRamp(0f, 10, 1))
        // Past the end it folds back to the start — the point of the control.
        assertEquals(0, GlyphEngine.mapToRamp(1f, 10, 1))
        assertEquals(9, GlyphEngine.mapToRamp(0f, 10, -1))
    }

    @Test
    fun `tone curve stays inside 0 and 1`() {
        val hot = GlyphEngine.toneCurve(1f, params.copy(brightness = 1f, contrast = 3f))
        val cold = GlyphEngine.toneCurve(0f, params.copy(brightness = -1f, contrast = 3f))
        assertEquals(1f, hot, 1e-4f)
        assertEquals(0f, cold, 1e-4f)
    }

    @Test
    fun `contrast pushes mid grey outwards but leaves the pivot alone`() {
        val pivot = GlyphEngine.toneCurve(0.5f, params.copy(contrast = 2.5f))
        assertEquals(0.5f, pivot, 1e-4f)
        assertTrue(GlyphEngine.toneCurve(0.6f, params.copy(contrast = 2f)) > 0.6f)
    }

    @Test
    fun `luminance is weighted for green`() {
        assertTrue(GlyphEngine.luminance(0, 255, 0) > GlyphEngine.luminance(255, 0, 0))
        assertEquals(1f, GlyphEngine.luminance(255, 255, 255), 1e-4f)
    }

    /** Left-to-right linear grey ramp — varies only in x, so every row is identical. */
    private fun gradient(width: Int, height: Int) = IntArray(width * height) { i ->
        val value = (i % width) * 255 / (width - 1)
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }

    private val twoLevel = RenderSettings(charSetId = "ascii-standard-10", depth = 2, cellSize = 1)

    @Test
    fun `without dithering a gradient collapses into one repeated row`() {
        val art = GlyphEngine.convert(gradient(64, 64), 64, 64, twoLevel, cellAspect = 1f)
        // Two glyphs, hard threshold: the image becomes two flat bands and every row is
        // the same. This is the banding dithering exists to break.
        assertEquals(1, art.toText().lines().toSet().size)
    }

    @Test
    fun `floyd steinberg breaks the banding without shifting the average`() {
        val dithered = twoLevel.copy(ditherMode = DitherMode.FLOYD_STEINBERG)
        val art = GlyphEngine.convert(gradient(64, 64), 64, 64, dithered, cellAspect = 1f)

        assertTrue(
            "error diffusion produced identical rows",
            art.toText().lines().toSet().size > 1,
        )
        // A linear ramp averages to mid grey, so about half the cells should be the dense
        // glyph — diffusion redistributes error, it must not brighten or darken the image.
        val dense = art.glyphs.count { it == '@' }.toFloat() / art.glyphs.size
        assertEquals(0.5f, dense, 0.08f)
    }

    @Test
    fun `ordered dithering also breaks the banding`() {
        val bayer = twoLevel.copy(ditherMode = DitherMode.BAYER_4)
        val art = GlyphEngine.convert(gradient(64, 64), 64, 64, bayer, cellAspect = 1f)
        assertTrue(art.toText().lines().toSet().size > 1)
    }

    /**
     * A gradient varies only in x, so an undithered render repeats one row. A modulation
     * pattern is a function of *both* axes, so it must break that — if a mode leaves the
     * rows identical it is not actually reaching the glyph choice.
     */
    @Test
    fun `every modulation mode varies the grid vertically`() {
        listOf(
            DitherMode.MOD_LINES,
            DitherMode.MOD_WAVE,
            DitherMode.MOD_RINGS,
            DitherMode.MOD_ORB,
            DitherMode.BEEHIVE,
        ).forEach { mode ->
            val params = twoLevel.copy(ditherMode = mode, modScale = 6, modAngle = 30)
            val art = GlyphEngine.convert(gradient(64, 64), 64, 64, params, cellAspect = 1f)
            assertTrue("$mode left every row identical", art.toText().lines().toSet().size > 1)
        }
    }

    @Test
    fun `pattern scale changes a modulated render but leaves an undithered one alone`() {
        val base = twoLevel.copy(ditherMode = DitherMode.MOD_ORB, modScale = 4)
        val small = GlyphEngine.convert(gradient(64, 64), 64, 64, base, cellAspect = 1f)
        val large = GlyphEngine.convert(
            gradient(64, 64), 64, 64, base.copy(ditherScale = 400), cellAspect = 1f,
        )
        assertTrue("pattern scale did nothing", small.toText() != large.toText())

        // With no dithering there is no pattern to scale, so the control must be inert.
        val plain = GlyphEngine.convert(gradient(64, 64), 64, 64, twoLevel, cellAspect = 1f)
        val scaled = GlyphEngine.convert(
            gradient(64, 64), 64, 64, twoLevel.copy(ditherScale = 400), cellAspect = 1f,
        )
        assertEquals(plain.toText(), scaled.toText())
    }

    @Test
    fun `zero dither strength is the same as no dithering`() {
        val off = GlyphEngine.convert(gradient(64, 64), 64, 64, twoLevel, cellAspect = 1f)
        val neutral = twoLevel.copy(ditherMode = DitherMode.JARVIS, ditherStrength = 0)
        val art = GlyphEngine.convert(gradient(64, 64), 64, 64, neutral, cellAspect = 1f)
        assertEquals(off.toText(), art.toText())
    }

    @Test
    fun `edges only blanks the flat areas`() {
        val params = twoLevel.copy(edgeEnabled = true, edgeOnly = true, edgeThreshold = 50)
        // Half black, half white: only the seam should carry a glyph.
        val split = IntArray(32 * 32) { if (it % 32 < 16) BLACK else WHITE }
        val art = GlyphEngine.convert(split, 32, 32, params, cellAspect = 1f)

        assertEquals(' ', art.glyphAt(2, 5))
        assertEquals(' ', art.glyphAt(29, 5))
        assertEquals('|', art.glyphAt(15, 5))
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
    }
}
