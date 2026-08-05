package org.phioster.glyphsmith.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The rule the mapping panel is split along: the dither half is render-neutral and the glyph
 * half is not.
 *
 * Stated here rather than inside the composable so it can be run without a Compose host — the
 * panel only reads the answers.
 */
class MappingSectionsTest {

    /** Every mapping setting away from its default, so a reset has something to undo. */
    private fun mangled(mode: RenderMode) = RenderSettings(
        renderMode = mode,
        brightness = 0.4f,
        contrast = 1.8f,
        gamma = 0.6f,
        saturation = 170,
        midtones = 70,
        highlights = 20,
        hue = 200,
        preBlur = 3,
        denoise = 2,
        ditherMode = DitherMode.MOD_ORB,
        ditherStrength = 40,
        serpentine = false,
        ditherScale = 250,
        modScale = 20,
        modAngle = 45,
        modPhase = 30,
        patternDensity = 80,
        orbCount = 6,
        orbSize = 30,
        orbIntensity = 90,
        orbRandom = 50,
        orbOffset = -40,
        orbDirection = 120,
        edgeEnabled = true,
        edgeThreshold = 70,
        edgeSetId = "box",
        edgeOnly = true,
        // An imported screen counts: the panel offers a load button and a clear button for it,
        // so it is one of the controls the panel shows. A field left out of here is a field the
        // whole-object comparison below cannot see — which is how this one was missed.
        screenOverride = List(256) { 255 - it },
    )

    @Test
    fun `pixel dither hides the glyph half of the mapping panel`() {
        assertFalse(MappingSections.showsGlyphMapping(RenderMode.PurePixel))
    }

    @Test
    fun `both glyph-producing modes show the glyph half`() {
        assertTrue(MappingSections.showsGlyphMapping(RenderMode.GlyphMatrix))
        assertTrue(MappingSections.showsGlyphMapping(RenderMode.PixelThenGlyph))
    }

    @Test
    fun `reset restores the dither settings in pixel dither`() {
        val reset = MappingSections.reset(mangled(RenderMode.PurePixel))
        val default = RenderSettings()

        assertEquals(default.brightness, reset.brightness, 0f)
        assertEquals(default.contrast, reset.contrast, 0f)
        assertEquals(default.gamma, reset.gamma, 0f)
        assertEquals(default.saturation, reset.saturation)
        assertEquals(default.midtones, reset.midtones)
        assertEquals(default.highlights, reset.highlights)
        assertEquals(default.hue, reset.hue)
        assertEquals(default.preBlur, reset.preBlur)
        assertEquals(default.denoise, reset.denoise)
        assertEquals(default.ditherMode, reset.ditherMode)
        assertEquals(default.ditherStrength, reset.ditherStrength)
        assertEquals(default.serpentine, reset.serpentine)
        assertEquals(default.ditherScale, reset.ditherScale)
        assertEquals(default.modScale, reset.modScale)
        assertEquals(default.modAngle, reset.modAngle)
        assertEquals(default.modPhase, reset.modPhase)
        assertEquals(default.patternDensity, reset.patternDensity)
        assertEquals(default.orbCount, reset.orbCount)
        assertEquals(default.orbSize, reset.orbSize)
        assertEquals(default.orbIntensity, reset.orbIntensity)
        assertEquals(default.orbRandom, reset.orbRandom)
        assertEquals(default.orbOffset, reset.orbOffset)
        assertEquals(default.orbDirection, reset.orbDirection)
    }

    @Test
    fun `reset leaves the edge settings alone when the glyph half is hidden`() {
        val before = mangled(RenderMode.PurePixel)
        val reset = MappingSections.reset(before)

        assertEquals(before.edgeEnabled, reset.edgeEnabled)
        assertEquals(before.edgeThreshold, reset.edgeThreshold)
        assertEquals(before.edgeSetId, reset.edgeSetId)
        assertEquals(before.edgeOnly, reset.edgeOnly)
    }

    /**
     * The panel's own claim, stated once over the whole settings object: a reset in glyph art
     * shows every mapping control there is, so afterwards nothing the panel owns can still be
     * off its default. Written this way because the failure it guards against is a *forgotten*
     * field, which a list of assertions written by the same hand as the implementation cannot
     * catch.
     */
    @Test
    fun `after a reset in glyph art no mapping setting is left off its default`() {
        val reset = MappingSections.reset(mangled(RenderMode.GlyphMatrix))

        assertEquals(RenderSettings(renderMode = RenderMode.GlyphMatrix), reset)
    }

    @Test
    fun `reset restores the edge settings in glyph art`() {
        val reset = MappingSections.reset(mangled(RenderMode.GlyphMatrix))
        val default = RenderSettings()

        assertEquals(default.edgeEnabled, reset.edgeEnabled)
        assertEquals(default.edgeThreshold, reset.edgeThreshold)
        assertEquals(default.edgeSetId, reset.edgeSetId)
        assertEquals(default.edgeOnly, reset.edgeOnly)
    }

    @Test
    fun `reset keeps the render mode and everything the panel does not own`() {
        val before = mangled(RenderMode.PixelThenGlyph).copy(
            charSetId = "blocks",
            cellSize = 3,
            depth = 4,
            invert = true,
            colorMode = ColorMode.PALETTE,
            paletteId = "palette.gameboy",
            pixelBlock = 6,
        )
        val reset = MappingSections.reset(before)

        assertEquals(before.renderMode, reset.renderMode)
        assertEquals(before.charSetId, reset.charSetId)
        assertEquals(before.cellSize, reset.cellSize)
        assertEquals(before.depth, reset.depth)
        assertEquals(before.invert, reset.invert)
        assertEquals(before.colorMode, reset.colorMode)
        assertEquals(before.paletteId, reset.paletteId)
        assertEquals(before.pixelBlock, reset.pixelBlock)
    }
}
