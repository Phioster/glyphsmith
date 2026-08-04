package org.phioster.glyphsmith.core.dither

/**
 * The matrix-driven styles: a threshold read off a fixed tile that repeats across the grid.
 *
 * They differ only in what is in the tile. Bayer's is a published table small enough to write
 * out — or rather to grow, since the construction is shorter than the numbers — while the
 * clustered-dot screens and the blue-noise masks are defined by their construction rules and
 * come from [DitherMatrices].
 *
 * Every screen here is declared lazily on purpose. The picker asks each style what kind of thing
 * it is as it draws the dropdown, and building a 32×32 blue-noise mask to answer that question
 * would be paid for by anyone who merely opened the list.
 */
internal object OrderedScreens {

    private val BAYER_2X2 = arrayOf(
        intArrayOf(0, 2),
        intArrayOf(3, 1),
    )

    /** `M₂ₙ = [[4M+0, 4M+2], [4M+3, 4M+1]]` — the standard recursive construction. */
    private fun grow(base: Array<IntArray>): Array<IntArray> {
        val n = base.size
        val out = Array(n * 2) { IntArray(n * 2) }
        for (y in 0 until n) {
            for (x in 0 until n) {
                val v = base[y][x] * 4
                out[y][x] = v
                out[y][x + n] = v + 2
                out[y + n][x] = v + 3
                out[y + n][x + n] = v + 1
            }
        }
        return out
    }

    private val BAYER_4X4 = grow(BAYER_2X2)
    private val BAYER_8X8 = grow(BAYER_4X4)
    private val BAYER_16X16 = grow(BAYER_8X8)

    val BAYER_2 = OrderedMatrix { BAYER_2X2 }
    val BAYER_4 = OrderedMatrix { BAYER_4X4 }
    val BAYER_8 = OrderedMatrix { BAYER_8X8 }
    val BAYER_16 = OrderedMatrix { BAYER_16X16 }

    val CLUSTER_4 = OrderedMatrix { DitherMatrices.clusteredDot(4) }
    val CLUSTER_8 = OrderedMatrix { DitherMatrices.clusteredDot(8) }

    val BLUE_NOISE_16 = OrderedMatrix { DitherMatrices.blueNoise(16) }
    val BLUE_NOISE_32 = OrderedMatrix { DitherMatrices.blueNoise(32) }
}
