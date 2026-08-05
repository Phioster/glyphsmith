package org.phioster.glyphsmith.core.dither

/**
 * Turning a picture into an ordered-dither screen.
 *
 * The conversion is a **ranking**, not a copy of the brightness. An ordered matrix works because
 * every threshold in it appears exactly once: the n² cells hold the numbers 0 until n², and the
 * renderer divides by n² to get a threshold. Copying luminance instead would leave some levels
 * unreachable and double others — a screen made of a flat grey image would put every threshold at
 * the same place and dither nothing at all.
 *
 * Ranking sidesteps that entirely. Whatever the picture is, sorting its samples and replacing
 * each by its position gives a valid screen; what differs is only the shape the ink clumps into.
 *
 * What the size means is not the same for every screen, which is why it is the importer's choice
 * rather than a constant. For a structured picture — a radial hill, a spiral — the size is the
 * size of the *clump*, and 32 is dramatically coarser than 16. For an unstructured one it is how
 * far the tile travels before it repeats, and 32 is visibly cleaner. A single default would be
 * wrong for half of the pictures anyone might bring.
 */
object ScreenImport {

    /** The sizes offered. Powers of two so the tile lines up with the cell grid at any scale. */
    val SIZES = listOf(16, 32)

    /** The largest screen this build will store, and what `screenOf` is held to. */
    const val MAX_SIZE = 32

    /**
     * A greyscale field of any size, reduced to an `n × n` screen.
     *
     * [luma] is read at whatever resolution it comes in and box-sampled down, so an imported
     * photograph and a 32-pixel icon both work. Ties in the sort are broken by index, which is
     * what keeps the result deterministic — two runs of the same picture give the same screen,
     * and a flat picture still gives a usable (if arbitrary) one rather than a degenerate one.
     */
    fun screenOf(luma: FloatArray, width: Int, height: Int, size: Int): List<Int> {
        require(size in SIZES) { "unsupported screen size: $size" }
        require(width > 0 && height > 0) { "empty image" }

        val sampled = FloatArray(size * size)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val x0 = col * width / size
                val x1 = ((col + 1) * width / size).coerceAtLeast(x0 + 1)
                val y0 = row * height / size
                val y1 = ((row + 1) * height / size).coerceAtLeast(y0 + 1)
                var sum = 0f
                var count = 0
                for (y in y0 until y1.coerceAtMost(height)) {
                    for (x in x0 until x1.coerceAtMost(width)) {
                        sum += luma[y * width + x]
                        count++
                    }
                }
                sampled[row * size + col] = if (count == 0) 0f else sum / count
            }
        }

        // Sorted by value, then by index: the second key is what makes a flat field deterministic
        // instead of dependent on the sort's own tie handling.
        val order = sampled.indices.sortedWith(compareBy({ sampled[it] }, { it }))
        val ranks = IntArray(size * size)
        order.forEachIndexed { rank, index -> ranks[index] = rank }
        return ranks.toList()
    }

    /**
     * The screen this style uses before anything has been imported.
     *
     * Not a Bayer tile, and that is the point twice over. A style that fell back to one would be
     * indistinguishable from the Bayer style already in the list — `DitherRegressionTest` says so
     * — and it would also tell the user nothing about what they are looking at. This is two
     * interfering sines, ranked: a woven screen that exists nowhere else in the app, so picking
     * the style shows what a screen *does* and importing one replaces it with your own.
     */
    val DEFAULT: List<Int> by lazy {
        val n = 16
        val woven = FloatArray(n * n) { i ->
            val x = (i % n).toFloat()
            val y = (i / n).toFloat()
            (kotlin.math.sin(x * 0.9f) * kotlin.math.sin(y * 0.9f) + 1f) / 2f
        }
        screenOf(woven, n, n, n)
    }

    /** The side of a stored screen, or 0 when there is none or it is not square. */
    fun sizeOf(screen: List<Int>): Int {
        if (screen.isEmpty()) return 0
        val side = Math.round(Math.sqrt(screen.size.toDouble())).toInt()
        return if (side * side == screen.size && side in SIZES) side else 0
    }

    /**
     * Normalised threshold in 0..1 for a cell, tiling the screen in both directions.
     *
     * Returns null when there is no usable screen, so the style can fall back rather than
     * render a flat field — a mode that draws nothing at all reads as a broken app.
     */
    fun thresholdAt(screen: List<Int>, x: Int, y: Int): Float? {
        val side = sizeOf(screen)
        if (side == 0) return null
        val value = screen[Math.floorMod(y, side) * side + Math.floorMod(x, side)]
        return (value + 0.5f) / (side * side)
    }
}
