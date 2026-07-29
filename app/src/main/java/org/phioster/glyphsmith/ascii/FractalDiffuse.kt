package org.phioster.glyphsmith.ascii

import kotlin.math.roundToInt

/**
 * Error diffusion along a space-filling curve.
 *
 * Every row-scanning kernel has a direction, and that direction is visible: Floyd-Steinberg
 * leaves diagonal worms, the axis-dominant kernels leave streaks. The cause is structural —
 * error can only ever travel forwards and downwards, so it accumulates along a preferred
 * axis whatever the weights are.
 *
 * A Hilbert curve has no preferred axis. It visits every cell exactly once, never jumps, and
 * turns constantly, so error handed from one cell to the next travels in a path that folds
 * back on itself at every scale. What is left is grain without direction.
 *
 * This shares its curve with [Riemersma] and differs in what it does along it. Riemersma
 * keeps a decaying queue of the last sixteen errors, which softens the result; this hands the
 * whole error to the next cell, which keeps it crisp. Same path, different memory.
 */
object FractalDiffuse {

    fun quantise(luma: FloatArray, cols: Int, rows: Int, levels: Int, strength: Float): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out

        // The curve is defined on a power-of-two square, so it is drawn on the smallest one
        // that covers the grid and the points falling outside are skipped. Clipping the path
        // rather than stretching it keeps the turns where they belong.
        val side = Integer.highestOneBit(maxOf(cols, rows, 1)).let {
            if (it < maxOf(cols, rows)) it * 2 else it
        }

        val top = levels - 1
        var carried = 0f

        for (step in 0 until side * side) {
            val (x, y) = Riemersma.curvePoint(side, step)
            if (x >= cols || y >= rows) continue
            val cell = y * cols + x

            val target = luma[cell] + carried
            val index = (target.coerceIn(0f, 1f) * top).roundToInt().coerceIn(0, top)
            out[cell] = index
            carried = (target - index.toFloat() / top) * strength
        }
        return out
    }
}
