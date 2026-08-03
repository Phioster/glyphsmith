package org.phioster.glyphsmith.render

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.GlyphFromBitmap
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.image.Pixels

/**
 * The chained mode: dither to a palette first, read glyphs off the result.
 *
 * `Pipeline` itself cannot be tested here — it rasterises through `Canvas` — so these cover the
 * two pure halves the chain is made of, and the mode's contract with the rest of the app.
 */
class ChainedModeTest {

    private val side = 48

    private fun gradient(): IntArray = IntArray(side * side) { i ->
        val v = ((i % side) * 255 / (side - 1))
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    private fun params(mode: RenderMode) = AsciiParams(
        renderMode = mode,
        cellSize = 2,
        colorMode = ColorMode.PALETTE,
        paletteId = "grayscale",
        ditherMode = DitherMode.FLOYD_STEINBERG,
    )

    /** A palette dither, exactly as the first stage of the chain produces it. */
    private fun dithered(p: AsciiParams): Pixels {
        val grid = CellSampler.sample(gradient(), side, side, p, p.cellSize, p.cellSize)
        val indexed = QuantisePass.run(p, grid, PixelDitherRenderer.levelsFor(p))
        return PixelDitherRenderer.render(indexed, p, 1)
    }

    @Test
    fun `the chained mode counts as a glyph mode and dithers first`() {
        assertTrue(RenderMode.PixelThenGlyph.isGlyph)
        assertTrue(RenderMode.PixelThenGlyph.ditherFirst)

        assertTrue("pure pixel dithers first too", RenderMode.PurePixel.ditherFirst)
        assertFalse("but it produces no glyphs", RenderMode.PurePixel.isGlyph)
        assertTrue(RenderMode.GlyphMatrix.isGlyph)
        assertFalse("the glyph-only mode has no dither stage of its own", RenderMode.GlyphMatrix.ditherFirst)
    }

    /**
     * The point of the whole mode. Reading glyphs off a bitmap has to work at all — it is what
     * turns the character stage from an alternative into a step.
     */
    @Test
    fun `glyphs can be read off a dithered bitmap`() {
        val p = params(RenderMode.PixelThenGlyph)
        val art = GlyphFromBitmap.convert(dithered(p), p, cellAspect = 1f)

        assertTrue("no grid was produced", art.cols > 0 && art.rows > 0)
        assertEquals(art.cols * art.rows, art.glyphs.size)
        assertTrue("the grid is blank", art.glyphs.any { it != ' ' })
    }

    /**
     * The reason the glyph stage flattens the dither settings: a second error diffusion over an
     * image that is *made of* diffusion error reads as noise, not as detail. Same input, so the
     * only way the two can differ is if the second stage dithered again.
     */
    @Test
    fun `the glyph stage does not dither a second time`() {
        val diffused = params(RenderMode.PixelThenGlyph)
        val bitmap = dithered(diffused)

        val viaDiffusion = GlyphFromBitmap.convert(bitmap, diffused, cellAspect = 1f).toText()
        val viaNone = GlyphFromBitmap.convert(
            bitmap, diffused.copy(ditherMode = DitherMode.BAYER_8), cellAspect = 1f,
        ).toText()

        assertEquals("the dither mode reached the glyph stage", viaDiffusion, viaNone)
    }

    /** Tone adjustments belong to the first stage; applying them twice would crush the result. */
    @Test
    fun `the glyph stage ignores the tone controls`() {
        val p = params(RenderMode.PixelThenGlyph)
        val bitmap = dithered(p)

        val plain = GlyphFromBitmap.convert(bitmap, p, cellAspect = 1f).toText()
        val cranked = GlyphFromBitmap.convert(
            bitmap, p.copy(contrast = 2.4f, brightness = 0.4f, gamma = 2f), cellAspect = 1f,
        ).toText()

        assertEquals("tone was applied twice", plain, cranked)
    }

    @Test
    fun `the same bitmap always gives the same grid`() {
        val p = params(RenderMode.PixelThenGlyph)
        val bitmap = dithered(p)

        assertEquals(
            GlyphFromBitmap.convert(bitmap, p, cellAspect = 1f).toText(),
            GlyphFromBitmap.convert(bitmap, p, cellAspect = 1f).toText(),
        )
    }

    /**
     * A preset written before this mode existed has no `renderMode` at all and must still load as
     * the glyph mode — the compatibility rule the whole enum rests on. Adding a third entry must
     * not have moved that.
     */
    @Test
    fun `a preset without a render mode still loads as glyph art`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val decoded = json.decodeFromString<AsciiParams>("""{"cellSize":6}""")

        assertEquals(RenderMode.GlyphMatrix, decoded.renderMode)
        assertEquals("the new block control must default to auto", 0, decoded.pixelBlock)
    }

    @Test
    fun `the chained mode survives a round trip`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val encoded = json.encodeToString(AsciiParams.serializer(), params(RenderMode.PixelThenGlyph))
        val decoded = json.decodeFromString(AsciiParams.serializer(), encoded)

        assertEquals(RenderMode.PixelThenGlyph, decoded.renderMode)
    }
}
