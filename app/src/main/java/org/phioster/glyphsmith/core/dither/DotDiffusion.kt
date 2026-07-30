package org.phioster.glyphsmith.core.dither

import kotlin.math.roundToInt

/**
 * Knuth's dot diffusion, from "Digital Halftones by Dot Diffusion" (1987).
 *
 * The idea is unlike anything else here. Instead of sweeping the image in reading order, every
 * cell is given a **class number** from 0 to 63 by its position in a repeating 8×8 tile. All
 * the cells of class 0 are decided first, wherever they are in the picture; then all of class
 * 1, and so on. Error only ever moves to neighbours of a *higher* class — that is, to cells
 * that have not been decided yet — which is what makes the whole thing well defined without a
 * scan order, and incidentally parallelisable, which was Knuth's actual motivation.
 *
 * **The class matrix is not tabulated anywhere; it is constructed.** Knuth's own CWEB source
 * builds it from eight seed positions, each rotated and mirrored into eight, and the order
 * they are visited in is the order dots blacken in a 45° halftone font. Reproducing his
 * construction is the honest way to get it, and it verifies itself twice over: the result must
 * be a permutation of 0..63, and the number of diffusion instructions it compiles to must come
 * to exactly 256 — a figure Knuth states in the source as a check on his own work.
 *
 * Two cells end up as what he calls **barons**: class 62 and 63 have no higher-class neighbour
 * anywhere, so their error has nowhere to go and is dropped. That is inherent to the method,
 * not a corner cut here.
 */
object DotDiffusion {

    const val SIZE = 8

    /**
     * Class number per position in the tile, built the way Knuth builds it.
     *
     * `storeEight` places one seed and its seven symmetric images; eight seeds fill all
     * sixty-four classes. The coordinate juggling is his, transcribed rather than reasoned
     * out — it encodes the order a 45° halftone dot grows in.
     */
    val classes: Array<IntArray> = run {
        val grid = Array(SIZE + 2) { IntArray(SIZE + 2) { -1 } }
        var next = 0

        fun store(i0: Int, j0: Int) {
            var i = i0
            var j = j0
            if (i < 1) i += SIZE else if (i > SIZE) i -= SIZE
            if (j < 1) j += SIZE else if (j > SIZE) j -= SIZE
            grid[i][j] = next
            next++
        }

        fun storeEight(i: Int, j: Int) {
            store(i, j); store(i - 4, j + 4); store(1 - j, i - 4); store(5 - j, i)
            store(j, 5 - i); store(4 + j, 1 - i); store(5 - i, 5 - j); store(1 - i, 1 - j)
        }

        listOf(7 to 2, 8 to 3, 8 to 2, 8 to 1, 1 to 4, 1 to 3, 1 to 2, 2 to 3)
            .forEach { (i, j) -> storeEight(i, j) }

        Array(SIZE) { y -> IntArray(SIZE) { x -> grid[y + 1][x + 1] } }
    }

    /** The class of the cell at ([x], [y]) — the tile simply repeats. */
    fun classAt(x: Int, y: Int): Int = classes[Math.floorMod(y, SIZE)][Math.floorMod(x, SIZE)]

    /**
     * How many diffusion instructions the class matrix compiles to.
     *
     * Knuth notes in his source that this comes to 256. It is the cheapest possible check that
     * the construction above was transcribed correctly, and it is worth having because a class
     * matrix that is subtly wrong still produces a plausible-looking picture.
     */
    fun instructionCount(): Int {
        var total = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val k = classes[y][x]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (classAt(x + dx, y + dy) > k) total++
                    }
                }
            }
        }
        return total
    }

    /**
     * Every cell's glyph index, decided in class order.
     *
     * Orthogonal neighbours are weighted 2 and diagonal ones 1, normalised across whichever of
     * them actually qualify — so a cell with one eligible neighbour hands it everything, and a
     * baron with none simply loses its error.
     */
    fun quantise(luma: FloatArray, cols: Int, rows: Int, levels: Int, strength: Float): IntArray {
        val out = IntArray(cols * rows)
        if (levels <= 1) return out

        val error = FloatArray(cols * rows)
        val top = levels - 1

        // Cells bucketed by class once, rather than sweeping the whole grid sixty-four times
        // looking for the handful that belong to the current one.
        val byClass = Array(SIZE * SIZE) { ArrayList<Int>() }
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                byClass[classAt(x, y)].add(y * cols + x)
            }
        }

        for (k in 0 until SIZE * SIZE) {
            for (cell in byClass[k]) {
                val x = cell % cols
                val y = cell / cols
                val target = luma[cell] + error[cell]
                val index = (target.coerceIn(0f, 1f) * top).roundToInt().coerceIn(0, top)
                out[cell] = index

                val residue = (target - index.toFloat() / top) * strength
                if (residue == 0f) continue

                var weight = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue
                        if (classAt(nx, ny) <= k) continue
                        weight += if (dx != 0 && dy != 0) 1 else 2
                    }
                }
                if (weight == 0) continue

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue
                        if (classAt(nx, ny) <= k) continue
                        val share = if (dx != 0 && dy != 0) 1f else 2f
                        error[ny * cols + nx] += residue * share / weight
                    }
                }
            }
        }
        return out
    }
}
