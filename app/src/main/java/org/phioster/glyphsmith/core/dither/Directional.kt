package org.phioster.glyphsmith.core.dither

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Two ways of diffusing error that a per-cell kernel cannot express.
 *
 * A kernel is asked one question — where does this cell's error go — and answered before the
 * loop starts. Both of these need more than that. One needs to look at a neighbourhood; the
 * other needs to choose its own path across the picture. So neither is a kernel, and both hand
 * back a finished grid instead.
 */
object Directional {

    /**
     * One-dimensional diffusion that gives way at edges.
     *
     * Pushing all of the error along a single axis produces long clean streaks, which is the
     * look — but it also drags error straight across every boundary in the picture, and edges
     * dissolve into smears. Measuring the local contrast first and passing on less of the
     * error where it is high stops the streak at the edge and lets it run everywhere else.
     *
     * That measurement is the whole reason this cannot be a kernel: a kernel sees one cell.
     *
     * [vertical] runs the diffusion down columns instead of along rows. Their catalogue names
     * these the other way round from what one might guess — Contrast Aware X is the vertical
     * one — and the names are kept as they are rather than quietly corrected.
     */
    fun contrastAware(
        luma: FloatArray,
        cols: Int,
        rows: Int,
        levels: Int,
        strength: Float,
        vertical: Boolean,
    ): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out
        val top = levels - 1

        val outer = if (vertical) cols else rows
        val inner = if (vertical) rows else cols

        for (a in 0 until outer) {
            var carried = 0f
            for (b in 0 until inner) {
                val x = if (vertical) a else b
                val y = if (vertical) b else a
                val cell = y * cols + x

                val target = luma[cell] + carried
                val index = (target.coerceIn(0f, 1f) * top).roundToInt().coerceIn(0, top)
                out[cell] = index

                // Contrast as the spread of the 3×3 neighbourhood. Cheap, and the only
                // property that matters here is whether this cell sits on a boundary.
                var low = 1f
                var high = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue
                        val v = luma[ny * cols + nx]
                        if (v < low) low = v
                        if (v > high) high = v
                    }
                }
                val keep = (1f - (high - low)).coerceIn(0f, 1f)
                carried = (target - index.toFloat() / top) * strength * keep
            }
        }
        return out
    }

    /**
     * Error diffusion along a spiral out from the centre.
     *
     * Every scanning order leaves its own signature: rows leave worms, a Hilbert curve leaves
     * none. A spiral leaves a swirl, because the error is handed along a path that curves, and
     * the grain curves with it. The picture ends up organised around its own middle, which is
     * where a face usually is.
     *
     * The path is built by sorting the cells by angle within rings rather than by walking a
     * rectangular spiral: the rectangular one has four corners per turn, and the error lurches
     * at each of them.
     */
    fun spiral(luma: FloatArray, cols: Int, rows: Int, levels: Int, strength: Float): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out
        val top = levels - 1

        val cx = cols / 2f
        val cy = rows / 2f
        val order = (0 until cols * rows).sortedBy { cell ->
            val dx = cell % cols - cx
            val dy = cell / cols - cy
            val radius = hypot(dx, dy)
            // Ring first, then angle within the ring — the angle alone would sweep a wedge
            // from the centre to the edge and hand error between cells that never touch.
            radius.toInt() * TURN + (atan2(dy, dx) + Math.PI).toFloat() / TAU * TURN
        }

        var carried = 0f
        for (cell in order) {
            val target = luma[cell] + carried
            val index = (target.coerceIn(0f, 1f) * top).roundToInt().coerceIn(0, top)
            out[cell] = index
            carried = (target - index.toFloat() / top) * strength
        }
        return out
    }

    /** Cells per notional turn — large enough that a ring always outranks an angle. */
    private const val TURN = 1024f

    private const val TAU = (2.0 * Math.PI).toFloat()
}
