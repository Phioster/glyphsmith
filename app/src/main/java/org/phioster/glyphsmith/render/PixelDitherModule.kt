package org.phioster.glyphsmith.render

import kotlin.math.max

/**
 * [RenderMode.PurePixel] as a module: sample, quantise, paint the levels as colours.
 *
 * Cells are square here — there is no glyph metric to compensate for, so a cell of one source
 * pixel is a full-resolution dither. Lifted out of the pipeline unchanged; it sat in the same
 * `when` as the two glyph branches, which is what made the shared pipeline import Glyph Art in
 * order to render a pixel dither.
 */
object PixelDitherModule : RenderModule {

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

        val block = RenderBudget.blockFor(params, grid.cols, grid.rows, maxSide)
        val rendered = PixelDitherRenderer.render(indexed, params, block)

        val exportBlock = (RenderBudget.MAX_OUTPUT_SIDE / max(grid.cols, grid.rows))
            .coerceIn(1, cell)
        return ModuleRender(
            bitmap = rendered.toBitmap(),
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = grid.cols * exportBlock,
            outputHeight = grid.rows * exportBlock,
        )
    }
}
