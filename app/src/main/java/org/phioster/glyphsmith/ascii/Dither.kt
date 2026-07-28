package org.phioster.glyphsmith.ascii

/** How the quantisation error is dealt with when a cell picks its glyph. */
enum class DitherMode {
    NONE,
    FLOYD_STEINBERG,
    ATKINSON,
    JARVIS,
    SIERRA_LITE,
    BAYER_2,
    BAYER_4,
    BAYER_8,
    ;

    val label: String
        get() = when (this) {
            NONE -> "None"
            FLOYD_STEINBERG -> "Floyd-Steinberg"
            ATKINSON -> "Atkinson"
            JARVIS -> "Jarvis"
            SIERRA_LITE -> "Sierra Lite"
            BAYER_2 -> "Bayer 2×2"
            BAYER_4 -> "Bayer 4×4"
            BAYER_8 -> "Bayer 8×8"
        }
}

/** One neighbour that receives a share of a cell's quantisation error. */
data class DiffusionTap(val dx: Int, val dy: Int, val weight: Float)

/**
 * Dither matrices and error-diffusion kernels, applied to the *cell grid* rather than to
 * pixels — the cell is the smallest thing this app can draw, so that's the resolution the
 * error has to be spread across.
 */
object Dither {

    fun isOrdered(mode: DitherMode): Boolean = when (mode) {
        DitherMode.BAYER_2, DitherMode.BAYER_4, DitherMode.BAYER_8 -> true
        else -> false
    }

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

    fun matrix(mode: DitherMode): Array<IntArray>? = when (mode) {
        DitherMode.BAYER_2 -> BAYER_2X2
        DitherMode.BAYER_4 -> BAYER_4X4
        DitherMode.BAYER_8 -> BAYER_8X8
        else -> null
    }

    /** Normalised threshold in 0..1 for the cell at ([x], [y]). */
    fun orderedThreshold(mode: DitherMode, x: Int, y: Int): Float {
        val m = matrix(mode) ?: return 0.5f
        val n = m.size
        val value = m[Math.floorMod(y, n)][Math.floorMod(x, n)]
        return (value + 0.5f) / (n * n)
    }

    /**
     * Where a cell's error goes. Weights sum to 1 for every kernel **except Atkinson**,
     * which deliberately throws away a quarter of the error — that loss is exactly what
     * gives Atkinson its higher-contrast, less muddy look, so it is not normalised away.
     */
    fun diffusionKernel(mode: DitherMode): List<DiffusionTap> = when (mode) {
        DitherMode.FLOYD_STEINBERG -> listOf(
            DiffusionTap(1, 0, 7 / 16f),
            DiffusionTap(-1, 1, 3 / 16f),
            DiffusionTap(0, 1, 5 / 16f),
            DiffusionTap(1, 1, 1 / 16f),
        )

        DitherMode.ATKINSON -> listOf(
            DiffusionTap(1, 0, 1 / 8f),
            DiffusionTap(2, 0, 1 / 8f),
            DiffusionTap(-1, 1, 1 / 8f),
            DiffusionTap(0, 1, 1 / 8f),
            DiffusionTap(1, 1, 1 / 8f),
            DiffusionTap(0, 2, 1 / 8f),
        )

        DitherMode.JARVIS -> listOf(
            DiffusionTap(1, 0, 7 / 48f),
            DiffusionTap(2, 0, 5 / 48f),
            DiffusionTap(-2, 1, 3 / 48f),
            DiffusionTap(-1, 1, 5 / 48f),
            DiffusionTap(0, 1, 7 / 48f),
            DiffusionTap(1, 1, 5 / 48f),
            DiffusionTap(2, 1, 3 / 48f),
            DiffusionTap(-2, 2, 1 / 48f),
            DiffusionTap(-1, 2, 3 / 48f),
            DiffusionTap(0, 2, 5 / 48f),
            DiffusionTap(1, 2, 3 / 48f),
            DiffusionTap(2, 2, 1 / 48f),
        )

        DitherMode.SIERRA_LITE -> listOf(
            DiffusionTap(1, 0, 2 / 4f),
            DiffusionTap(-1, 1, 1 / 4f),
            DiffusionTap(0, 1, 1 / 4f),
        )

        else -> emptyList()
    }

    /** How many rows below the current one a kernel reaches — the error buffer's depth. */
    fun kernelDepth(mode: DitherMode): Int =
        (diffusionKernel(mode).maxOfOrNull { it.dy } ?: 0) + 1
}
