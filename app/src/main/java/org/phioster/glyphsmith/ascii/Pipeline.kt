package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.effects.EffectPipeline
import org.phioster.glyphsmith.render.CellSampler
import org.phioster.glyphsmith.render.PixelDitherRenderer
import org.phioster.glyphsmith.render.QuantisePass
import org.phioster.glyphsmith.render.RenderMode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Source pixels → quantised grid → rendered, effected bitmap.
 *
 * One entry point shared by the live preview, the export, and every animation frame, so a
 * frame can never be produced by a slightly different code path than the preview that sold
 * it to the user. That now covers both render modes: the branch is here and nowhere else.
 */
object Pipeline {

    /** Aspect is measured at a fixed size so the grid is identical at every output scale. */
    private const val REFERENCE_FONT_SIZE = 32

    /**
     * [art] and [face] are null in the pixel mode — there is no character grid and no typeface.
     * [cols] and [rows] are carried separately for exactly that reason: the readouts and the
     * status line need the grid size in both modes.
     */
    class Result(
        val art: AsciiArt?,
        val bitmap: Bitmap,
        val face: FontChoice?,
        val cols: Int,
        val rows: Int,
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
        // The clock the effect chain animates from. It already travels inside the params —
        // renderFrames writes the frame's normalised loop position into `temporal.time` — so
        // handing it to the nodes costs one argument rather than a second plumbing path.
        val ctx = RenderContext(
            maxSide = maxSide,
            isScrubbing = isScrubbing,
            time = params.temporal.time,
        )
        val base = when (params.renderMode) {
            RenderMode.GlyphMatrix -> glyphRender(pixels, sourceWidth, sourceHeight, params, maxSide, ctx)
            RenderMode.PurePixel -> pixelRender(pixels, sourceWidth, sourceHeight, params, maxSide, ctx)
            RenderMode.PixelThenGlyph ->
                chainedRender(pixels, sourceWidth, sourceHeight, params, maxSide, ctx)
        }

        // Each layer is a full render of the same source at the base's size, transformed and
        // blended over it. Its own layer list is ignored — one level deep is the rule.
        if (params.layers.none { it.enabled && it.opacity > 0 }) return base

        val composited = LayerCompositor.composite(base.bitmap, params.layers) { layer ->
            runCatching {
                renderFlat(pixels, sourceWidth, sourceHeight, layer.params, maxSide)
            }.getOrNull()
        }
        return Result(
            base.art, composited, base.face, base.cols, base.rows, base.outputWidth, base.outputHeight,
        )
    }

    @Suppress("LongParameterList")
    private fun glyphRender(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
        ctx: RenderContext,
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
            ctx,
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

        return Result(grid, bitmap, face, grid.cols, grid.rows, output.first, output.second)
    }

    @Suppress("LongParameterList")
    private fun pixelRender(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
        ctx: RenderContext,
    ): Result {
        // Cells are square here: there is no glyph metric to compensate for, so a cell of one
        // source pixel is a full-resolution dither.
        val cell = effectiveCell(sourceWidth, sourceHeight, params.cellSize, maxSide)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val indexed = QuantisePass.run(params, grid, PixelDitherRenderer.levelsFor(params))

        val block = blockFor(params, grid.cols, grid.rows, maxSide)
        val rendered = PixelDitherRenderer.render(indexed, params, block)
        val bitmap = EffectPipeline.apply(rendered.toBitmap(), params.effects, ctx)

        val exportBlock = (AsciiRenderer.MAX_OUTPUT_SIDE / max(grid.cols, grid.rows))
            .coerceIn(1, cell)
        return Result(
            art = null,
            bitmap = bitmap,
            face = null,
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = grid.cols * exportBlock,
            outputHeight = grid.rows * exportBlock,
        )
    }

    /**
     * How many output pixels one dithered cell becomes.
     *
     * Zero — the default, and what every stored preset has — keeps the original behaviour: blocks
     * grow to fill the budget, the same way glyphs do in the other mode, so both modes produce
     * comparably sized output for the same settings. A pinned value separates *how coarsely the
     * image was sampled* from *how large that is drawn*, which used to be one number: a chunky
     * dither could only ever be a small image. Still clamped to the budget, because a preview that
     * ignored it would stop being a preview.
     */
    private fun blockFor(params: AsciiParams, cols: Int, rows: Int, maxSide: Int): Int {
        val fill = (maxSide / max(cols, rows)).coerceAtLeast(1)
        val pinned = params.pixelBlock
        return if (pinned <= 0) fill else pinned.coerceIn(1, fill.coerceAtLeast(1))
    }

    /**
     * A render of the source as a palette dither, handed on to the glyph stage as its input.
     *
     * The other two modes fork at the quantised level, so a palette dither and a character grid
     * could never both happen. Here the dither finishes into a real bitmap first and the glyphs are
     * read off *that* — which is what makes the character stage a step rather than an alternative,
     * and what lets this mode produce a `.txt` of a paletted dither.
     *
     * Effects run once, at the end, on the glyph render. Running them on the intermediate bitmap
     * would mean the glyph stage described the effects rather than the dither.
     */
    @Suppress("LongParameterList")
    private fun chainedRender(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: AsciiParams,
        maxSide: Int,
        ctx: RenderContext,
    ): Result {
        val cell = effectiveCell(sourceWidth, sourceHeight, params.cellSize, maxSide)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val indexed = QuantisePass.run(params, grid, PixelDitherRenderer.levelsFor(params))
        val dithered = PixelDitherRenderer.render(indexed, params, blockFor(params, grid.cols, grid.rows, maxSide))

        val ramp = params.effectiveRamp().ifEmpty { " " }
        val face = AsciiRenderer.faceFor(params, ramp)
        val aspect = AsciiRenderer.metrics(REFERENCE_FONT_SIZE, ramp, face.typeface).aspect
        val art = GlyphFromBitmap.convert(dithered, params, aspect)

        val fontSize = AsciiRenderer.fitFontSize(
            art.cols, art.rows, ramp, params.fontSizePx, maxSide, face.typeface,
        )
        val bitmap = EffectPipeline.apply(
            AsciiRenderer.render(art, params, fontSize, 1f),
            params.effects,
            ctx,
        )

        val cellMetrics = AsciiRenderer.metrics(
            AsciiRenderer.fitFontSize(
                art.cols, art.rows, ramp, params.fontSizePx,
                AsciiRenderer.MAX_OUTPUT_SIDE, face.typeface,
            ),
            ramp,
            face.typeface,
        )
        return Result(
            art = art,
            bitmap = bitmap,
            face = face,
            cols = art.cols,
            rows = art.rows,
            outputWidth = art.cols * cellMetrics.width,
            outputHeight = art.rows * cellMetrics.height,
        )
    }

    /**
     * The cell size actually used, which may be coarser than asked for.
     *
     * A cell size of one on a 4000-pixel source would mean a 4000-cell grid, and no preview
     * budget can hold that. Coarsening the cell is the only lever — the grid *is* the output
     * resolution in this mode — so the requested size is a floor, not a promise.
     */
    private fun effectiveCell(width: Int, height: Int, requested: Int, maxSide: Int): Int {
        val budget = maxSide.coerceAtLeast(1)
        val minimum = ceil(max(width, height).toFloat() / budget).toInt().coerceAtLeast(1)
        return max(requested.coerceAtLeast(1), minimum)
    }
}
