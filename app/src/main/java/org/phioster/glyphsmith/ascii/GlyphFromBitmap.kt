package org.phioster.glyphsmith.ascii

import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.render.CellSampler
import org.phioster.glyphsmith.render.QuantisePass

/**
 * Reads glyphs off a finished bitmap, rather than off the source image.
 *
 * This is what makes ASCII a *step* instead of a *branch*. [GlyphEngine.convert] starts from the
 * original pixels, so the dither it runs is the only dither in that render — which is why a
 * palette dither and a character grid were mutually exclusive. Handing an already-rendered image
 * to this instead lets the two stack: the palette dither decides the tone, and the glyphs describe
 * what the dither produced.
 *
 * **The glyph stage does not dither again.** The tone is already carried by the pattern the first
 * pass laid down; running a second error diffusion over it would diffuse the error of an image
 * that is itself made of error, which reads as noise rather than as detail. So the mapping here is
 * plain quantisation, and the dither settings belong entirely to the first stage.
 */
object GlyphFromBitmap {

    /**
     * The character grid for [source], sampled with [cellAspect] so the cells match the font.
     *
     * Colour comes from the bitmap being read, never from the original: in this mode the palette
     * has already had its say, and going back to the source would undo it.
     */
    fun convert(
        source: Pixels,
        params: RenderSettings,
        cellAspect: Float = GlyphEngine.DEFAULT_CELL_ASPECT,
    ): GlyphGrid {
        val ramp = params.effectiveRamp().ifEmpty { " " }
        // Tone adjustments were applied before the first dither. Applying them a second time here
        // would double the contrast and crush what the dither just resolved, so the glyph stage
        // reads the bitmap flat — and, for the same reason, without a dither of its own.
        val flat = params.copy(
            ditherMode = DitherMode.NONE,
            brightness = 0f,
            contrast = 1f,
            gamma = 1f,
            midtones = NEUTRAL_TONE,
            highlights = NEUTRAL_TONE,
            saturation = NEUTRAL_SATURATION,
            hue = 0,
            preBlur = 0,
            denoise = 0,
        )
        val grid = CellSampler.sample(
            pixels = source.data,
            width = source.width,
            height = source.height,
            params = flat,
            cellWidth = params.cellSize.coerceAtLeast(1),
            cellHeight = CellSampler.cellHeightFor(params.cellSize, cellAspect),
        )
        return GlyphEngine.mapToGlyphs(QuantisePass.run(flat, grid, ramp.length), flat, ramp)
    }

    private const val NEUTRAL_TONE = 50
    private const val NEUTRAL_SATURATION = 100
}
