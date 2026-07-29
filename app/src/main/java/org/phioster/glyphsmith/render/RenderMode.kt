package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable

/**
 * What a quantised level is turned into.
 *
 * Both modes drive the same sampler and the same 78 dither algorithms; they differ only in what
 * they do with the index each cell comes out with, and therefore in how many levels they ask
 * the dither for.
 *
 * The default is [GlyphMatrix] and has to stay that way: this field arrived after presets were
 * already being written to disk, and a preset without it must render exactly as it did before
 * the field existed.
 */
@Serializable
enum class RenderMode {
    /** Levels become characters from the ramp. The original behaviour. */
    GlyphMatrix,

    /**
     * Levels become colours — a direct pixel dither with no character mapping at all. Cell
     * size becomes the size of a pixel block, so a cell size of 1 dithers at full resolution.
     */
    PurePixel,
    ;

    val isGlyph: Boolean get() = this == GlyphMatrix
}
