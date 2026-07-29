package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import org.phioster.glyphsmith.core.pipeline.RenderContext
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

    /** One rendering with no layers of its own — what a layer is built from. */
    private fun renderFlat(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
    ): Bitmap = run(pixels, sourceWidth, sourceHeight, params.copy(layers = emptyList()), maxSide).bitmap

    fun run(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
        isScrubbing: Boolean = false,
    ): Result {
        val ctx = RenderContext(maxSide = maxSide, isScrubbing = isScrubbing)
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

        var bitmap = EffectPipeline.apply(
            AsciiRenderer.render(grid, params, fontSize, canvasScale),
            params.effects,
            ctx,
        )

        // Each layer is a full render of the same source at the base's size, transformed and
        // blended over it. Its own layer list is ignored — one level deep is the rule.
        if (params.layers.any { it.enabled && it.opacity > 0 }) {
            bitmap = LayerCompositor.composite(bitmap, params.layers) { layer ->
                runCatching {
                    renderFlat(pixels, sourceWidth, sourceHeight, layer.params, maxSide)
                }.getOrNull()
            }
        }

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
