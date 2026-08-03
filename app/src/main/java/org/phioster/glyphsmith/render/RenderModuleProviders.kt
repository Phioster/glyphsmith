package org.phioster.glyphsmith.render

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * A render module: what a quantised level is turned into.
 *
 * This is the one provider category that carries capabilities, because they are already the
 * thing the UI branches on — whether the glyph controls are worth showing, whether the text
 * exports exist. Declared here rather than asked of the enum at each call site, so a third
 * module cannot be added without stating what it can do.
 */
class RenderModuleProvider(val mode: RenderMode, override val displayName: String) : Provider {
    override val id: String = RenderModeIds.idOf(mode)
    override val category = ProviderCategory.RENDER

    /** True when the render produces a character grid, and so a `.txt`, `.svg` or `.ansi`. */
    val producesGlyphs: Boolean get() = mode.isGlyph

    /** True when a palette dither runs before anything else looks at the image. */
    val ditherFirst: Boolean get() = mode.ditherFirst
}

/**
 * Every render module this build ships.
 *
 * The display names live here rather than beside the enum: `PurePixel` is spelled *pixel dither*
 * for the user and has been since long before the enum could be renamed, and a panel should not
 * have to keep its own table to say so.
 *
 * Declared in the enum's own order, and a test holds it there. The registry is what the mode
 * dropdown lists, so any other order here would silently reorder that dropdown — which may well
 * be worth doing, pixel dither being the default, but it is a decision about the product rather
 * than a consequence of introducing a registry.
 */
object RenderModules : Registry<RenderModuleProvider>(
    ProviderCategory.RENDER,
    listOf(
        RenderModuleProvider(RenderMode.GlyphMatrix, "glyph art"),
        RenderModuleProvider(RenderMode.PurePixel, "pixel dither"),
        RenderModuleProvider(RenderMode.PixelThenGlyph, "pixel dither → glyphs"),
    ),
) {
    fun of(mode: RenderMode): RenderModuleProvider = all.first { it.mode == mode }
}
