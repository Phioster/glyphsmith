package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitherTest {

    private val diffusionModes = listOf(
        DitherMode.FLOYD_STEINBERG,
        DitherMode.ATKINSON,
        DitherMode.JARVIS,
        DitherMode.SIERRA_LITE,
    )

    @Test
    fun `diffusion kernels conserve the error, except Atkinson`() {
        diffusionModes.filter { it != DitherMode.ATKINSON }.forEach { mode ->
            val sum = Dither.diffusionKernel(mode).map { it.weight }.sum()
            assertEquals("$mode should pass on all of the error", 1f, sum, 1e-4f)
        }
    }

    /**
     * Atkinson deliberately drops a quarter of the error — that loss is what gives it its
     * higher contrast. If this ever sums to 1, someone has "fixed" it into a different
     * algorithm.
     */
    @Test
    fun `Atkinson discards a quarter of the error on purpose`() {
        val sum = Dither.diffusionKernel(DitherMode.ATKINSON).map { it.weight }.sum()
        assertEquals(0.75f, sum, 1e-4f)
    }

    @Test
    fun `diffusion only ever writes forwards`() {
        diffusionModes.forEach { mode ->
            Dither.diffusionKernel(mode).forEach { tap ->
                // A tap on an earlier row, or to the left on the current row, would land on a
                // cell that has already been quantised — the error would be silently lost.
                assertTrue("$mode taps backwards", tap.dy > 0 || (tap.dy == 0 && tap.dx > 0))
            }
        }
    }

    @Test
    fun `kernel depth covers the furthest row a kernel reaches`() {
        assertEquals(1, Dither.kernelDepth(DitherMode.NONE))
        assertEquals(2, Dither.kernelDepth(DitherMode.FLOYD_STEINBERG))
        assertEquals(3, Dither.kernelDepth(DitherMode.ATKINSON))
        assertEquals(3, Dither.kernelDepth(DitherMode.JARVIS))
    }

    @Test
    fun `bayer matrices are complete permutations`() {
        listOf(DitherMode.BAYER_2, DitherMode.BAYER_4, DitherMode.BAYER_8).forEach { mode ->
            val matrix = requireNotNull(Dither.matrix(mode))
            val n = matrix.size
            val values = matrix.flatMap { it.asList() }.sorted()
            assertEquals("$mode is not $n×$n", n * n, values.size)
            assertEquals("$mode is not a permutation", (0 until n * n).toList(), values)
        }
    }

    @Test
    fun `ordered thresholds stay inside the unit interval and tile`() {
        val mode = DitherMode.BAYER_4
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val t = Dither.orderedThreshold(mode, x, y)
                assertTrue("threshold $t out of range", t > 0f && t < 1f)
                // The matrix must repeat, including at negative coordinates.
                assertEquals(t, Dither.orderedThreshold(mode, x + 4, y + 4), 1e-6f)
                assertEquals(t, Dither.orderedThreshold(mode, x - 4, y - 4), 1e-6f)
            }
        }
    }

    @Test
    fun `ordered modes are the bayer ones`() {
        assertTrue(Dither.isOrdered(DitherMode.BAYER_2))
        assertTrue(Dither.isOrdered(DitherMode.BAYER_8))
        assertTrue(!Dither.isOrdered(DitherMode.FLOYD_STEINBERG))
        assertTrue(!Dither.isOrdered(DitherMode.NONE))
    }
}
