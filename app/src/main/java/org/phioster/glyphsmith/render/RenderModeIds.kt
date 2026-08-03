package org.phioster.glyphsmith.render

import org.phioster.glyphsmith.core.serial.WireIdSerializer

/**
 * What each render mode is called in a saved preset.
 *
 * The ids describe the mode rather than the constant: `GlyphMatrix` is the Glyph Art module and
 * `PurePixel` is the pixel dither, which is what the product calls them and what a preset should
 * say. Renaming either constant — both are on the list of eventual renames — must not change a
 * single byte of what is written here.
 */
object RenderModeIds : WireIdSerializer<RenderMode>(
    category = "render",
    values = RenderMode.entries.toList(),
    idOf = { mode ->
        when (mode) {
            RenderMode.GlyphMatrix -> "render.glyph-art"
            RenderMode.PurePixel -> "render.pixel-dither"
            RenderMode.PixelThenGlyph -> "render.pixel-then-glyph"
        }
    },
)
