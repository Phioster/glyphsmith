package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.data.PresetLibrary

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
     * The shipped library says what it wants for itself.
     *
     * This test used to read the other way round: every built-in was glyph art, because every
     * built-in named no mode at all and rode the field default. That made the default do two
     * jobs, and the pixel-first library separated them — each preset now names its own mode,
     * so the field default protects exactly one thing, which is files written before the
     * field existed. `PresetLibraryTest` holds the stronger form of this by reading the bytes.
     */
    @Test
    fun `the shipped library names its modes rather than inheriting one`() {
        val modes = PresetLibrary.builtIns.map { it.params.renderMode }.toSet()

        assertEquals(setOf(RenderMode.PurePixel, RenderMode.GlyphMatrix), modes)
    }

    /**
     * What undo and redo restore is a whole [AsciiParams] — the stacks hold nothing else — so
     * the mode travels with a history step for exactly as long as it stays a field of that
     * object. Hoisting it into the UI state instead would make a mode switch invisible to the
     * history: the two values either side of it would compare equal, no step would be
     * recorded, and undo would come back with the wrong renderer.
     *
     * The stacks themselves live in the view model, which needs an `Application` and cannot be
     * built in a JVM test; this pins the property they rest on.
     */
    @Test
    fun `a mode switch is a history step of its own`() {
        val before = AsciiParams.newSession()
        val after = before.copy(renderMode = RenderMode.GlyphMatrix)

        assertNotEquals(before, after)
        assertEquals(RenderMode.PurePixel, before.renderMode)
        assertEquals(RenderMode.GlyphMatrix, after.renderMode)
    }
}
