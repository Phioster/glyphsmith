package org.phioster.glyphsmith.render

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The oscilloscope: the picture as glowing traces on a tube.
 *
 * Everything that decides the picture is in [Oscilloscope], which knows nothing of Android. This
 * chooses how finely to draw it and wraps the result in a bitmap.
 */
object OscilloscopeModule : RenderModule {

    /** The longest side worth drawing: past this the traces only get wider, not more exact. */
    private const val MAX_SIDE = 3072f

    override fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender {
        val cell = Oscilloscope.cellFor(sourceWidth, sourceHeight)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val picture = Oscilloscope.draw(grid, params, scaleFor(grid.cols, grid.rows, maxSide))
        // Mutable, like every other module's: the effect chain may write into it.
        val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(picture.pixels, 0, picture.width, 0, 0, picture.width, picture.height)

        val exportScale = scaleFor(grid.cols, grid.rows, RenderBudget.MAX_OUTPUT_SIDE)
        return ModuleRender(
            bitmap = bitmap,
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = max(1, (grid.cols * exportScale).toInt()),
            outputHeight = max(1, (grid.rows * exportScale).toInt()),
        )
    }

    /** As large as [maxSide] allows, inside the pixel budget every export keeps to. */
    private fun scaleFor(cols: Int, rows: Int, maxSide: Int): Float {
        val longest = max(cols, rows).toFloat()
        val fill = min(maxSide.toFloat(), MAX_SIDE) / longest
        val budget = sqrt(RenderBudget.MAX_OUTPUT_PIXELS.toFloat() / (cols.toFloat() * rows))
        return min(fill, budget).coerceAtLeast(0.25f)
    }
}
