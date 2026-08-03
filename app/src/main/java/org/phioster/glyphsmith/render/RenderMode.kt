package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable

/**
 * What a quantised level is turned into.
 *
 * Both modes drive the same sampler and the same 78 dither algorithms; they differ only in what
 * they do with the index each cell comes out with, and therefore in how many levels they ask
 * the dither for.
 *
 * There are two defaults here and they are deliberately different values.
 *
 * The *field* default on `AsciiParams.renderMode` is [GlyphMatrix] and has to stay that way:
 * this field arrived after presets were already being written to disk, and a preset without it
 * must render exactly as it did before the field existed.
 *
 * [DEFAULT] is what new work starts in, and it is [PurePixel]. Pixel dithering is the
 * general-purpose mode; glyph rendering is a render module you choose. Keeping the two apart is
 * what lets new sessions open in the general mode without the saved library changing under it.
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

    /**
     * Both, in order: the image is dithered to a palette, and *that* is what the glyphs are read
     * from.
     *
     * The other two modes fork at the quantised level — one index becomes either a colour or a
     * character, never both — so a palette dither could not be turned into characters at all.
     * Here the dither finishes first and produces a real bitmap, which the glyph stage then reads
     * as if it were any other image. That is the difference between a branch and a chain, and it
     * is the only way to get a paletted dither *and* a `.txt` out of the same render.
     */
    PixelThenGlyph,
    ;

    /**
     * True when the render produces a character grid — which is what decides whether the text
     * exports are available and whether the mapping controls are worth showing.
     */
    val isGlyph: Boolean get() = this != PurePixel

    /** True when a palette dither runs before anything else looks at the image. */
    val ditherFirst: Boolean get() = this != GlyphMatrix

    companion object {
        /**
         * The mode new work starts in: new sessions, and any general-purpose roll of a look.
         *
         * Not the same thing as the default of `AsciiParams.renderMode`, which answers a
         * different question — what a preset that names no mode should be read as — and must
         * stay [GlyphMatrix] for as long as presets written before the field exist.
         */
        val DEFAULT = PurePixel
    }
}
