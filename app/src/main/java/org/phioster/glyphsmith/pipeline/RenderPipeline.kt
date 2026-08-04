package org.phioster.glyphsmith.pipeline

import android.graphics.Bitmap
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.effects.EffectPipeline
import org.phioster.glyphsmith.render.RenderModuleOutput
import org.phioster.glyphsmith.render.RenderModuleSet
import org.phioster.glyphsmith.render.RenderSettings

/**
 * Source pixels → a render module → effects → layers.
 *
 * One entry point shared by the live preview, the export, and every animation frame, so a
 * frame can never be produced by a slightly different code path than the preview that sold
 * it to the user.
 *
 * What is left here is only the part that is the same whatever was rendered. The three modes
 * used to be three branches in this file, and two of them were Glyph Art — which is how the
 * shared pipeline came to import a character grid, a typeface and a ramp. The branch is now a
 * lookup in the [RenderModuleSet] the caller supplies: the pipeline runs a module without
 * knowing which modules exist.
 */
object RenderPipeline {

    /**
     * [output] is whatever the module carried beyond its pixels — a character grid and its face
     * in the glyph modes, nothing in the pixel one. Opaque here on purpose; see
     * [RenderModuleOutput].
     *
     * [cols] and [rows] are carried separately from the bitmap size because the readouts and the
     * status line need the grid size in every mode.
     */
    class Result(
        val bitmap: Bitmap,
        val cols: Int,
        val rows: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val output: RenderModuleOutput?,
    )

    /** One rendering with no layers of its own — what a layer is built from. */
    @Suppress("LongParameterList")
    private fun renderFlat(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
        modules: RenderModuleSet,
    ): Bitmap = run(
        pixels, sourceWidth, sourceHeight, params.copy(layers = emptyList()), maxSide, modules,
    ).bitmap

    @Suppress("LongParameterList")
    fun run(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
        modules: RenderModuleSet,
        isScrubbing: Boolean = false,
    ): Result {
        // The clock the effect chain animates from. It already travels inside the params —
        // renderFrames writes the frame's normalised loop position into `temporal.time` — so
        // handing it to the nodes costs one argument rather than a second plumbing path.
        val ctx = RenderContext(
            maxSide = maxSide,
            isScrubbing = isScrubbing,
            time = params.temporal.time,
        )
        val rendered = modules.moduleFor(params.renderMode)
            .render(pixels, sourceWidth, sourceHeight, params, maxSide)
        // Once, here, rather than at the end of each module: three copies of this line are three
        // chances for the effect chain to run somewhere else in the order.
        val base = Result(
            bitmap = EffectPipeline.apply(rendered.bitmap, params.effects, ctx),
            cols = rendered.cols,
            rows = rendered.rows,
            outputWidth = rendered.outputWidth,
            outputHeight = rendered.outputHeight,
            output = rendered.output,
        )

        // Each layer is a full render of the same source at the base's size, transformed and
        // blended over it. Its own layer list is ignored — one level deep is the rule.
        if (params.layers.none { it.enabled && it.opacity > 0 }) return base

        val composited = LayerCompositor.composite(base.bitmap, params.layers) { layer ->
            runCatching {
                renderFlat(pixels, sourceWidth, sourceHeight, layer.params, maxSide, modules)
            }.getOrNull()
        }
        return Result(
            composited, base.cols, base.rows, base.outputWidth, base.outputHeight, base.output,
        )
    }
}
