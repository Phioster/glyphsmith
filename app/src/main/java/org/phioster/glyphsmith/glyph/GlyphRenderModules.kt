package org.phioster.glyphsmith.glyph

import org.phioster.glyphsmith.render.CellSampler
import org.phioster.glyphsmith.render.ModuleRender
import org.phioster.glyphsmith.render.PixelDitherRenderer
import org.phioster.glyphsmith.render.QuantisePass
import org.phioster.glyphsmith.render.RenderBudget
import org.phioster.glyphsmith.render.RenderModule
import org.phioster.glyphsmith.render.RenderModuleOutput
import org.phioster.glyphsmith.render.RenderSettings
import kotlin.math.max
import kotlin.math.min

/** Aspect is measured at a fixed size so the grid is identical at every output scale. */
private const val REFERENCE_FONT_SIZE = 32

/**
 * The half of a render that only Glyph Art has: the character grid, and the face it was drawn
 * with.
 *
 * Travels through the shared pipeline as an opaque [RenderModuleOutput]. The pipeline used to
 * carry these two as named fields — `art: GlyphGrid?` and `face: FontChoice?`, both null in the
 * pixel mode — which is how a shared type came to name two glyph ones. Whoever wants them still
 * gets them, by asking for this type; whoever does not, no longer has to mention them.
 */
class GlyphRenderOutput(val art: GlyphGrid, val face: FontChoice) : RenderModuleOutput

/**
 * [org.phioster.glyphsmith.render.RenderMode.GlyphMatrix]: the source read straight as
 * characters.
 *
 * Lifted out of the pipeline unchanged, except that the effect chain is no longer applied here —
 * it runs once in the pipeline, on whatever a module produced, which is what it already did for
 * all three modes.
 */
object GlyphMatrixModule : RenderModule {

    override fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender {
        val ramp = params.effectiveRamp().ifEmpty { " " }
        val face = GlyphRenderer.faceFor(params, ramp)
        val aspect = GlyphRenderer.metrics(REFERENCE_FONT_SIZE, ramp, face.typeface).aspect
        val grid = GlyphEngine.convert(pixels, sourceWidth, sourceHeight, params, aspect)

        val fontSize = GlyphRenderer.fitFontSize(
            grid.cols, grid.rows, ramp, params.fontSizePx, maxSide, face.typeface,
        )
        // In canvas mode the whole canvas is scaled down instead of the glyphs shrinking,
        // so the framing — letterboxing included — survives at any preview size.
        val canvasScale = if (params.canvasEnabled) {
            min(1f, maxSide.toFloat() / max(params.canvasWidth, params.canvasHeight))
        } else {
            1f
        }

        val exportSize = if (params.canvasEnabled) {
            params.canvasWidth to params.canvasHeight
        } else {
            val cell = exportCell(grid.cols, grid.rows, ramp, params, face)
            (grid.cols * cell.width) to (grid.rows * cell.height)
        }

        return ModuleRender(
            bitmap = GlyphRenderer.render(grid, params, fontSize, canvasScale),
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = exportSize.first,
            outputHeight = exportSize.second,
            output = GlyphRenderOutput(grid, face),
        )
    }
}

/**
 * [org.phioster.glyphsmith.render.RenderMode.PixelThenGlyph]: a render of the source as a
 * palette dither, handed on to the glyph stage as its input.
 *
 * The other two modes fork at the quantised level, so a palette dither and a character grid
 * could never both happen. Here the dither finishes into a real bitmap first and the glyphs are
 * read off *that* — which is what makes the character stage a step rather than an alternative,
 * and what lets this mode produce a `.txt` of a paletted dither.
 *
 * The first stage is the pixel module's own arithmetic, called directly rather than through the
 * module: what this mode chains is the *dither*, not the other module's framing, effects or
 * export sizing.
 */
object PixelThenGlyphModule : RenderModule {

    override fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender {
        val cell = RenderBudget.cellFor(sourceWidth, sourceHeight, params.cellSize, maxSide)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val indexed = QuantisePass.run(params, grid, PixelDitherRenderer.levelsFor(params))
        val dithered = PixelDitherRenderer.render(
            indexed, params, RenderBudget.blockFor(params, grid.cols, grid.rows, maxSide),
        )

        val ramp = params.effectiveRamp().ifEmpty { " " }
        val face = GlyphRenderer.faceFor(params, ramp)
        val aspect = GlyphRenderer.metrics(REFERENCE_FONT_SIZE, ramp, face.typeface).aspect
        val art = GlyphFromBitmap.convert(dithered, params, aspect)

        val fontSize = GlyphRenderer.fitFontSize(
            art.cols, art.rows, ramp, params.fontSizePx, maxSide, face.typeface,
        )
        val cellMetrics = exportCell(art.cols, art.rows, ramp, params, face)

        return ModuleRender(
            bitmap = GlyphRenderer.render(art, params, fontSize, 1f),
            cols = art.cols,
            rows = art.rows,
            outputWidth = art.cols * cellMetrics.width,
            outputHeight = art.rows * cellMetrics.height,
            output = GlyphRenderOutput(art, face),
        )
    }
}

/**
 * The cell the grid would be drawn with at full export size.
 *
 * Measured against [RenderBudget.MAX_OUTPUT_SIDE] rather than the preview budget, because the
 * question it answers — how large this render exports — must not change with preview quality.
 */
private fun exportCell(
    cols: Int,
    rows: Int,
    ramp: String,
    params: RenderSettings,
    face: FontChoice,
): CellMetrics = GlyphRenderer.metrics(
    GlyphRenderer.fitFontSize(
        cols, rows, ramp, params.fontSizePx, RenderBudget.MAX_OUTPUT_SIDE, face.typeface,
    ),
    ramp,
    face.typeface,
)
