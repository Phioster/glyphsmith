package org.phioster.glyphsmith.render

import org.phioster.glyphsmith.anim.Temporal
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.EdgeDetect
import org.phioster.glyphsmith.ascii.EdgeField
import kotlin.math.max
import org.phioster.glyphsmith.core.dither.Riemersma
import org.phioster.glyphsmith.core.dither.Regions
import org.phioster.glyphsmith.core.dither.PatternOptions
import org.phioster.glyphsmith.core.dither.FractalDiffuse
import org.phioster.glyphsmith.core.dither.DotDiffusion
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.Dither
import org.phioster.glyphsmith.core.dither.Directional

/**
 * A grid of quantised levels — the output of the dither, before anything decides what a level
 * looks like.
 *
 * This is the seam the two render modes meet at. An index is a number in `0 until levels` and
 * nothing more: the glyph path reads it as a position in the character ramp, the pixel path as
 * a position in the palette. Neither interpretation is present here, which is exactly why the
 * dither never had to know about either.
 *
 * [base] is the *undithered* luminance per cell, kept because colour has to be sampled from it
 * rather than from the dithered value — colour that flickers in step with the pattern reads as
 * twice the noise.
 */
class IndexGrid(
    val cols: Int,
    val rows: Int,
    val levels: Int,
    val indices: IntArray,
    val base: FloatArray,
    val colors: IntArray?,
    val edges: EdgeField?,
) {
    fun indexAt(col: Int, row: Int): Int = indices[row * cols + col]
}

/**
 * Runs a dither over a [CellGrid] and hands back the level each cell landed on.
 *
 * Every algorithm in the dither library takes a luminance grid, a cell count and a number of
 * levels, and returns indices — it has always been free of any notion of what an index means.
 * This pass is the thing that was previously fused into the glyph mapper and is now separate,
 * so both modes can drive the same 78 algorithms by simply asking for a different number of
 * levels: the ramp length for glyphs, the palette size for pixels.
 */
object QuantisePass {

    fun run(params: AsciiParams, grid: CellGrid, levels: Int): IndexGrid {
        val cols = grid.cols
        val rows = grid.rows
        val lumaGrid = grid.luma
        val indices = IntArray(cols * rows)

        val mode = params.ditherMode
        val strength = (params.ditherStrength / 100f).coerceIn(0f, 1f)
        val ordered = Dither.isThresholdBased(mode)
        val pattern = PatternOptions(
            scale = params.ditherScale,
            period = params.modScale,
            angle = params.modAngle,
            phase = params.modPhase / 100f,
            centerX = cols / 2f,
            centerY = rows / 2f,
            density = params.patternDensity,
            orb = params.orbOptions(),
        )
        val kernel = Dither.diffusionKernel(mode)
        val variableKernel = Dither.hasVariableKernel(mode)
        // Some modes do not visit cells in reading order at all, so they cannot share this
        // loop. They resolve the whole grid up front and the loop below simply reads the
        // index each cell was given.
        val precomputed = if (Dither.isPrecomputed(mode)) {
            precomputedIndices(mode, params, lumaGrid, cols, rows, levels, strength, pattern)
        } else {
            null
        }
        val depth = Dither.kernelDepth(mode)
        // Only the rows a kernel can still reach are kept, not a full-grid error buffer.
        val errorRows = Array(depth) { FloatArray(cols) }

        for (row in 0 until rows) {
            val currentError = errorRows[row % depth]
            // Serpentine scanning alternates direction so diffusion errors don't all drift
            // the same way and form the classic diagonal worm pattern.
            val leftToRight = !params.serpentine || row % 2 == 0
            var step = 0
            while (step < cols) {
                val col = if (leftToRight) step else cols - 1 - step
                val cell = row * cols + col
                val base = lumaGrid[cell]

                // Temporal noise nudges the threshold rather than the image, so it reaches
                // every dither mode — including NONE, where it is the only thing moving.
                // Scaled to one level step, exactly as the ordered branch scales its own.
                val jitter = Temporal.offset(params.temporal, col, row) / max(1, levels - 1)

                val target = jitter + when {
                    ordered -> base + (Dither.threshold(mode, col, row, base, pattern) - 0.5f) *
                        strength / max(1, levels - 1)

                    kernel.isNotEmpty() -> base + currentError[col]
                    else -> base
                }

                val index = precomputed?.get(cell) ?: quantize(target, levels)
                indices[cell] = index

                if (!ordered && kernel.isNotEmpty()) {
                    val reproduced = if (levels <= 1) 0f else index.toFloat() / (levels - 1)
                    val error = (target - reproduced) * strength
                    // A variable kernel is chosen from the value being quantised, so it can
                    // only be asked for here — after the cell is known, not before the loop.
                    val taps = if (variableKernel) {
                        Dither.variableKernel(mode, target.coerceIn(0f, 1f)) ?: kernel
                    } else {
                        kernel
                    }
                    for (tap in taps) {
                        val dx = if (leftToRight) tap.dx else -tap.dx
                        val tx = col + dx
                        val ty = row + tap.dy
                        if (tx < 0 || tx >= cols || ty >= rows) continue
                        errorRows[ty % depth][tx] += error * tap.weight
                    }
                }
                step++
            }
            // The row just consumed becomes the buffer for the row `depth` further down.
            currentError.fill(0f)
        }

        val edges = if (params.edgeEnabled) EdgeDetect.sobel(lumaGrid, cols, rows) else null
        return IndexGrid(cols, rows, levels, indices, lumaGrid, grid.colors, edges)
    }

    /**
     * A finished grid of indices, for the modes that refuse to be walked row by row.
     *
     * They share nothing but that refusal, so this is a dispatch and not an abstraction: each
     * one gets its whole grid to itself and hands back what every cell should be. The caller
     * only has to know that an answer arrived.
     */
    @Suppress("LongParameterList")
    private fun precomputedIndices(
        mode: DitherMode,
        params: AsciiParams,
        lumaGrid: FloatArray,
        cols: Int,
        rows: Int,
        levels: Int,
        strength: Float,
        pattern: PatternOptions,
    ): IntArray? = when (mode) {
        DitherMode.RIEMERSMA -> Riemersma.quantise(lumaGrid, cols, rows, levels, strength) { x, y ->
            Temporal.offset(params.temporal, x, y) / max(1, levels - 1)
        }

        DitherMode.DOT_DIFFUSION ->
            DotDiffusion.quantise(lumaGrid, cols, rows, levels, strength)

        DitherMode.FRACTAL_DIFFUSE ->
            FractalDiffuse.quantise(lumaGrid, cols, rows, levels, strength)

        DitherMode.CONTRAST_AWARE_X ->
            Directional.contrastAware(lumaGrid, cols, rows, levels, strength, vertical = true)

        DitherMode.CONTRAST_AWARE_Y ->
            Directional.contrastAware(lumaGrid, cols, rows, levels, strength, vertical = false)

        DitherMode.VORTEX_DIFFUSION ->
            Directional.spiral(lumaGrid, cols, rows, levels, strength)

        else -> Regions.quantise(mode, lumaGrid, cols, rows, levels, pattern)
    }

    /**
     * Luminance to an index, *before* any offset. Dithering needs this separately: the error
     * is the difference between the requested tone and the tone actually reproduced, which only
     * means anything on the un-rotated scale.
     */
    fun quantize(luma: Float, levels: Int): Int = Dither.quantise(luma, levels)
}
