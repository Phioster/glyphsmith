package org.phioster.glyphsmith.render

import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.Dither
import org.phioster.glyphsmith.ascii.PatternOptions
import org.phioster.glyphsmith.core.color.PaletteQuantizer

/**
 * Colour dithering: reduces each cell to a palette entry and carries the error it made into the
 * cells it has not reached yet — on all three channels, not on luminance.
 *
 * This is what the luminance path cannot do. [QuantisePass] works on one value per cell, so its
 * error is one number, and a palette of coloured entries cannot be corrected by a brightness
 * correction: reducing a warm mid-grey to a palette holding a blue and a brown needs to know it
 * came out too blue, which is a per-channel fact. Without this the source-colour mode snaps each
 * cell to its nearest entry independently, which is posterisation — visible as flat blocks and
 * banding where a dither would have held the tone.
 *
 * The scan and the kernels are the same ones the luminance path uses, so all the error-diffusion
 * styles behave here as they do there, serpentine included. Two families cannot take part:
 *
 * - The **threshold** styles have no error to carry. They perturb each channel by the pattern
 *   before the lookup instead, which is an ordered colour dither and keeps the style's texture.
 * - The **precomputed and region** styles do not visit cells in reading order at all, so there is
 *   no "not reached yet" to diffuse into. They fall back to a plain nearest-colour reduction, and
 *   that is honest: those styles impose a shape rather than reproduce a tone.
 */
object ColorDiffusionPass {

    /** One colour per cell, in palette entries. */
    fun run(params: AsciiParams, grid: IndexGrid, quantizer: PaletteQuantizer): IntArray {
        val cols = grid.cols
        val rows = grid.rows
        val source = grid.colors ?: return IntArray(cols * rows) { params.inkColor }
        val out = IntArray(cols * rows)

        val mode = params.ditherMode
        val strength = (params.ditherStrength / 100f).coerceIn(0f, 1f)
        val kernel = Dither.diffusionKernel(mode)
        val variableKernel = Dither.hasVariableKernel(mode)
        val ordered = Dither.isThresholdBased(mode)
        val scanned = !Dither.isPrecomputed(mode) && !Dither.isRegion(mode)

        if (!scanned || (kernel.isEmpty() && !ordered)) {
            for (i in out.indices) out[i] = quantizer.nearest(source[i])
            return out
        }

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
        // One step between neighbouring palette entries, in channel units. Perturbing by more
        // than this would push a cell past its neighbour's neighbour and read as noise.
        val step = 255f / quantizer.size.coerceAtLeast(2)

        val depth = Dither.kernelDepth(mode)
        // Three channels interleaved, so one row of error is one allocation rather than three.
        val errorRows = Array(depth) { FloatArray(cols * CHANNELS) }

        for (row in 0 until rows) {
            val currentError = errorRows[row % depth]
            val leftToRight = !params.serpentine || row % 2 == 0
            var scan = 0
            while (scan < cols) {
                val col = if (leftToRight) scan else cols - 1 - scan
                val cell = row * cols + col
                val pixel = source[cell]

                val perturb = if (ordered) {
                    (Dither.threshold(mode, col, row, grid.base[cell], pattern) - 0.5f) * strength * step
                } else {
                    0f
                }
                val base = col * CHANNELS
                val wantR = ((pixel shr 16) and 0xFF) + perturb + currentError[base]
                val wantG = ((pixel shr 8) and 0xFF) + perturb + currentError[base + 1]
                val wantB = (pixel and 0xFF) + perturb + currentError[base + 2]

                val chosen = quantizer.nearest(packClamped(wantR, wantG, wantB))
                out[cell] = chosen

                if (!ordered) {
                    val errR = (wantR - ((chosen shr 16) and 0xFF)) * strength
                    val errG = (wantG - ((chosen shr 8) and 0xFF)) * strength
                    val errB = (wantB - (chosen and 0xFF)) * strength
                    // A variable kernel is chosen from the value being quantised, so it can only
                    // be asked for here. Luminance is the right key for it even in colour: the
                    // kernels vary by tone, not by hue.
                    val taps = if (variableKernel) {
                        Dither.variableKernel(mode, grid.base[cell].coerceIn(0f, 1f)) ?: kernel
                    } else {
                        kernel
                    }
                    for (tap in taps) {
                        val dx = if (leftToRight) tap.dx else -tap.dx
                        val tx = col + dx
                        val ty = row + tap.dy
                        if (tx < 0 || tx >= cols || ty >= rows) continue
                        val target = errorRows[ty % depth]
                        val offset = tx * CHANNELS
                        target[offset] += errR * tap.weight
                        target[offset + 1] += errG * tap.weight
                        target[offset + 2] += errB * tap.weight
                    }
                }
                scan++
            }
            currentError.fill(0f)
        }
        return out
    }

    private const val CHANNELS = 3

    private fun packClamped(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)
}
