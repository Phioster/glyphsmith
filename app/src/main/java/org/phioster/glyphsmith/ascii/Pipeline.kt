package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import org.phioster.glyphsmith.effects.EffectPipeline
import kotlin.math.max
import kotlin.math.min

/**
 * Source pixels → character grid → rendered, effected bitmap.
 *
 * One entry point shared by the live preview, the export, and every animation frame, so a
 * frame can never be produced by a slightly different code path than the preview that sold
 * it to the user.
 */
object Pipeline {

    /** Aspect is measured at a fixed size so the grid is identical at every output scale. */
    private const val REFERENCE_FONT_SIZE = 32

    class Result(
        val art: AsciiArt,
        val bitmap: Bitmap,
        val face: FontChoice,
        val outputWidth: Int,
        val outputHeight: Int,
    )

    fun run(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
    ): Result {
        val ramp = params.effectiveRamp().ifEmpty { " " }
        val face = AsciiRenderer.faceFor(params, ramp)
        val aspect = AsciiRenderer.metrics(REFERENCE_FONT_SIZE, ramp, face.typeface).aspect
        val grid = AsciiEngine.convert(pixels, sourceWidth, sourceHeight, params, aspect)

        val fontSize = AsciiRenderer.fitFontSize(
            grid.cols, grid.rows, ramp, params.fontSizePx, maxSide, face.typeface,
        )
        // In canvas mode the whole canvas is scaled down instead of the glyphs shrinking,
        // so the framing — letterboxing included — survives at any preview size.
        val canvasScale = if (params.canvasEnabled) {
            min(1f, maxSide.toFloat() / max(params.canvasWidth, params.canvasHeight))
        } else {
            1f
        }

        val bitmap = EffectPipeline.apply(
            AsciiRenderer.render(grid, params, fontSize, canvasScale),
            params.effects,
        )

        val output = if (params.canvasEnabled) {
            params.canvasWidth to params.canvasHeight
        } else {
            val cell = AsciiRenderer.metrics(
                AsciiRenderer.fitFontSize(
                    grid.cols, grid.rows, ramp, params.fontSizePx,
                    AsciiRenderer.MAX_OUTPUT_SIDE, face.typeface,
                ),
                ramp,
                face.typeface,
            )
            (grid.cols * cell.width) to (grid.rows * cell.height)
        }

        return Result(grid, bitmap, face, output.first, output.second)
    }
}
