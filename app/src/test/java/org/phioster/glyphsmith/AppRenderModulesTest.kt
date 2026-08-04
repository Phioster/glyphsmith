package org.phioster.glyphsmith

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModules

/**
 * The binding of a render mode to the code that renders it.
 *
 * This is the seam that replaced the pipeline's three-way branch, and the whole point of it is
 * a rule that cannot be read off any single file: the pixel mode must be served by shared code,
 * the glyph modes by the glyph module, and the pipeline by neither in particular. The `when` in
 * [AppRenderModules] makes a *missing* binding a compile error; what it cannot catch is a
 * binding pointing at the wrong module, which is what these check.
 *
 * Packages are asserted by name rather than by identity on purpose. Naming the module objects
 * here would only restate the `when`; asking which package the answer came from is the question
 * the layering rules actually care about.
 */
class AppRenderModulesTest {

    private fun packageOf(mode: RenderMode): String =
        AppRenderModules.moduleFor(mode)::class.java.name.substringBeforeLast('.')

    /**
     * One module each, and no two modes sharing one. Two modes answering with the same object
     * is what a copy-paste in the `when` looks like, and it renders the wrong mode silently.
     */
    @Test
    fun `every render mode has a module of its own`() {
        val modules = RenderMode.entries.map { AppRenderModules.moduleFor(it) }

        assertEquals(RenderMode.entries.size, modules.distinct().size)
    }

    /** A pixel dither that needed the glyph module to render would make Glyph Art mandatory. */
    @Test
    fun `the pixel mode is rendered by shared code`() {
        assertEquals("org.phioster.glyphsmith.render", packageOf(RenderMode.PurePixel))
    }

    @Test
    fun `the glyph modes are rendered by the glyph module`() {
        assertEquals("org.phioster.glyphsmith.glyph", packageOf(RenderMode.GlyphMatrix))
        assertEquals("org.phioster.glyphsmith.glyph", packageOf(RenderMode.PixelThenGlyph))
    }

    /**
     * The registry says which modes produce a character grid — the UI hides the text exports by
     * it. A mode declared as glyph art but bound to a module outside Glyph Art, or the reverse,
     * would offer a `.txt` of a render that has no characters in it.
     */
    @Test
    fun `a mode produces glyphs exactly when the glyph module renders it`() {
        RenderMode.entries.forEach { mode ->
            assertEquals(
                "$mode disagrees with its module about producing glyphs",
                RenderModules.of(mode).producesGlyphs,
                packageOf(mode) == "org.phioster.glyphsmith.glyph",
            )
        }
    }
}
