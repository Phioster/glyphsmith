package org.phioster.glyphsmith

import org.phioster.glyphsmith.glyph.GlyphMatrixModule
import org.phioster.glyphsmith.glyph.PixelThenGlyphModule
import org.phioster.glyphsmith.render.PixelDitherModule
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModule
import org.phioster.glyphsmith.render.RenderModuleSet

/**
 * Which module this build renders each mode with.
 *
 * The one place that names Glyph Art and the pixel module together, and it sits here — beside
 * the view model and the activity — because that is what a composition root is for. Everything
 * below it is either shared and knows no module, or a module and knows no other; the pipeline
 * is handed this and runs whatever it finds.
 *
 * A `when` over the enum rather than a map, so a fourth render mode does not compile until
 * somebody has said what renders it. A map would have thrown at the first frame instead, and
 * only in the mode nobody had tried yet.
 *
 * Deliberately not a registration list something has to remember to call: a module that is
 * bound at startup is a module that is missing in every test, and missing quietly.
 */
object AppRenderModules : RenderModuleSet {

    override fun moduleFor(mode: RenderMode): RenderModule = when (mode) {
        RenderMode.GlyphMatrix -> GlyphMatrixModule
        RenderMode.PurePixel -> PixelDitherModule
        RenderMode.PixelThenGlyph -> PixelThenGlyphModule
    }
}
