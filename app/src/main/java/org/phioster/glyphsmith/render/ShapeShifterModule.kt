package org.phioster.glyphsmith.render

/**
 * [RenderMode.ShapeShifter] as a module: sample the grid, place a shape per cell, draw them.
 *
 * No dither stage — the bands are the quantiser here, and the feather is its dither.
 */
object ShapeShifterModule : RenderModule {

    override fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender {
        val cell = RenderBudget.cellFor(sourceWidth, sourceHeight, params.cellSize, maxSide)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val block = ShapeShifter.blockFor(grid.cols, grid.rows, cell, maxSide)
        val rendered = ShapeShifter.render(ShapeShifter.field(grid, params), block, params)

        val exportBlock = ShapeShifter.blockFor(grid.cols, grid.rows, cell, RenderBudget.MAX_OUTPUT_SIDE)
        return ModuleRender(
            bitmap = rendered.toBitmap(),
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = grid.cols * exportBlock,
            outputHeight = grid.rows * exportBlock,
        )
    }
}
