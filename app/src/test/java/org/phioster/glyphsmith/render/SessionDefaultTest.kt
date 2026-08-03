package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.data.PresetStore

/**
 * The two defaults, which are deliberately not the same one.
 *
 * A new session opens in Pixel Dither, because that is the general-purpose mode. The *field*
 * default stays Glyph Art, because it is what a preset falls back to when it does not name a
 * mode — and every preset written before the field existed is exactly that case.
 *
 * Collapsing the two into one value is the mistake these tests exist to catch: it would turn
 * the whole saved library into pixel dithers the first time it was opened.
 */
class SessionDefaultTest {

    @Test
    fun `a new session starts in pixel dither`() {
        assertEquals(RenderMode.PurePixel, AsciiParams.newSession().renderMode)
    }

    @Test
    fun `the new session default is the one the mode names`() {
        assertEquals(RenderMode.DEFAULT, AsciiParams.newSession().renderMode)
    }

    /** Starting a session must change the mode and nothing else. */
    @Test
    fun `a new session differs from a bare configuration only in the render mode`() {
        assertEquals(AsciiParams(renderMode = RenderMode.PurePixel), AsciiParams.newSession())
    }

    /**
     * The compatibility default. A preset that does not name a mode predates the field, and
     * the schema migration writes Glyph Art into it by leaning on exactly this value.
     */
    @Test
    fun `the field default stays glyph art`() {
        assertEquals(RenderMode.GlyphMatrix, AsciiParams().renderMode)
    }

    /**
     * The shipped presets name no render mode, so they ride the field default. If that ever
     * became Pixel Dither, all of them would change appearance without a line of them being
     * edited — which is the failure this pins.
     */
    @Test
    fun `every built-in preset still renders as glyph art`() {
        val modes = PresetStore.builtIns.map { it.params.renderMode }.toSet()

        assertEquals(setOf(RenderMode.GlyphMatrix), modes)
    }
}
