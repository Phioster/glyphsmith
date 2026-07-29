package org.phioster.glyphsmith.core.dither

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Riemersma dithering: error diffusion along a Hilbert curve.
 *
 * Every other kernel here spreads the error to fixed neighbours, which is why they all leave
 * some directional grain — the error always travels the way the scan does. Riemersma has no
 * kernel at all. It walks a space-filling curve and remembers only the last handful of
 * errors, weighted so the most recent counts most. Because the curve keeps neighbouring
 * cells adjacent in *both* directions, the result has no preferred axis and no worm pattern.
 *
 * The weighting follows the published implementation: with a queue of [QUEUE] entries and a
 * ratio of [RATIO] between newest and oldest, `m = exp(ln(ratio) / (queue - 1))` and the
 * weights are successive powers of `m`. The accumulated sum is divided by the ratio, not by
 * the sum of the weights, so the effective gain is deliberately greater than one — that is
 * what makes the dither respond quickly rather than drift. Dither strength is the brake.
 */
object Riemersma {

    /** Errors remembered. Sixteen is the smallest the method is described as working at. */
    const val QUEUE = 16

    /** Weight of the newest error against the oldest. */
    const val RATIO = 16

    private val weights: FloatArray = FloatArray(QUEUE).also { out ->
        val m = exp(ln(RATIO.toDouble()) / (QUEUE - 1))
        var v = 1.0
        for (i in 0 until QUEUE) {
            out[i] = v.roundToInt().toFloat()
            v *= m
        }
    }

    /**
     * Quantises the whole grid along the curve and returns one ramp index per cell.
     *
     * Produced up front rather than inside the engine's row loop because the traversal order
     * is not the drawing order — the curve visits cells in a sequence that has nothing to do
     * with rows, so the two cannot share a loop.
     */
    fun quantise(
        luma: FloatArray,
        cols: Int,
        rows: Int,
        levels: Int,
        strength: Float,
        /** Per-cell nudge, so temporal noise reaches this mode like it reaches the others. */
        jitter: (Int, Int) -> Float = { _, _ -> 0f },
    ): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out

        val side = Integer.highestOneBit(maxOf(cols, rows, 1)).let {
            if (it < maxOf(cols, rows)) it * 2 else it
        }
        val queue = FloatArray(QUEUE)

        for (step in 0 until side * side) {
            val (x, y) = curvePoint(side, step)
            // The curve covers a power-of-two square; a grid that is not one has cells
            // outside it, and skipping them keeps the queue continuous across the gap.
            if (x >= cols || y >= rows) continue

            var accumulated = 0f
            for (i in 0 until QUEUE) accumulated += queue[i] * weights[i]

            val cell = y * cols + x
            val target = luma[cell] + jitter(x, y) + (accumulated / RATIO) * strength
            val index = AsciiEngine.quantize(target, levels)
            out[cell] = index

            val reproduced = index.toFloat() / (levels - 1)
            // Oldest drops off the front; the newest error goes in at the heaviest weight.
            System.arraycopy(queue, 1, queue, 0, QUEUE - 1)
            queue[QUEUE - 1] = target - reproduced
        }
        return out
    }

    /**
     * The [step]-th point of a Hilbert curve filling a [side]×[side] square.
     *
     * The standard iterative construction: read the index two bits at a time from the least
     * significant end, and at each level rotate and reflect the quadrant accumulated so far.
     */
    fun curvePoint(side: Int, step: Int): Pair<Int, Int> {
        var x = 0
        var y = 0
        var t = step
        var s = 1
        while (s < side) {
            val rx = 1 and (t / 2)
            val ry = 1 and (t xor rx)
            if (ry == 0) {
                if (rx == 1) {
                    x = s - 1 - x
                    y = s - 1 - y
                }
                val swap = x
                x = y
                y = swap
            }
            x += s * rx
            y += s * ry
            t /= 4
            s *= 2
        }
        return x to y
    }
}
